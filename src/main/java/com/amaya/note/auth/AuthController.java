package com.amaya.note.auth;
import com.amaya.note.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db; private final PasswordEncoder passwords;
    private final String dummyHash;
    public AuthController(Db db, PasswordEncoder passwords) { this.db=db; this.passwords=passwords; this.dummyHash=passwords.encode(UUID.randomUUID().toString()); }
    public record Register(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=10,max=72) String password, @NotBlank @Size(max=80) String nickname) {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> register(@Valid @RequestBody Register input) {
        checkPassword(input.password());
        UUID id=UUID.randomUUID();
        db.jdbc.update("insert into app_user(id,email,password_hash,nickname) values(?,?,?,?)", id, input.email().trim().toLowerCase(Locale.ROOT), passwords.encode(input.password()), input.nickname());
        db.jdbc.update("insert into ledger(id,user_id,name,currency) values(?,?,?,?)", UUID.randomUUID(),id,"Default","CNY");
        return session(id);
    }
    @PostMapping("/login") @Transactional
    public Map<String,Object> login(@Valid @RequestBody Login input) {
        checkPassword(input.password());
        var users=db.jdbc.queryForList("select id,password_hash from app_user where email=?",input.email().trim().toLowerCase(Locale.ROOT));
        String hash=users.isEmpty()?dummyHash:(String)users.getFirst().get("password_hash");
        boolean matches=passwords.matches(input.password(),hash);
        if (users.isEmpty() || !matches) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid email or password");
        return session((UUID)users.getFirst().get("id"));
    }
    private void checkPassword(String value) { if(value.getBytes(StandardCharsets.UTF_8).length>72) throw ApiException.bad("Password must be at most 72 UTF-8 bytes"); }
    private Map<String,Object> session(UUID user) {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.jdbc.update("delete from auth_session where user_id=? and expires_at<=now()",user);
        db.jdbc.update("insert into auth_session(token_hash,user_id,expires_at) values(?,?,now()+interval '7 days')",Hashes.sha256(token),user);
        return Map.of("accessToken",token,"tokenType","Bearer","expiresIn",604800,"userId",user);
    }
    @GetMapping("/me") public Object me(@AuthenticationPrincipal CurrentUser user) {
        return db.one("select id,email,nickname,created_at from app_user where id=?",user.id());
    }
    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String header) { db.jdbc.update("delete from auth_session where token_hash=?",Hashes.sha256(header.substring(7))); }
}
