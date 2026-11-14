package com.amaya.note.statistics;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.*;
import java.time.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/statistics")
public class StatisticsController {
    private final Db db;
    public StatisticsController(Db db){this.db=db;}
    @GetMapping("/summary") public Object summary(@AuthenticationPrincipal CurrentUser user,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        validate(from,to);
        return db.jdbc.queryForList("""
            select l.currency,count(*) as bill_count,
                coalesce(sum(case when b.kind='INCOME' then b.amount else 0 end),0) as income,
                coalesce(sum(case when b.kind='EXPENSE' then b.amount when b.kind='REFUND' then -b.amount else 0 end),0) as net_expense
            from bill b join ledger l on l.id=b.ledger_id
            where b.user_id=? and b.deleted_at is null and b.occurred_at>=? and b.occurred_at<? group by l.currency order by l.currency
            """,user.id(),from.atStartOfDay().atOffset(ZoneOffset.UTC),to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
    }
    @GetMapping("/categories") public Object categories(@AuthenticationPrincipal CurrentUser user,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        validate(from,to);
        return db.jdbc.queryForList("""
            select l.currency,coalesce(original.category,b.category) as category,
                sum(case when b.kind='REFUND' then -b.amount else b.amount end) as net_expense
            from bill b join ledger l on l.id=b.ledger_id left join bill original on original.id=b.original_bill_id
            where b.user_id=? and b.deleted_at is null and b.kind in ('EXPENSE','REFUND')
                and b.occurred_at>=? and b.occurred_at<?
            group by l.currency,coalesce(original.category,b.category) order by net_expense desc
            """,user.id(),from.atStartOfDay().atOffset(ZoneOffset.UTC),to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
    }
    @GetMapping("/regions") public Object regions(@AuthenticationPrincipal CurrentUser user) {
        return db.jdbc.queryForList("""
            select c.country_id,c.province_id,c.city_id,l.currency,count(*) as bill_count,
                sum(case when b.kind='EXPENSE' then b.amount when b.kind='REFUND' then -b.amount else 0 end) as net_expense
            from bill b join checkin c on c.bill_id=b.id and c.status='VERIFIED' join ledger l on l.id=b.ledger_id
            where b.user_id=? and b.deleted_at is null
            group by c.country_id,c.province_id,c.city_id,l.currency order by c.country_id,c.province_id,c.city_id
            """,user.id());
    }
    private void validate(LocalDate from,LocalDate to) {
        if(from.isAfter(to)||from.isBefore(LocalDate.of(1900,1,1))||to.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1))) throw ApiException.bad("Invalid date range");
    }
}
