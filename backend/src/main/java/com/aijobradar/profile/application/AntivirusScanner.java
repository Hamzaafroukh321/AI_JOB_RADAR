package com.aijobradar.profile.application;

public interface AntivirusScanner {
  ScanResult scan(byte[] content, String filename);

  enum ScanResult {
    CLEAN,
    REJECTED,
    NOT_CONFIGURED
  }
}
