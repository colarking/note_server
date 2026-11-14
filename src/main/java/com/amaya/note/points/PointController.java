package com.amaya.note.points;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.Db;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/points")
public class PointController {
    private final Db db; private final PointService points;
    public PointController(Db db,PointService points) {this.db=db;this.points=points;}
    @GetMapping public Object balance(@AuthenticationPrincipal CurrentUser user) {return points.balance(user.id());}
    @GetMapping("/events") public Object events(@AuthenticationPrincipal CurrentUser user,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return db.jdbc.queryForList("select * from point_event where user_id=? order by created_at desc,id limit ? offset ?",user.id(),Db.limit(limit),Db.offset(offset));
    }
    @GetMapping("/rules") public Object rules() {
        return Map.of("version",1,"dayTimezone","UTC","bill",Map.of("points",10,"dailyCap",10),
            "image",Map.of("points",2,"perBillCap",5,"dailyCap",20),"video",Map.of("points",5,"perBillCap",2,"dailyCap",5),
            "onsite",Map.of("points",5,"dailyCap",5),"place",Map.of("points",10,"dailyCap",5),
            "city",100,"province",300,"country",1000);
    }
}
