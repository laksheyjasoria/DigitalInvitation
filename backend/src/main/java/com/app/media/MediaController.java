package com.app.media;

import com.app.common.api.ApiResponse;
import com.app.storage.StorageProvider;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/invitations/{invitationId}/media")
public class MediaController {
 private final JdbcTemplate jdbc;private final StorageProvider storage;
 public MediaController(JdbcTemplate jdbc,StorageProvider storage){this.jdbc=jdbc;this.storage=storage;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public ApiResponse<?> upload(@PathVariable UUID invitationId,@RequestParam("file")MultipartFile file,Authentication auth){
  UUID owner=UUID.fromString(auth.getName());
  jdbc.queryForObject("SELECT id FROM invitation WHERE id=? AND owner_user_id=? AND status IN ('DRAFT','PENDING_PAYMENT')",UUID.class,invitationId,owner);
  long maxBytes=jdbc.query("SELECT setting_value::text FROM system_setting WHERE setting_key='invitation.media.max-file-bytes'",rs->rs.next()?Long.parseLong(rs.getString(1).replace("\"","")):10485760L);
  if(file.isEmpty()||file.getSize()>maxBytes)throw new IllegalArgumentException("File is empty or exceeds the configured upload limit");
  String type=file.getContentType()==null?"application/octet-stream":file.getContentType();
  if(!java.util.Set.of("image/jpeg","image/png","image/webp","image/gif").contains(type))throw new IllegalArgumentException("Only JPEG, PNG, WebP and GIF images are currently supported");
  throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Storage upload adapter is not configured for this FileServer API contract");
 }
 @GetMapping public ApiResponse<?> list(@PathVariable UUID invitationId,Authentication auth){
  return ApiResponse.success(jdbc.queryForList("SELECT id,original_filename,content_type,size_bytes,visibility,created_at FROM customer_media WHERE invitation_id=? AND owner_user_id=? ORDER BY created_at DESC",invitationId,UUID.fromString(auth.getName())));
 }
 @PatchMapping("/{mediaId}/visibility") public ApiResponse<?> visibility(@PathVariable UUID invitationId,@PathVariable UUID mediaId,@RequestBody Map<String,String> body,Authentication auth){
  String visibility=body.get("visibility");if(!java.util.Set.of("PRIVATE","PUBLIC").contains(visibility))throw new IllegalArgumentException("Visibility must be PRIVATE or PUBLIC");
  int n=jdbc.update("UPDATE customer_media SET visibility=? WHERE id=? AND invitation_id=? AND owner_user_id=?",visibility,mediaId,invitationId,UUID.fromString(auth.getName()));
  if(n==0)throw new IllegalArgumentException("Media not found");return ApiResponse.success(Map.of("id",mediaId,"visibility",visibility));
 }
}
