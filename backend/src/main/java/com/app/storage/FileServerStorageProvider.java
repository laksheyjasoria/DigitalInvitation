package com.app.storage;

import java.io.InputStream;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.storage.fileserver.enabled",havingValue="true")
public class FileServerStorageProvider implements StorageProvider {
 private final RestClient client;private final String uploadPath,streamPath,deletePath;
 public FileServerStorageProvider(@Value("${app.storage.fileserver.base-url}")String baseUrl,
  @Value("${app.storage.fileserver.service-token}")String token,
  @Value("${app.storage.fileserver.upload-path:/master-drive/files}")String uploadPath,
  @Value("${app.storage.fileserver.stream-path:/master-drive/files/{id}/stream}")String streamPath,
  @Value("${app.storage.fileserver.delete-path:/master-drive/files/{id}}")String deletePath){
  if(baseUrl==null||baseUrl.isBlank()||token==null||token.isBlank())throw new IllegalStateException("FileServer storage is enabled but its base URL or service token is missing");
  this.uploadPath=uploadPath;this.streamPath=streamPath;this.deletePath=deletePath;
  this.client=RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization","Bearer "+token).build();
 }
 public String name(){return "FILESERVER";}
 public StoredObject upload(String objectName,String contentType,long size,InputStream data){
  InputStreamResource resource=new InputStreamResource(data){
   @Override public long contentLength(){return size;}
   @Override public String getFilename(){return objectName;}
  };
  LinkedMultiValueMap<String,Object> parts=new LinkedMultiValueMap<>();
  parts.add("file",resource);
  Map response=client.post().uri(uploadPath).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(Map.class);
  if(response==null)throw new IllegalStateException("FileServer returned an empty upload response");
  Object key=response.get("fileId");if(key==null)key=response.get("id");
  if(key==null)throw new IllegalStateException("FileServer response did not include fileId or id");
  return new StoredObject(String.valueOf(key),objectName,contentType,size,name());
 }
 public InputStream download(String storageKey){
  return client.get().uri(streamPath,storageKey).accept(MediaType.APPLICATION_OCTET_STREAM).retrieve().body(InputStream.class);
 }
 public void delete(String storageKey){client.delete().uri(deletePath,storageKey).retrieve().toBodilessEntity();}
}
