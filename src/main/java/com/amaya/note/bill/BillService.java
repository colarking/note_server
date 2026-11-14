package com.amaya.note.bill;
import com.amaya.note.common.*;
import com.amaya.note.points.PointService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BillService {
    private final Db db; private final PointService points; private final ObjectMapper json;
    public BillService(Db db,PointService points,ObjectMapper json) {this.db=db;this.points=points;this.json=json;}
    public record Input(@NotNull UUID requestKey,@NotNull UUID ledgerId,@NotNull UUID placeId,
        @NotNull @DecimalMin("0.01") @Digits(integer=14,fraction=2) BigDecimal amount,
        @NotNull @Pattern(regexp="EXPENSE|INCOME|TRANSFER|REFUND") String kind,
        @NotBlank @Size(max=80) String category,@NotNull @Size(max=2000) String note,
        @NotNull @PastOrPresent OffsetDateTime occurredAt, UUID originalBillId) {}
    public record Edit(@NotBlank @Size(max=80) String category,@NotNull @Size(max=2000) String note) {}
    @Transactional public Object create(UUID user,Input in) {
        db.lock(user);
        String hash;
        try {hash=Hashes.sha256(json.writeValueAsString(in));} catch(JsonProcessingException e){throw new IllegalStateException(e);}
        var existing=db.jdbc.queryForList("select id,request_hash,deleted_at from bill where user_id=? and request_key=?",user,in.requestKey());
        if(!existing.isEmpty()) {
            var row=existing.getFirst();
            if(!hash.equals(row.get("request_hash")) || row.get("deleted_at")!=null) throw ApiException.conflict("Request key already used");
            return db.bill(user,(UUID)row.get("id"));
        }
        db.one("select id from ledger where id=? and user_id=?",in.ledgerId(),user);
        db.one("select id from place where id=? and user_id=?",in.placeId(),user);
        if(in.kind().equals("REFUND")) {
            if(in.originalBillId()==null) throw ApiException.bad("Refund requires originalBillId");
            var original=db.bill(user,in.originalBillId());
            if(!original.get("kind").equals("EXPENSE") || !original.get("ledger_id").equals(in.ledgerId())) throw ApiException.bad("Refund must reference an expense in the same ledger");
            BigDecimal refunded=db.jdbc.queryForObject("select coalesce(sum(amount),0) from bill where original_bill_id=? and deleted_at is null",BigDecimal.class,in.originalBillId());
            if(refunded.add(in.amount()).compareTo((BigDecimal)original.get("amount"))>0) throw ApiException.bad("Refund exceeds remaining expense amount");
        } else if(in.originalBillId()!=null) throw ApiException.bad("Only refunds may reference an original bill");
        UUID id=UUID.randomUUID();
        db.jdbc.update("insert into bill(id,user_id,ledger_id,place_id,amount,kind,category,note,occurred_at,original_bill_id,request_key,request_hash) values(?,?,?,?,?,?,?,?,?,?,?,?)",
            id,user,in.ledgerId(),in.placeId(),in.amount(),in.kind(),in.category(),in.note(),in.occurredAt(),in.originalBillId(),in.requestKey(),hash);
        points.bill(user,id);
        return db.bill(user,id);
    }
    @Transactional public Object edit(UUID user,UUID id,Edit input) {
        db.lock(user);db.bill(user,id);
        db.jdbc.update("update bill set category=?,note=? where id=?",input.category(),input.note(),id);
        return db.bill(user,id);
    }
    @Transactional public void delete(UUID user,UUID id) {
        db.lock(user);
        var rows=db.jdbc.queryForList("select deleted_at from bill where id=? and user_id=?",id,user);
        if(rows.isEmpty()) throw ApiException.missing();
        if(rows.getFirst().get("deleted_at")!=null) return;
        if(db.jdbc.queryForObject("select count(*) from bill where original_bill_id=? and deleted_at is null",Long.class,id)>0) throw ApiException.conflict("Delete related refunds first");
        db.jdbc.update("update bill set deleted_at=now() where id=?",id);
        db.jdbc.update("update media set deleted_at=now() where bill_id=? and deleted_at is null",id);
        db.jdbc.update("update checkin set status='REVOKED' where bill_id=?",id);
        points.revokeBill(user,id);
    }
}
