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
        entity = httpResponse.getEntity();
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
        Header[] headers = this.httpResponse.getHeaders(name);
        return Objects.nonNull(headers) && headers.length > 0 ? headers[0].getValue() : StringUtils.EMPTY;
    }

    @Override
    public String asString(Charset charset) {
        if (StringUtils.isNotEmpty(this.responseAsString))
            return this.responseAsString;

        try {
            this.responseAsString = EntityUtils.toString(entity, charset);
            streamConsumed = true;
        } catch (IOException e) {
            ThrowProvider.doThrow(e);
        }
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
