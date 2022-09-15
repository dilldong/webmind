package org.mind.framework.http.okhttp3;

import okhttp3.Interceptor;

public interface HttpOption {

  String getApiKey();

  String getSecretKey();

  String getRestHost();

  String getWebSocketHost();

  boolean isWebSocketAutoConnect();

  boolean isSignature();

  Interceptor getInterceptor();
}
