package com.app.storage;
public record StoredObject(String storageKey,String originalFilename,String contentType,long sizeBytes,String provider) {}
