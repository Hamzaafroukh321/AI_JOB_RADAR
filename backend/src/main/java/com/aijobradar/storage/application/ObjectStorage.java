package com.aijobradar.storage.application;

public interface ObjectStorage {
  boolean isAvailable();

  void put(String key, byte[] content, String contentType);

  byte[] get(String key);
}
