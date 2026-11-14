package com.amaya.note.admin;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.Db;
import com.amaya.note.checkin.CheckinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final Db db;private final CheckinService service;
    public AdminController(Db db,CheckinService service) {this.db=db;this.service=service;}
    public record Reason(@NotBlank @Size(max=500) String reason) {}
    @GetMapping("/checkins") public Object pending(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return db.jdbc.queryForList("select c.*,p.name,p.address,p.latitude as place_latitude,p.longitude as place_longitude from checkin c join bill b on b.id=c.bill_id join place p on p.id=b.place_id where c.status='PENDING' order by c.created_at,c.id limit ? offset ?",Db.limit(limit),Db.offset(offset));
    }
    @PostMapping("/checkins/{id}/review") public Object review(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID id,@Valid @RequestBody CheckinService.Review input) {return service.review(user.id(),id,input);}
    @PostMapping("/checkins/{id}/revoke")
    public void revoke(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID id,@Valid @RequestBody Reason input) {service.revoke(user.id(),id,input.reason());}
    @GetMapping("/audit") public Object audit(@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return db.jdbc.queryForList("select * from audit_log order by id desc limit ? offset ?",Db.limit(limit),Db.offset(offset));
    }
}
