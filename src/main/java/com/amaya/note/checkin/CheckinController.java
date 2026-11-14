package com.amaya.note.checkin;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.Db;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/checkins")
public class CheckinController {
    private final Db db;private final CheckinService service;
    public CheckinController(Db db,CheckinService service) {this.db=db;this.service=service;}
    @PostMapping public Object create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody CheckinService.Input input) {return service.create(user.id(),input);}
    @GetMapping public Object list(@AuthenticationPrincipal CurrentUser user,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return db.jdbc.queryForList("select * from checkin where user_id=? order by created_at desc,id limit ? offset ?",user.id(),Db.limit(limit),Db.offset(offset));
    }
}
