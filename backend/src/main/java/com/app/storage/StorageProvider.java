package com.app.storage;
import java.io.InputStream;
public interface StorageProvider {
 String name();
 StoredObject upload(String objectName,String contentType,long size,InputStream data);
 InputStream download(String storageKey);
 void delete(String storageKey);
}
