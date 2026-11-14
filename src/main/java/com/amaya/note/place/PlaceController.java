package com.amaya.note.place;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/places")
public class PlaceController {
    private final Db db;
    public PlaceController(Db db) { this.db=db; }
    public record Input(@NotBlank @Size(max=120) String name, @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude, @NotNull @Size(max=300) String address) {}
    @PostMapping public Object create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody Input input) {
        UUID id=UUID.randomUUID();
        db.jdbc.update("insert into place(id,user_id,name,latitude,longitude,address) values(?,?,?,?,?,?)",id,user.id(),input.name(),input.latitude(),input.longitude(),input.address());
        return db.one("select * from place where id=?",id);
    }
    @GetMapping public Object list(@AuthenticationPrincipal CurrentUser user,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return db.jdbc.queryForList("select * from place where user_id=? order by created_at desc limit ? offset ?",user.id(),Db.limit(limit),Db.offset(offset));
    }
    @GetMapping("/nearby") public Object nearby(@AuthenticationPrincipal CurrentUser user,@RequestParam double latitude,@RequestParam double longitude,@RequestParam(defaultValue="1000") double radiusMeters) {
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||!Double.isFinite(radiusMeters)||Math.abs(latitude)>90||Math.abs(longitude)>180||radiusMeters<=0||radiusMeters>50000) throw ApiException.bad("Invalid coordinates or radius (maximum 50000m)");
        return db.jdbc.queryForList("""
            select * from (select p.*,6371000*acos(least(1.0,greatest(-1.0,
            sin(radians(?))*sin(radians(latitude))+cos(radians(?))*cos(radians(latitude))*cos(radians(longitude)-radians(?))))) as distance_m,
            (select count(*) from bill b where b.place_id=p.id and b.deleted_at is null) as bill_count
            from place p where p.user_id=?) nearby where distance_m<=? order by distance_m limit 100
            """,latitude,latitude,longitude,user.id(),radiusMeters);
    }
}
