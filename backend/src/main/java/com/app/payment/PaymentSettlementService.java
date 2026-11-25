package com.app.payment;

import com.app.audit.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSettlementService {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final AuditLogService audit;
 public PaymentSettlementService(JdbcTemplate jdbc,ObjectMapper mapper,AuditLogService audit){this.jdbc=jdbc;this.mapper=mapper;this.audit=audit;}
 @Transactional
 public void settle(UUID orderId,String provider,String paymentId){
  var rows=jdbc.query("SELECT id,user_id,invitation_id,order_type,status,pricing_snapshot::text snapshot FROM customer_order WHERE id=?",
   (rs,n)->Map.of("userId",rs.getObject("user_id",UUID.class),"invitationId",rs.getObject("invitation_id",UUID.class),"type",rs.getString("order_type"),"status",rs.getString("status"),"snapshot",rs.getString("snapshot")),orderId);
  if(rows.isEmpty())throw new IllegalArgumentException("Order not found");
  var order=rows.get(0);if("PAID".equals(order.get("status")))return;
  int paid=jdbc.update("UPDATE customer_order SET status='PAID',updated_at=now() WHERE id=? AND status IN ('CREATED','PAYMENT_PENDING')",orderId);
  if(paid==0)return;
  UUID invitationId=(UUID)order.get("invitationId"),userId=(UUID)order.get("userId");
  if("INVITATION".equals(order.get("type"))){
   jdbc.update("UPDATE invitation SET status='READY_TO_PUBLISH',updated_at=now() WHERE id=? AND status='PENDING_PAYMENT'",invitationId);
  }else if("EXTENSION".equals(order.get("type"))){
   try{
    var snapshot=mapper.readTree((String)order.get("snapshot"));int days=snapshot.path("extensionDays").asInt(0);
    if(days<=0)throw new IllegalStateException("Extension order has invalid duration");
    var publications=jdbc.query("SELECT id,expires_at FROM invitation_publication WHERE invitation_id=? ORDER BY published_at DESC LIMIT 1",
     (rs,n)->Map.of("id",rs.getObject("id",UUID.class),"expiresAt",rs.getTimestamp("expires_at").toInstant()),invitationId);
    if(publications.isEmpty())throw new IllegalStateException("No invitation publication exists to extend");
    UUID publicationId=(UUID)publications.get(0).get("id");var previous=(java.time.Instant)publications.get(0).get("expiresAt");
    jdbc.update("UPDATE invitation_publication SET expires_at=GREATEST(expires_at,now())+(? * interval '1 day') WHERE id=?",days,publicationId);
    var next=jdbc.queryForObject("SELECT expires_at FROM invitation_publication WHERE id=?",java.sql.Timestamp.class,publicationId).toInstant();
    jdbc.update("UPDATE invitation SET status='PUBLISHED',expires_at=?,updated_at=now() WHERE id=?",java.sql.Timestamp.from(next),invitationId);
    jdbc.update("INSERT INTO invitation_extension(invitation_id,order_id,publication_id,extension_days,previous_expiry,new_expiry) VALUES (?,?,?,?,?,?)",
     invitationId,orderId,publicationId,days,java.sql.Timestamp.from(previous),java.sql.Timestamp.from(next));
   }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Unable to apply paid invitation extension",e);}
  }else throw new IllegalStateException("Unsupported order type");
  audit.record(userId,"PAYMENT_SETTLED","ORDER",orderId.toString(),"SUCCESS",Map.of("provider",provider,"paymentId",paymentId,"orderType",order.get("type")));
 }
}
