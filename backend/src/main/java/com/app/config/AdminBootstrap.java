package com.app.config;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;
 @Value("${app.bootstrap.admin.email:}") private String email;
 @Value("${app.bootstrap.admin.password:}") private String password;
 public AdminBootstrap(JdbcTemplate jdbc,PasswordEncoder encoder){this.jdbc=jdbc;this.encoder=encoder;}
 @Override public void run(ApplicationArguments args){
  if(email==null||email.isBlank()||password==null||password.length()<16)return;
  var existing=jdbc.queryForList("SELECT id FROM app_user WHERE email=?",email.trim().toLowerCase());
  if(existing.isEmpty())jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role,status) VALUES (?,?,?,?, 'SUPER_ADMIN','ACTIVE')",
   UUID.randomUUID(),email.trim().toLowerCase(),encoder.encode(password),"Platform Administrator");
 }
}
