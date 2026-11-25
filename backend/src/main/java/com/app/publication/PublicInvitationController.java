package com.app.publication;

import com.app.common.api.ApiResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public")
public class PublicInvitationController {
    private final JdbcTemplate jdbc;
    public PublicInvitationController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/invitations/{slug}") public ApiResponse<?> invitation(@PathVariable String slug){
        var rows=jdbc.query("SELECT published_snapshot,expires_at,revoked_at FROM invitation_publication WHERE public_slug=?",(rs,n)->Map.of("snapshot",rs.getString("published_snapshot"),"expires",rs.getTimestamp("expires_at").toInstant(),"revoked",rs.getTimestamp("revoked_at")==null?"":rs.getTimestamp("revoked_at").toInstant().toString()),slug);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invitation not found");
        var r=rows.get(0);if(!r.get("revoked").equals("")||!((Instant)r.get("expires")).isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.GONE,"Invitation expired or unavailable");
        return ApiResponse.success(r.get("snapshot"));
    }
}
