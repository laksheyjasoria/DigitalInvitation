package com.app.payment;

import com.app.audit.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSettlementService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogService audit;

    public PaymentSettlementService(JdbcTemplate jdbc, ObjectMapper mapper, AuditLogService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    /**
     * Applies a captured payment exactly once. The order row is the serialization
     * point shared by browser verification and asynchronous provider webhooks.
     */
    @Transactional
    public void settle(UUID orderId, String provider, String paymentId) {
        var orders = jdbc.query(
                "SELECT id,user_id,invitation_id,order_type,status,pricing_snapshot::text AS snapshot " +
                "FROM customer_order WHERE id=? FOR UPDATE",
                (rs, n) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", rs.getObject("user_id", UUID.class));
                    row.put("invitationId", rs.getObject("invitation_id", UUID.class));
                    row.put("type", rs.getString("order_type"));
                    row.put("status", rs.getString("status"));
                    row.put("snapshot", rs.getString("snapshot"));
                    return row;
                }, orderId);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("Order not found");
        }

        Map<String, Object> order = orders.get(0);
        String orderStatus = (String) order.get("status");
        if ("PAID".equals(orderStatus)) {
            return;
        }
        if (!"CREATED".equals(orderStatus) && !"PAYMENT_PENDING".equals(orderStatus)) {
            throw new IllegalStateException("Payment cannot settle an order in status " + orderStatus);
        }

        UUID invitationId = (UUID) order.get("invitationId");
        UUID userId = (UUID) order.get("userId");
        String orderType = (String) order.get("type");

        if ("INVITATION".equals(orderType)) {
            int changed = jdbc.update(
                    "UPDATE invitation SET status='READY_TO_PUBLISH',updated_at=now() " +
                    "WHERE id=? AND status='PENDING_PAYMENT'",
                    invitationId);
            if (changed != 1) {
                throw new IllegalStateException("Invitation is not awaiting payment settlement");
            }
        } else if ("EXTENSION".equals(orderType)) {
            settleExtension(orderId, invitationId, userId, (String) order.get("snapshot"));
        } else {
            throw new IllegalStateException("Unsupported order type");
        }

        int paid = jdbc.update(
                "UPDATE customer_order SET status='PAID',updated_at=now() " +
                "WHERE id=? AND status IN ('CREATED','PAYMENT_PENDING')",
                orderId);
        if (paid != 1) {
            throw new IllegalStateException("Order payment state changed during settlement");
        }

        audit.record(userId, "PAYMENT_SETTLED", "ORDER", orderId.toString(), "SUCCESS",
                Map.of("provider", provider, "paymentId", paymentId, "orderType", orderType));
    }

    private void settleExtension(UUID orderId, UUID invitationId, UUID userId, String snapshotText) {
        try {
            JsonNode snapshot = mapper.readTree(snapshotText);
            int days = snapshot.path("extensionDays").asInt(0);
            String publicationIdText = snapshot.path("publicationId").asText("");
            if (days <= 0 || publicationIdText.isBlank()) {
                throw new IllegalStateException("Extension order is missing its purchase-time terms");
            }
            UUID publicationId = UUID.fromString(publicationIdText);

            var publications = jdbc.query(
                    "SELECT id,expires_at FROM invitation_publication " +
                    "WHERE id=? AND invitation_id=? AND revoked_at IS NULL FOR UPDATE",
                    (rs, n) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rs.getObject("id", UUID.class));
                        row.put("expiresAt", rs.getTimestamp("expires_at").toInstant());
                        return row;
                    }, publicationId, invitationId);
            if (publications.isEmpty()) {
                throw new IllegalStateException("The purchased invitation publication no longer exists or was revoked");
            }

            Instant previousExpiry = (Instant) publications.get(0).get("expiresAt");
            int changed = jdbc.update(
                    "UPDATE invitation_publication SET expires_at=GREATEST(expires_at,now()) + (? * interval '1 day') " +
                    "WHERE id=? AND invitation_id=? AND revoked_at IS NULL",
                    days, publicationId, invitationId);
            if (changed != 1) {
                throw new IllegalStateException("Invitation publication could not be extended");
            }

            Instant newExpiry = jdbc.queryForObject(
                    "SELECT expires_at FROM invitation_publication WHERE id=?",
                    Timestamp.class, publicationId).toInstant();

            int invitationChanged = jdbc.update(
                    "UPDATE invitation SET status='PUBLISHED',expires_at=?,updated_at=now() " +
                    "WHERE id=? AND status IN ('PUBLISHED','EXPIRED')",
                    Timestamp.from(newExpiry), invitationId);
            if (invitationChanged != 1) {
                throw new IllegalStateException("Invitation is not in an extendable state");
            }

            jdbc.update(
                    "INSERT INTO invitation_extension(invitation_id,order_id,publication_id,extension_days,previous_expiry,new_expiry) " +
                    "VALUES (?,?,?,?,?,?)",
                    invitationId, orderId, publicationId, days,
                    Timestamp.from(previousExpiry), Timestamp.from(newExpiry));
            audit.record(userId, "INVITATION_EXTENDED", "INVITATION", invitationId.toString(), "SUCCESS",
                    Map.of("orderId", orderId.toString(), "publicationId", publicationId.toString(),
                            "extensionDays", days, "previousExpiry", previousExpiry.toString(),
                            "newExpiry", newExpiry.toString()));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to apply paid invitation extension", ex);
        }
    }
}
