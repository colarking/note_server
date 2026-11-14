package com.amaya.note.media;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.Db;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/media")
public class MediaController {
    private final Db db;private final MediaService service;
    public MediaController(Db db,MediaService service){this.db=db;this.service=service;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(@AuthenticationPrincipal CurrentUser user,@RequestParam UUID billId,@RequestPart("file") MultipartFile file) throws IOException {return service.upload(user.id(),billId,file);}
    @GetMapping public Object list(@AuthenticationPrincipal CurrentUser user,@RequestParam UUID billId) {
        db.bill(user.id(),billId);return db.jdbc.queryForList("select * from media where bill_id=? and user_id=? and deleted_at is null order by created_at,id",billId,user.id());
    }
    @GetMapping("/{id}/content") public ResponseEntity<FileSystemResource> content(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID id) {
        var row=db.one("select m.* from media m join bill b on b.id=m.bill_id where m.id=? and m.user_id=? and m.deleted_at is null and b.deleted_at is null",id,user.id());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)row.get("mime")))
            .cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=\""+id+"\"")
            .header("X-Content-Type-Options","nosniff").body(new FileSystemResource(service.path(id)));
    }
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID id) {service.delete(user.id(),id);}
}
