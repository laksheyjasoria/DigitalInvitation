package com.app.auth;

import com.app.common.api.ApiResponse;
import com.app.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final JdbcTemplate jdbc; private final PasswordEncoder encoder; private final JwtService jwt;
    public AuthController(JdbcTemplate jdbc,PasswordEncoder encoder,JwtService jwt){this.jdbc=jdbc;this.encoder=encoder;this.jwt=jwt;}
    public record RegisterRequest(@Email @NotBlank String email,@NotBlank @Size(min=12,max=72) String password,@NotBlank @Size(max=120) String displayName){}
    public record LoginRequest(@Email @NotBlank String email,@NotBlank String password){}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest r){
        try{UUID id=UUID.randomUUID();jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,status) VALUES (?,?,?,?, 'ACTIVE')",id,r.email().trim().toLowerCase(),encoder.encode(r.password()),r.displayName().trim());return ApiResponse.success(Map.of("userId",id,"email",r.email().trim().toLowerCase()));}
        catch(DuplicateKeyException e){throw new IllegalArgumentException("An account with this email already exists");}
    }
    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest r){
        var users=jdbc.query("SELECT id,email,password_hash,role,status FROM app_user WHERE email=?",(rs,n)->Map.of("id",rs.getObject("id").toString(),"email",rs.getString("email"),"hash",rs.getString("password_hash"),"role",rs.getString("role"),"status",rs.getString("status")),r.email().trim().toLowerCase());
        if(users.isEmpty()||!encoder.matches(r.password(),(String)users.get(0).get("hash")))throw new IllegalArgumentException("Invalid email or password");
        var u=users.get(0);if(!"ACTIVE".equals(u.get("status")))throw new IllegalArgumentException("Account is not active");
        String token=jwt.issue((String)u.get("id"),(String)u.get("email"),(String)u.get("role"));
        return ApiResponse.success(Map.of("accessToken",token,"tokenType","Bearer","user",Map.of("id",u.get("id"),"email",u.get("email"),"role",u.get("role"))));
    }
    @GetMapping("/me")
    public ApiResponse<?> me(org.springframework.security.core.Authentication auth){return ApiResponse.success(jdbc.queryForMap("SELECT id,email,display_name,role,created_at FROM app_user WHERE id=? AND status='ACTIVE'",UUID.fromString(auth.getName())));}
}
