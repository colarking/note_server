package com.amaya.note.achievement;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.Db;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/achievements")
public class AchievementController {
    private final Db db;
    public AchievementController(Db db){this.db=db;}
    @GetMapping public Object list(@AuthenticationPrincipal CurrentUser user) {
        long bills=db.jdbc.queryForObject("select count(*) from bill where user_id=? and deleted_at is null",Long.class,user.id());
        long cities=db.jdbc.queryForObject("select count(distinct city_id) from checkin where user_id=? and status='VERIFIED'",Long.class,user.id());
        return List.of(badge("FIRST_BILL",bills,1),badge("BILLS_10",bills,10),badge("BILLS_100",bills,100),badge("CITIES_5",cities,5));
    }
    @GetMapping("/daily-task") public Object daily(@AuthenticationPrincipal CurrentUser user) {
        long count=db.jdbc.queryForObject("select count(*) from bill where user_id=? and deleted_at is null and created_at>=?",Long.class,user.id(),LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC));
        return Map.of("code","RECORD_TODAY","progress",Math.min(count,1),"target",1,"completed",count>0,"additionalPoints",0);
    }
    private Object badge(String code,long progress,int target) {return Map.of("code",code,"progress",Math.min(progress,target),"target",target,"unlocked",progress>=target);}
}
