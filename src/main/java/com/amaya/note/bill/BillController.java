package com.amaya.note.bill;
import com.amaya.note.auth.CurrentUser;
import com.amaya.note.common.Db;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/bills")
public class BillController {
    private final Db db; private final BillService bills;
    public BillController(Db db,BillService bills) {this.db=db;this.bills=bills;}
    @PostMapping public Object create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody BillService.Input input) {return bills.create(user.id(),input);}
    @GetMapping public Object list(@AuthenticationPrincipal CurrentUser user,@RequestParam(required=false) UUID ledgerId,@RequestParam(required=false) UUID placeId,
        @RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset) {
        return db.jdbc.queryForList("select b.*,l.currency from bill b join ledger l on l.id=b.ledger_id where b.user_id=? and b.deleted_at is null and (cast(? as uuid) is null or b.ledger_id=?) and (cast(? as uuid) is null or b.place_id=?) order by b.occurred_at desc,b.id limit ? offset ?",
            user.id(),ledgerId,ledgerId,placeId,placeId,Db.limit(limit),Db.offset(offset));
    }
    @GetMapping("/{id}") public Object get(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID id) {return db.bill(user.id(),id);}
    @PatchMapping("/{id}") public Object edit(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID id,@Valid @RequestBody BillService.Edit input) {return bills.edit(user.id(),id,input);}
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal CurrentUser user,@PathVariable UUID id) {bills.delete(user.id(),id);}
}
