package org.mind.framework.http.okhttp3;

import lombok.Getter;
import lombok.Setter;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.exception.RequestException;

import java.io.IOException;
import java.util.Objects;

/**
 * @version 1.0
 * @auther Marcus
 */
public abstract class AbstractRequestInterceptor implements Interceptor {
    @Setter
    @Getter
    private HttpOption option;

    public AbstractRequestInterceptor(){

    }

    public AbstractRequestInterceptor(HttpOption option) {
        this.option = option;
    }

    protected abstract void handle(Request original, Request.Builder newRequestBuilder) throws IOException;

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder newRequestBuilder = original.newBuilder();

        // handle business
        this.handle(original, newRequestBuilder);

        // add validation param
        // String query = original.url().query();
        // String body2String = this.body2String(original.body());

        // Build new request after adding the necessary authentication information
        return chain.proceed(newRequestBuilder.build());
    }

    /**
     * Extracts the request body into a String.
     *
     * @return request body as a string
     */
    protected String body2String(Request original) {
        RequestBody request = original.body();
        if (Objects.isNull(request))
            return StringUtils.EMPTY;

        try (final Buffer buffer = new Buffer()) {
            request.writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            throw new RequestException(e);
        }
    }

    protected String query2String(Request original) {
        return original.url().query();
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        AbstractRequestInterceptor that = (AbstractRequestInterceptor) o;
        return Objects.equals(option.getApiKey(), that.option.getApiKey()) &&
                Objects.equals(option.getSecretKey(), that.option.getSecretKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(option.getApiKey(), option.getSecretKey());
    }
}
