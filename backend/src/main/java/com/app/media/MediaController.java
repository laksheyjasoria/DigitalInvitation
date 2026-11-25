package com.app.media;

import com.app.common.api.ApiResponse;
import com.app.storage.StorageProvider;
import com.app.storage.StoredObject;
import java.io.ByteArrayInputStream;
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
 private final JdbcTemplate jdbc;private final StorageProvider storage;private final com.app.config.SystemSettingService settings;
 public MediaController(JdbcTemplate jdbc,StorageProvider storage,com.app.config.SystemSettingService settings){this.jdbc=jdbc;this.storage=storage;this.settings=settings;}
 @PostMapping public ApiResponse<?> upload(@PathVariable UUID invitationId,@RequestParam("file")MultipartFile file,Authentication auth){
  UUID owner=UUID.fromString(auth.getName());
  jdbc.queryForObject("SELECT id FROM invitation WHERE id=? AND owner_user_id=? AND status IN ('DRAFT','PENDING_PAYMENT')",UUID.class,invitationId,owner);
  long maxBytes=settings.getLong("invitation.media.max-file-bytes");
  long maxFiles=settings.getLong("invitation.media.max-files");
  if(file.isEmpty()||file.getSize()>maxBytes)throw new IllegalArgumentException("File is empty or exceeds the configured upload limit");
  Long count=jdbc.queryForObject("SELECT count(*) FROM customer_media WHERE invitation_id=? AND owner_user_id=?",Long.class,invitationId,owner);
  if(count!=null&&count>=maxFiles)throw new IllegalArgumentException("Invitation has reached its configured media limit");
  try{
   byte[] bytes=file.getBytes();String detected=detectType(bytes);
   if(detected==null)throw new IllegalArgumentException("Only valid JPEG, PNG, WebP and GIF image files are supported");
   String name=UUID.randomUUID()+"-"+(file.getOriginalFilename()==null?"upload":file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]","_"));
   StoredObject stored=storage.upload(name,detected,bytes.length,new ByteArrayInputStream(bytes));
   UUID id=UUID.randomUUID();
   try{
    jdbc.update("INSERT INTO customer_media(id,owner_user_id,invitation_id,storage_provider,storage_key,original_filename,content_type,size_bytes) VALUES (?,?,?,?,?,?,?,?)",id,owner,invitationId,stored.provider(),stored.storageKey(),file.getOriginalFilename()==null?"upload":file.getOriginalFilename(),detected,bytes.length);
   }catch(Exception dbError){try{storage.delete(stored.storageKey());}catch(Exception ignored){}throw dbError;}
   return ApiResponse.success(Map.of("id",id,"filename",file.getOriginalFilename()==null?"upload":file.getOriginalFilename(),"contentType",detected,"sizeBytes",bytes.length,"visibility","PRIVATE"));
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalStateException("Media upload failed; check storage provider connectivity and configuration",e);}
 }
 @GetMapping public ApiResponse<?> list(@PathVariable UUID invitationId,Authentication auth){return ApiResponse.success(jdbc.queryForList("SELECT id,original_filename,content_type,size_bytes,visibility,created_at FROM customer_media WHERE invitation_id=? AND owner_user_id=? ORDER BY created_at DESC",invitationId,UUID.fromString(auth.getName())));}
 @GetMapping("/{mediaId}/content")
 public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID invitationId,@PathVariable UUID mediaId,Authentication auth){
  var rows=jdbc.query("SELECT storage_key,content_type,original_filename FROM customer_media WHERE id=? AND invitation_id=? AND owner_user_id=?",(rs,n)->Map.of("key",rs.getString("storage_key"),"type",rs.getString("content_type"),"name",rs.getString("original_filename")),mediaId,invitationId,UUID.fromString(auth.getName()));
  if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);return stream(rows.get(0));
 }
 @PatchMapping("/{mediaId}/visibility")
 public ApiResponse<?> visibility(@PathVariable UUID invitationId,@PathVariable UUID mediaId,@RequestBody Map<String,String> body,Authentication auth){
  String visibility=body.get("visibility");if(!java.util.Set.of("PRIVATE","PUBLIC").contains(visibility))throw new IllegalArgumentException("Visibility must be PRIVATE or PUBLIC");
  int n=jdbc.update("UPDATE customer_media SET visibility=? WHERE id=? AND invitation_id=? AND owner_user_id=?",visibility,mediaId,invitationId,UUID.fromString(auth.getName()));
  if(n==0)throw new IllegalArgumentException("Media not found");return ApiResponse.success(Map.of("id",mediaId,"visibility",visibility));
 }
 private static String detectType(byte[] b){
  if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";
  if(b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "image/png";
  if(b.length>=6&&new String(b,0,6,java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a"))return "image/gif";
  if(b.length>=12&&new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
  return null;
 }
 private ResponseEntity<StreamingResponseBody> stream(Map<String,?> row){
  StreamingResponseBody body=out->{try(var in=storage.download((String)row.get("key"))){in.transferTo(out);}};
  return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)row.get("type")))
   .header(HttpHeaders.CONTENT_DISPOSITION,"inline")
   .header("X-Content-Type-Options","nosniff").body(body);
 }
}
