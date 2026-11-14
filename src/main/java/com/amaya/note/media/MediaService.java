package com.amaya.note.media;
import com.amaya.note.common.*;
import com.amaya.note.points.PointService;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
@Service
public class MediaService {
    private final Db db;private final PointService points;private final Path root;
    public MediaService(Db db,PointService points,@Value("${app.media-root}") String root) throws IOException {
        this.db=db;this.points=points;this.root=Path.of(root).toAbsolutePath().normalize();Files.createDirectories(this.root);
    }
    public Path path(UUID id) {return root.resolve(id.toString());}
    @Transactional public Object upload(UUID user,UUID bill,MultipartFile file) throws IOException {
        db.lock(user);db.bill(user,bill);
        if(file.isEmpty()||file.getSize()>50L*1024*1024) throw ApiException.bad("File must be between 1 byte and 50 MB");
        byte[] bytes=file.getBytes();
        String mime=detect(bytes);String kind=mime.startsWith("image/")?"IMAGE":"VIDEO";
        String digest=Hashes.sha256(bytes);
        var existing=db.jdbc.queryForList("select * from media where user_id=? and sha256=?",user,digest);
        if(!existing.isEmpty()) {
            var row=existing.getFirst();
            if(row.get("deleted_at")!=null||!row.get("bill_id").equals(bill)) throw ApiException.conflict("This file was already uploaded for another or deleted record");
            return row;
        }
        if(db.jdbc.queryForObject("select count(*) from media where bill_id=? and deleted_at is null",Long.class,bill)>=30) throw ApiException.bad("A bill can contain at most 30 attachments");
        long used=db.jdbc.queryForObject("select coalesce(sum(size_bytes),0) from media where user_id=?",Long.class,user);
        if(used+bytes.length>1024L*1024*1024) throw ApiException.bad("User media storage quota exceeded (1 GiB)");
        UUID id=UUID.randomUUID();Path target=path(id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) {
                    try {Files.deleteIfExists(target);} catch(IOException e) {org.slf4j.LoggerFactory.getLogger(MediaService.class).warn("Could not remove rolled-back media {}",id,e);}
                }
            }
        });
        Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
        db.jdbc.update("insert into media(id,user_id,bill_id,kind,mime,sha256,size_bytes) values(?,?,?,?,?,?,?)",id,user,bill,kind,mime,digest,bytes.length);
        points.media(user,bill,id,kind);
        return db.one("select * from media where id=?",id);
    }
    private String detect(byte[] bytes) throws IOException {
        if(bytes.length<12) throw ApiException.bad("Unsupported media file");
        boolean jpeg=(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
        byte[] png={(byte)137,80,78,71,13,10,26,10};
        if(jpeg||Arrays.equals(Arrays.copyOf(bytes,8),png)) {
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);
                if(!readers.hasNext()) throw ApiException.bad("Invalid image");
                var reader=readers.next();
                try {reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);
                    if(w<=0||h<=0||(long)w*h>40000000) throw ApiException.bad("Image exceeds 40 megapixels");
                    if(reader.read(0)==null) throw ApiException.bad("Invalid image");
                } finally {reader.dispose();}
            } catch(javax.imageio.IIOException e) {throw ApiException.bad("Invalid image data");}
            return jpeg?"image/jpeg":"image/png";
        }
        if(new String(bytes,4,4,StandardCharsets.US_ASCII).equals("ftyp")) {
            String brand=new String(bytes,8,4,StandardCharsets.US_ASCII);
            if(Set.of("isom","iso2","mp41","mp42","avc1","M4V ").contains(brand)) return "video/mp4";
        }
        throw ApiException.bad("Only JPEG, PNG and MP4 files are supported");
    }
    @Transactional public void delete(UUID user,UUID id) {
        db.lock(user);var row=db.one("select * from media where id=? and user_id=?",id,user);
        if(row.get("deleted_at")!=null) return;
        db.jdbc.update("update media set deleted_at=now() where id=?",id);points.revokeMedia(user,id);
    }
}
