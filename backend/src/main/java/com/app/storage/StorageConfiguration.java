package com.app.storage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class StorageConfiguration {
 @Bean
 @ConditionalOnMissingBean(StorageProvider.class)
 public StorageProvider unavailableStorageProvider(){
  return new StorageProvider(){
   public String name(){return "UNCONFIGURED";}
   public StoredObject upload(String n,String t,long s,java.io.InputStream i){throw new IllegalStateException("No storage provider is configured");}
   public java.io.InputStream download(String k){throw new IllegalStateException("No storage provider is configured");}
   public void delete(String k){throw new IllegalStateException("No storage provider is configured");}
  };
 }
}
