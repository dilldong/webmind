package org.mind.framework.http;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * HttpClient response wrapper
 *
 * @param <T> result type
 * @author marcus
 */
public class HttpResponse<T> implements Closeable {
    protected static final Logger log = LoggerFactory.getLogger(HttpResponse.class);
    protected int responseCode;
    protected String responseAsString;
    protected transient InputStream inStream;
    protected transient HttpURLConnection con;
    protected boolean streamConsumed = false;

    public HttpResponse() {
    }

    /**
     * Returns the response GZip stream.
     *
     * @param con
     * @throws IOException
     */
    public HttpResponse(HttpURLConnection con) throws IOException {
        this.con = con;
        try {
            this.responseCode = con.getResponseCode();
        } catch (SocketTimeoutException e) {
            try {
                TimeUnit.MILLISECONDS.sleep(120L);
            } catch (InterruptedException ignored) {}

            Thread.yield();
            this.responseCode = con.getResponseCode();
        }

        if (log.isDebugEnabled())
            log.debug("Http response code: {}", this.responseCode);

        if (Objects.isNull((inStream = con.getErrorStream())))
            inStream = con.getInputStream();

        // the response is gzipped
        if (Objects.nonNull(inStream) && HttpUtils.GZIP.equals(con.getContentEncoding()))
            inStream = new GZIPInputStream(inStream);
    }

    public String getHeader(String name) {
        return Objects.isNull(this.con) || StringUtils.isEmpty(name) ? null : con.getHeaderField(name);
    }

    /**
     * Returns the response stream.<br>
     * This method cannot be called after calling asString()<br>
     * It is suggested to call close() after consuming the stream.
     * <p>
     * Disconnects the internal HttpURLConnection silently.
     *
     * @return response body stream
     * @see #close()
     */
    public InputStream asStream() {
        if (this.streamConsumed)
            throw new IllegalStateException("Stream has already been consumed.");

        return this.inStream;
    }

    public String asString() throws IOException {
        return this.asString(StandardCharsets.UTF_8);
    }

    public String asString(String charset) throws IOException {
        return this.asString(Charset.forName(charset));
    }

    /**
     * Returns the response body as string.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as string @throws
     */
    public String asString(Charset charset) throws IOException {
        if (StringUtils.isNotEmpty(this.responseAsString))
            return this.responseAsString;

        InputStream stream = asStream();
        if (Objects.isNull(stream))
            return null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))) {
            StringBuilder buf = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null)
                buf.append(line);

            this.responseAsString = buf.toString();
            streamConsumed = true;
        } finally {
            this.close();
        }

        return responseAsString;
    }

    public boolean isSuccessful() {
        return this.responseCode == HttpURLConnection.HTTP_OK;
    }

    /**
     * Returns the response body as json.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as json @throws
     */
    public T asJson() throws IOException {
        return this.asJson(StandardCharsets.UTF_8);
    }

    public T asJson(TypeToken<T> typeToken) throws IOException {
        return this.asJson(StandardCharsets.UTF_8, typeToken);
    }

    /**
     * Returns the response body as json.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as json @throws
     */
    public T asJson(String charset) throws IOException {
        return this.asJson(Charset.forName(charset));
    }

    public T asJson(Charset charset) throws IOException {
        return this.asJson(charset, new TypeToken<T>() {});
    }

    public T asJson(Charset charset, TypeToken<T> typeToken) throws IOException {
        if (this.streamConsumed && StringUtils.isNotEmpty(this.responseAsString))
            return JsonUtils.fromJson(responseAsString, typeToken);

        try (InputStreamReader reader = new InputStreamReader(this.inStream, charset)) {
            return JsonUtils.fromJson(reader, typeToken);
        }
    }

    @Override
    public void close() {
        if (Objects.nonNull(con)) {
            con.disconnect();
            con = null;
        }

        IOUtils.closeQuietly(inStream);
    }

    public int getStatusCode() {
        return responseCode;
    }

}
