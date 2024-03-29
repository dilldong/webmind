package org.mind.framework.http;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.util.EntityUtils;
import org.mind.framework.exception.ThrowProvider;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Apache HttpClient request.
 *
 * @author dp
 */
public class DefaultHttpClientResponse<T> extends HttpResponse<T> {

    private final CloseableHttpResponse httpResponse;
    private final HttpEntity entity;

    public DefaultHttpClientResponse(CloseableHttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        super.responseCode = httpResponse.getStatusLine().getStatusCode();
        this.entity = httpResponse.getEntity();

        try {
            super.inStream = entity.getContent();
        } catch (IOException e) {
            ThrowProvider.doThrow(e);
        }
    }

    public Header[] getAllHeaders() {
        return this.httpResponse.getAllHeaders();
    }

    @Override
    public String getHeader(String name) {
        if(StringUtils.isEmpty(name))
            return null;

        Header[] headers = this.httpResponse.getHeaders(name);
        return Objects.nonNull(headers) && headers.length > 0 ? headers[0].getValue() : null;
    }

    @Override
    public String asString(Charset charset) throws IOException {
        if (StringUtils.isNotEmpty(super.responseAsString))
            return super.responseAsString;

        super.responseAsString = EntityUtils.toString(entity, charset);
        super.streamConsumed = true;
        return this.responseAsString;
    }

    public CloseableHttpResponse getResponse() {
        return httpResponse;
    }

    @Override
    public void close() {
        HttpClientUtils.closeQuietly(getResponse());
        super.close();
    }
}
