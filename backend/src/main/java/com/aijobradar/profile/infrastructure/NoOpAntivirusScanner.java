package com.aijobradar.profile.infrastructure;

import com.aijobradar.profile.application.AntivirusScanner;
import org.springframework.stereotype.Component;

@Component
public class NoOpAntivirusScanner implements AntivirusScanner {
  @Override
  public ScanResult scan(byte[] content, String filename) {
    return ScanResult.NOT_CONFIGURED;
  }
}
