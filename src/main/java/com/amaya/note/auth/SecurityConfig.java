package com.amaya.note.auth;
import com.amaya.note.common.Hashes;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, JdbcTemplate jdbc) throws Exception {
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ") && header.length() < 256) {
                    var rows = jdbc.queryForList("select u.id,u.role from auth_session s join app_user u on u.id=s.user_id where s.token_hash=? and s.expires_at>now()", Hashes.sha256(header.substring(7)));
                    if (!rows.isEmpty()) {
                        var row = rows.getFirst();
                        var user = new CurrentUser((UUID) row.get("id"), (String) row.get("role"));
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
                    }
                }
                chain.doFilter(req,res);
            }
        };
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll().requestMatchers("/api/auth/register", "/api/auth/login", "/actuator/health").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> {
                res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"msg\":\"Authentication required\",\"data\":null}");
            }).accessDeniedHandler((req,res,ex) -> {
                res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"code\":\"FORBIDDEN\",\"msg\":\"Access denied\",\"data\":null}");
            })).addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
