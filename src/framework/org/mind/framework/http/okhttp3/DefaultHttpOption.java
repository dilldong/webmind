package org.mind.framework.http.okhttp3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class DefaultHttpOption implements HttpOption {

    @Builder.Default
    private String restHost = StringUtils.EMPTY;

    @Builder.Default
    private String websocketHost = StringUtils.EMPTY;

    @Builder.Default
    private String apiKey = StringUtils.EMPTY;

    @Builder.Default
    private String secretKey = StringUtils.EMPTY;

    private boolean signature;

    @Builder.Default
    private boolean websocketAutoConnect = true;

    @Override
    public String getApiKey() {
        return this.apiKey;
    }

    @Override
    public String getSecretKey() {
        return this.secretKey;
    }

    @Override
    public String getRestHost() {
        return this.restHost;
    }

    @Override
    public String getWebSocketHost() {
        return this.websocketHost;
    }

    @Override
    public boolean isWebSocketAutoConnect() {
        return this.websocketAutoConnect;
    }

    @Override
    public boolean isSignature() {
        return signature;
    }
}
