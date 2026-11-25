package com.app.invitation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class InvitationExpiryJob {
 private final JdbcTemplate jdbc;
 public InvitationExpiryJob(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Scheduled(cron="${app.invitation.expiry.cron:0 */15 * * * *}")
 public void expirePublications(){
  jdbc.update("UPDATE invitation SET status='EXPIRED',updated_at=now() WHERE status='PUBLISHED' AND expires_at<=now()");
 }
}
