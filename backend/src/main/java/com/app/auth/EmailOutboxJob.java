package com.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
public class EmailOutboxJob {
 private final JdbcTemplate jdbc;private final ObjectProvider<JavaMailSender> senderProvider;private final ObjectMapper mapper;private final String from;
 public EmailOutboxJob(JdbcTemplate jdbc,ObjectProvider<JavaMailSender> senderProvider,ObjectMapper mapper,
  @Value("${app.mail.from:no-reply@example.invalid}")String from){this.jdbc=jdbc;this.senderProvider=senderProvider;this.mapper=mapper;this.from=from;}
 @Scheduled(fixedDelayString="${app.mail.outbox-delay-ms:15000}")
 public void dispatch(){
  JavaMailSender sender=senderProvider.getIfAvailable();if(sender==null)return;
  List<Map<String,Object>> batch=jdbc.query("SELECT id,recipient,template_key,payload,attempts FROM email_outbox WHERE status='PENDING' AND next_attempt_at<=now() ORDER BY created_at LIMIT 20",
   (rs,n)->Map.of("id",rs.getObject("id"),"recipient",rs.getString("recipient"),"template",rs.getString("template_key"),"payload",rs.getString("payload"),"attempts",rs.getInt("attempts")));
  for(var row:batch){
   try{
    Map payload=mapper.readValue((String)row.get("payload"),Map.class);String token=String.valueOf(payload.get("token"));String base=String.valueOf(payload.get("baseUrl")).replaceAll("/+$","");
    String template=(String)row.get("template");String subject,link,body;
    if(template.equals("EMAIL_VERIFY")){link=base+"/verify.html?token="+token;subject="Verify your DigitalInvitation account";body="Welcome. Verify your email address using this link:\n\n"+link+"\n\nThis link expires in 24 hours. If you did not create this account, ignore this message.";}
    else{link=base+"/reset.html?token="+token;subject="Reset your DigitalInvitation password";body="Use this link to reset your password:\n\n"+link+"\n\nThis link expires in one hour. If you did not request a reset, ignore this message.";}
    SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo((String)row.get("recipient"));message.setSubject(subject);message.setText(body);sender.send(message);
    jdbc.update("UPDATE email_outbox SET status='SENT',sent_at=now(),attempts=attempts+1 WHERE id=?",row.get("id"));
   }catch(Exception ex){
    int attempts=((Number)row.get("attempts")).intValue()+1;
    jdbc.update("UPDATE email_outbox SET attempts=attempts+1,status=?,next_attempt_at=now()+interval '5 minutes' WHERE id=?",attempts>=5?"FAILED":"PENDING",row.get("id"));
   }
  }
 }
}
