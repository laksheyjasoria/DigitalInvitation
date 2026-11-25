package com.app.media;

import com.app.common.api.ApiResponse;
import com.app.storage.StorageProvider;
import com.app.storage.StoredObject;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/invitations/{invitationId}/media")
public class MediaController {
 private final JdbcTemplate jdbc;private final StorageProvider storage;
 public MediaController(JdbcTemplate jdbc,StorageProvider storage){this.jdbc=jdbc;this.storage=storage;}
 @PostMapping
 public ApiResponse<?> upload(@PathVariable UUID invitationId,@RequestParam("file")MultipartFile file,Authentication auth){
  UUID owner=UUID.fromString(auth.getName());
  jdbc.queryForObject("SELECT id FROM invitation WHERE id=? AND owner_user_id=? AND status IN ('DRAFT','PENDING_PAYMENT')",UUID.class,invitationId,owner);
  long maxBytes=jdbc.query("SELECT setting_value::text FROM system_setting WHERE setting_key='invitation.media.max-file-bytes'",rs->rs.next()?Long.parseLong(rs.getString(1).replace("\"","")):10485760L);
  long maxFiles=jdbc.query("SELECT setting_value::text FROM system_setting WHERE setting_key='invitation.media.max-files'",rs->rs.next()?Long.parseLong(rs.getString(1).replace("\"","")):30L);
  if(file.isEmpty()||file.getSize()>maxBytes)throw new IllegalArgumentException("File is empty or exceeds the configured upload limit");
  Long count=jdbc.queryForObject("SELECT count(*) FROM customer_media WHERE invitation_id=? AND owner_user_id=?",Long.class,invitationId,owner);
  if(count!=null&&count>=maxFiles)throw new IllegalArgumentException("Invitation has reached its configured media limit");
  String type=file.getContentType()==null?"application/octet-stream":file.getContentType();
  if(!java.util.Set.of("image/jpeg","image/png","image/webp","image/gif").contains(type))throw new IllegalArgumentException("Only JPEG, PNG, WebP and GIF images are currently supported");
  String name=java.util.UUID.randomUUID()+"-"+file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]","_");
  try{
   StoredObject stored=storage.upload(name,type,file.getSize(),file.getInputStream());
   UUID id=UUID.randomUUID();
   jdbc.update("INSERT INTO customer_media(id,owner_user_id,invitation_id,storage_provider,storage_key,original_filename,content_type,size_bytes) VALUES (?,?,?,?,?,?,?,?)",
    id,owner,invitationId,stored.provider(),stored.storageKey(),file.getOriginalFilename(),type,file.getSize());
   return ApiResponse.success(Map.of("id",id,"filename",file.getOriginalFilename(),"contentType",type,"sizeBytes",file.getSize(),"visibility","PRIVATE"));
  }catch(Exception e){throw new IllegalStateException("Media upload failed; retry after checking storage configuration",e);}
 }
 @GetMapping public ApiResponse<?> list(@PathVariable UUID invitationId,Authentication auth){
  return ApiResponse.success(jdbc.queryForList("SELECT id,original_filename,content_type,size_bytes,visibility,created_at FROM customer_media WHERE invitation_id=? AND owner_user_id=? ORDER BY created_at DESC",invitationId,UUID.fromString(auth.getName())));
 }
 @GetMapping("/{mediaId}/content")
 public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID invitationId,@PathVariable UUID mediaId,Authentication auth){
  var rows=jdbc.query("SELECT storage_key,content_type,original_filename FROM customer_media WHERE id=? AND invitation_id=? AND owner_user_id=?",
   (rs,n)->Map.of("key",rs.getString("storage_key"),"type",rs.getString("content_type"),"name",rs.getString("original_filename")),mediaId,invitationId,UUID.fromString(auth.getName()));
  if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
  return stream(rows.get(0));
 }
 @PatchMapping("/{mediaId}/visibility")
 public ApiResponse<?> visibility(@PathVariable UUID invitationId,@PathVariable UUID mediaId,@RequestBody Map<String,String> body,Authentication auth){
  String visibility=body.get("visibility");if(!java.util.Set.of("PRIVATE","PUBLIC").contains(visibility))throw new IllegalArgumentException("Visibility must be PRIVATE or PUBLIC");
  int n=jdbc.update("UPDATE customer_media SET visibility=? WHERE id=? AND invitation_id=? AND owner_user_id=?",visibility,mediaId,invitationId,UUID.fromString(auth.getName()));
  if(n==0)throw new IllegalArgumentException("Media not found");return ApiResponse.success(Map.of("id",mediaId,"visibility",visibility));
 }
 private ResponseEntity<StreamingResponseBody> stream(Map<String,Object> row){
  StreamingResponseBody body=out->{try(var in=storage.download((String)row.get("key"))){in.transferTo(out);}};
  return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)row.get("type")))
   .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+((String)row.get("name")).replaceAll("[\\r\\n\\"]","_")+"\"")
   .header("X-Content-Type-Options","nosniff").body(body);
 }
}
