package org.mind.framework.http;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import okhttp3.Headers;
import okhttp3.MediaType;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/6/11
 */
@Getter
public class NoContentResponse extends HttpResponse<Void> {
    private final Headers headers;
    private final MediaType mediaType;
    private final long contentLength;

    public NoContentResponse(Headers headers, int statusCode, MediaType mediaType, long contentLength) {
        super.streamConsumed = true;
        super.responseCode = statusCode;

        this.headers = headers;
        this.mediaType = mediaType;
        this.contentLength = contentLength;
    }

    @Override
    public String getHeader(String name) {
        return Objects.isNull(this.headers) || StringUtils.isEmpty(name)? null : headers.get(name);
    }

    @Override
    public InputStream asStream() {
        throw new IllegalStateException("Cannot read raw response body of a converted body.");
    }

    @Override
    public String asString(Charset charset) {
        throw new IllegalStateException("Cannot read raw response body of a converted body.");
    }

    @Override
    public Void asJson(Charset charset, TypeToken<Void> typeToken) {
        throw new IllegalStateException("Cannot read raw response body of a converted body.");
    }
}
