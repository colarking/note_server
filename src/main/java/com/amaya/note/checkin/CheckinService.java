package com.amaya.note.checkin;
import com.amaya.note.common.*;
import com.amaya.note.points.PointService;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CheckinService {
    private final Db db; private final PointService points;
    public CheckinService(Db db,PointService points) {this.db=db;this.points=points;}
    public record Input(@NotNull UUID billId,@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") Double accuracyMeters,@NotNull OffsetDateTime capturedAt) {}
    public record Review(@NotNull Boolean approved,
        @Size(max=160) @Pattern(regexp="[A-Za-z0-9:_-]+") String canonicalPlaceId,
        @Size(max=160) @Pattern(regexp="[A-Za-z0-9:_-]+") String cityId,
        @Size(max=160) @Pattern(regexp="[A-Za-z0-9:_-]+") String provinceId,
        @Size(max=160) @Pattern(regexp="[A-Za-z0-9:_-]+") String countryId,
        @NotBlank @Size(max=500) String reason) {}
    @Transactional public Object create(UUID user,Input input) {
        db.lock(user);var bill=db.bill(user,input.billId());
        var existing=db.jdbc.queryForList("select * from checkin where bill_id=?",input.billId());
        if(!existing.isEmpty()) return existing.getFirst();
        Instant now=Instant.now();
        if(input.capturedAt().toInstant().isBefore(now.minusSeconds(600)) || input.capturedAt().toInstant().isAfter(now.plusSeconds(30))) throw ApiException.bad("Location capture must be within the last ten minutes");
        var place=db.one("select latitude,longitude from place where id=?",bill.get("place_id"));
        double lat=((Number)place.get("latitude")).doubleValue(),lon=((Number)place.get("longitude")).doubleValue();
        double a=Math.pow(Math.sin(Math.toRadians(input.latitude()-lat)/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(input.latitude()))*Math.pow(Math.sin(Math.toRadians(input.longitude()-lon)/2),2);
        double distance=6371000*2*Math.asin(Math.sqrt(Math.min(1,a)));
        if(!Double.isFinite(distance)||distance>200+input.accuracyMeters()) throw ApiException.bad("Location is too far from the bill's place");
        UUID id=UUID.randomUUID();
        db.jdbc.update("insert into checkin(id,user_id,bill_id,latitude,longitude,accuracy_m,captured_at) values(?,?,?,?,?,?,?)",id,user,input.billId(),input.latitude(),input.longitude(),input.accuracyMeters(),input.capturedAt());
        return db.one("select * from checkin where id=?",id);
    }
    @Transactional public Object review(UUID admin,UUID id,Review input) {
        var row=db.one("select user_id from checkin where id=?",id); UUID user=(UUID)row.get("user_id");
        db.lock(user);row=db.one("select * from checkin where id=?",id);
        if(!row.get("status").equals("PENDING")) throw ApiException.conflict("Check-in already reviewed or revoked");
        db.bill(user,(UUID)row.get("bill_id"));
        if(input.approved() && (input.canonicalPlaceId()==null||input.cityId()==null||input.countryId()==null)) throw ApiException.bad("Approval requires canonical place, city and country IDs");
        db.jdbc.update("update checkin set status=?,canonical_place_id=?,city_id=?,province_id=?,country_id=?,reviewed_by=?,review_note=?,reviewed_at=now() where id=?",
            input.approved()?"VERIFIED":"REJECTED",input.canonicalPlaceId(),input.cityId(),input.provinceId(),input.countryId(),admin,input.reason(),id);
        if(input.approved()) points.checkin(user,(UUID)row.get("bill_id"),input.canonicalPlaceId(),input.cityId(),input.provinceId(),input.countryId());
        db.jdbc.update("insert into audit_log(actor_id,action,target_id,reason) values(?,?,?,?)",admin,input.approved()?"APPROVE_CHECKIN":"REJECT_CHECKIN",id,input.reason());
        return db.one("select * from checkin where id=?",id);
    }
    @Transactional public void revoke(UUID admin,UUID id,String reason) {
        var row=db.one("select user_id from checkin where id=?",id); UUID user=(UUID)row.get("user_id");
        db.lock(user);row=db.one("select * from checkin where id=?",id);
        if(row.get("status").equals("REVOKED")) return;
        db.jdbc.update("update checkin set status='REVOKED',reviewed_by=?,review_note=?,reviewed_at=now() where id=?",admin,reason,id);
        points.revokeCheckin(user,(UUID)row.get("bill_id"));
        db.jdbc.update("insert into audit_log(actor_id,action,target_id,reason) values(?,?,?,?)",admin,"REVOKE_CHECKIN",id,reason);
    }
}
