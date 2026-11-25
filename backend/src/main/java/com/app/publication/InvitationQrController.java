package com.app.publication;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/invitations")
public class InvitationQrController {
 private final JdbcTemplate jdbc;private final String baseUrl;
 public InvitationQrController(JdbcTemplate jdbc,@Value("${app.public-base-url}")String baseUrl){this.jdbc=jdbc;this.baseUrl=baseUrl;}
 @GetMapping(value="/{slug}/qr",produces=MediaType.IMAGE_PNG_VALUE)
 public ResponseEntity<byte[]> qr(@PathVariable String slug)throws Exception{
  var rows=jdbc.queryForList("SELECT public_slug FROM invitation_publication WHERE public_slug=? AND revoked_at IS NULL AND expires_at>now()",slug);
  if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,"Invitation expired or unavailable");
  String url=baseUrl.replaceAll("/+$","")+"/i/"+slug;
  BitMatrix matrix=new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,420,420,Map.of(EncodeHintType.MARGIN,2));
  BufferedImage image=new BufferedImage(420,420,BufferedImage.TYPE_INT_RGB);
  for(int y=0;y<420;y++)for(int x=0;x<420;x++)image.setRGB(x,y,matrix.get(x,y)?0x202020:0xFFFFFF);
  ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(out.toByteArray());
 }
}
