package com.amaya.note.points;
import com.amaya.note.common.Db;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PointService {
    private final Db db;
    public PointService(Db db) { this.db=db; }
    // Callers hold the user's database row lock for the complete business transaction.
    public void award(UUID user,String source,String type,int amount,int dailyCap,UUID bill,UUID media,String region) {
        LocalDate day=LocalDate.now(ZoneOffset.UTC);
        if(dailyCap>0 && db.jdbc.queryForObject("select count(*) from point_event where user_id=? and type=? and reward_day=?",Long.class,user,type,day)>=dailyCap) return;
        db.jdbc.update("insert into point_event(id,user_id,source_key,type,amount,bill_id,media_id,region_id,reward_day) values(?,?,?,?,?,?,?,?,?) on conflict(user_id,source_key) do nothing",
            UUID.randomUUID(),user,source,type,amount,bill,media,region,day);
    }
    public void bill(UUID user,UUID bill) { award(user,"bill:"+bill,"BILL",10,10,bill,null,null); }
    public void media(UUID user,UUID bill,UUID media,String kind) {
        String type=kind.equals("IMAGE")?"IMAGE":"VIDEO";
        int billCap=type.equals("IMAGE")?5:2;
        if(db.jdbc.queryForObject("select count(*) from point_event where user_id=? and bill_id=? and type=?",Long.class,user,bill,type)>=billCap) return;
        award(user,"media:"+media,type,type.equals("IMAGE")?2:5,type.equals("IMAGE")?20:5,bill,media,null);
    }
    public void checkin(UUID user,UUID bill,String place,String city,String province,String country) {
        String today=LocalDate.now(ZoneOffset.UTC).toString();
        award(user,"onsite:"+place+":"+today,"ONSITE",5,5,bill,null,place);
        award(user,"place:"+place,"PLACE",10,5,null,null,place);
        award(user,"city:"+city,"CITY",100,0,null,null,city);
        if(province!=null) award(user,"province:"+province,"PROVINCE",300,0,null,null,province);
        award(user,"country:"+country,"COUNTRY",1000,0,null,null,country);
    }
    public void revokeMedia(UUID user,UUID media) {
        db.jdbc.update("update point_event set status='REVOKED',revoked_at=now() where user_id=? and media_id=? and status='ACTIVE'",user,media);
    }
    public void revokeBill(UUID user,UUID bill) {
        db.jdbc.update("update point_event set status='REVOKED',revoked_at=now() where user_id=? and bill_id=? and status='ACTIVE'",user,bill);
        reconcileExploration(user);
    }
    public void revokeCheckin(UUID user,UUID bill) {
        db.jdbc.update("update point_event set status='REVOKED',revoked_at=now() where user_id=? and bill_id=? and type='ONSITE' and status='ACTIVE'",user,bill);
        reconcileExploration(user);
    }
    private void reconcileExploration(UUID user) {
        // A region stays unlocked while any non-deleted, verified bill still supports it.
        db.jdbc.update("""
            update point_event e set status='REVOKED',revoked_at=now()
            where e.user_id=? and e.status='ACTIVE' and e.type in ('PLACE','CITY','PROVINCE','COUNTRY')
            and not exists(select 1 from checkin c join bill b on b.id=c.bill_id
                where c.user_id=e.user_id and c.status='VERIFIED' and b.deleted_at is null
                and e.region_id=case e.type when 'PLACE' then c.canonical_place_id when 'CITY' then c.city_id
                    when 'PROVINCE' then c.province_id when 'COUNTRY' then c.country_id end)
            """,user);
    }
    public Object balance(UUID user) {
        long points=db.jdbc.queryForObject("select coalesce(sum(amount),0) from point_event where user_id=? and status='ACTIVE'",Long.class,user);
        return Map.of("availablePoints",points,"growth",points,"level",1+(int)Math.sqrt(points/100.0));
    }
}
