package com.app.media;

import com.app.common.api.ApiResponse;
import com.app.storage.StorageProvider;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/public/media")
public class PublicMediaController {
 private final JdbcTemplate jdbc;private final StorageProvider storage;
 public PublicMediaController(JdbcTemplate jdbc,StorageProvider storage){this.jdbc=jdbc;this.storage=storage;}
 @GetMapping("/{mediaId}")
 public ResponseEntity<StreamingResponseBody> media(@PathVariable UUID mediaId){
  var rows=jdbc.query("SELECT m.storage_key,m.content_type,m.original_filename FROM customer_media m JOIN invitation_publication p ON p.invitation_id=m.invitation_id WHERE m.id=? AND m.visibility='PUBLIC' AND p.revoked_at IS NULL AND p.expires_at>now()",
   (rs,n)->Map.of("key",rs.getString("storage_key"),"type",rs.getString("content_type"),"name",rs.getString("original_filename")),mediaId);
  if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
  var row=rows.get(0);StreamingResponseBody body=out->{try(var in=storage.download((String)row.get("key"))){in.transferTo(out);}};
  return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)row.get("type")))
   .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+((String)row.get("name")).replaceAll("[\\r\\n\\"]","_")+"\"")
   .header("X-Content-Type-Options","nosniff").body(body);
 }
}
