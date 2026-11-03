package com.scrumai.common.api;

public final class RequestId {
  public static final String HEADER = "X-Request-Id";
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private RequestId() {}

  public static void set(String requestId) {
    CURRENT.set(requestId);
  }

  public static String current() {
    String requestId = CURRENT.get();
    return requestId == null ? "unknown" : requestId;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
