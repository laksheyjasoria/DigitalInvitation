package com.app.publication;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public/invitations")
public class InvitationQrController {
 private final JdbcTemplate jdbc;
 public InvitationQrController(JdbcTemplate jdbc){this.jdbc=jdbc;}

 @GetMapping(value="/{slug}/qr",produces=MediaType.IMAGE_PNG_VALUE)
 public ResponseEntity<byte[]> qr(@PathVariable("slug") String slug)throws Exception{
  var rows=jdbc.query(
   "SELECT p.published_snapshot->'content'->>'locationUrl' AS location_url " +
   "FROM invitation_publication p WHERE p.public_slug=? AND p.revoked_at IS NULL AND p.expires_at>now()",
   (rs,n)->rs.getString("location_url"),slug);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.GONE,"Invitation expired or unavailable");
  String locationUrl=rows.get(0);
  if(locationUrl==null||locationUrl.isBlank())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No event location QR is available");
  java.net.URI parsed;
  try{parsed=java.net.URI.create(locationUrl);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.GONE,"Event location URL is invalid");}
  if(!"https".equalsIgnoreCase(parsed.getScheme())||parsed.getHost()==null)
   throw new ResponseStatusException(HttpStatus.GONE,"Event location URL is invalid");
  byte[] png=createPng(locationUrl);
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noCache()).body(png);
 }

 static String dataUrl(String locationUrl)throws Exception{return "data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(createPng(locationUrl));}

 private static byte[] createPng(String locationUrl)throws Exception{
  BitMatrix matrix=new MultiFormatWriter().encode(locationUrl,BarcodeFormat.QR_CODE,420,420,Map.of(EncodeHintType.MARGIN,4));
  BufferedImage image=new BufferedImage(420,420,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<420;y++)for(int x=0;x<420;x++)image.setRGB(x,y,matrix.get(x,y)?0xFF202020:0x00000000);
  ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);
  return out.toByteArray();
 }
}
