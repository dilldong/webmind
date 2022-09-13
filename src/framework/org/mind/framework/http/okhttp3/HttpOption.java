package org.mind.framework.http.okhttp3;

public interface HttpOption {

  String getApiKey();

  String getSecretKey();

  String getRestHost();

  String getWebSocketHost();

  boolean isWebSocketAutoConnect();

  boolean isSignature();

}
