package com.aijobradar.sources.application;

public class UnsafeSourceException extends RuntimeException {
  private final String category;

  public UnsafeSourceException(String category, String message) {
    super(message);
    this.category = category;
  }

  public String category() {
    return category;
  }
}
