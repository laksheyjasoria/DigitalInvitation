package com.app.storage;

import java.io.InputStream;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name="app.storage.fileserver.enabled",havingValue="true")
public class FileServerStorageProvider implements StorageProvider {
 private final RestClient client;private final String token,uploadPath,streamPath,deletePath;
 public FileServerStorageProvider(@Value("${app.storage.fileserver.base-url}")String baseUrl,
  @Value("${app.storage.fileserver.service-token}")String token,
  @Value("${app.storage.fileserver.upload-path:/master-drive/files}")String uploadPath,
  @Value("${app.storage.fileserver.stream-path:/master-drive/files/{id}/stream}")String streamPath,
  @Value("${app.storage.fileserver.delete-path:/master-drive/files/{id}}")String deletePath){
  this.token=token;this.uploadPath=uploadPath;this.streamPath=streamPath;this.deletePath=deletePath;
  this.client=RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization","Bearer "+token).build();
 }
 public String name(){return "FILESERVER";}
 public StoredObject upload(String objectName,String contentType,long size,InputStream data){
  throw new UnsupportedOperationException("Configure the FileServer multipart contract adapter for the deployed FileServer API before enabling uploads");
 }
 public InputStream download(String storageKey){
  return client.get().uri(streamPath,storageKey).accept(MediaType.APPLICATION_OCTET_STREAM).retrieve().body(InputStream.class);
 }
 public void delete(String storageKey){client.delete().uri(deletePath,storageKey).retrieve().toBodilessEntity();}
}
