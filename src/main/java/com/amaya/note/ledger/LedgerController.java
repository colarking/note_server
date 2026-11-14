package com.amaya.note.ledger;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ledgers")
public class LedgerController {
    private final Db db;
    public LedgerController(Db db) { this.db=db; }
    public record Input(@NotBlank @Size(max=80) String name, @NotNull @Pattern(regexp="[A-Z]{3}") String currency, @DecimalMin("0") @Digits(integer=14,fraction=2) BigDecimal budget) {}
    @GetMapping public Object list(@AuthenticationPrincipal CurrentUser user) { return db.jdbc.queryForList("select * from ledger where user_id=? order by created_at",user.id()); }
    @PostMapping public Object create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody Input input) {
        try { Currency.getInstance(input.currency()); } catch (IllegalArgumentException e) { throw ApiException.bad("Unsupported currency"); }
        UUID id=UUID.randomUUID();
        db.jdbc.update("insert into ledger(id,user_id,name,currency,budget) values(?,?,?,?,?)",id,user.id(),input.name(),input.currency(),input.budget());
        return db.one("select * from ledger where id=?",id);
    }
}
