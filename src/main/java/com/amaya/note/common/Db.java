package com.amaya.note.common;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class Db {
    public final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw ApiException.missing();
        return rows.getFirst();
    }
    public void lock(UUID user) { one("select id from app_user where id=? for update", user); }
    public Map<String,Object> bill(UUID user, UUID id) {
        return one("select b.*, l.currency from bill b join ledger l on l.id=b.ledger_id where b.id=? and b.user_id=? and b.deleted_at is null", id, user);
    }
    public static int limit(int limit) { return Math.max(1, Math.min(limit, 100)); }
    public static int offset(int offset) { return Math.max(0, offset); }
}
