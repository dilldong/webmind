package org.mind.framework.http;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
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
public class HttpResponse<T> {
    protected static final Logger log = LoggerFactory.getLogger(HttpResponse.class);
    protected int responseCode;
    protected String responseAsString;
    protected InputStream inStream;
    protected HttpURLConnection con;
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
                TimeUnit.MILLISECONDS.sleep(100L);
            } catch (InterruptedException e1) {}

            this.responseCode = con.getResponseCode();
        }

        log.debug("http response code: {}", this.responseCode);

        if ((inStream = con.getErrorStream()) == null)
            inStream = con.getInputStream();

        // the response is gzipped
        if (inStream != null && "gzip".equals(con.getContentEncoding()))
            inStream = new GZIPInputStream(inStream);
    }

    public String getHeader(String name) {
        if (con != null)
            return con.getHeaderField(name);

        return null;
    }

    /**
     * Returns the response stream.<br>
     * This method cannot be called after calling asString()<br>
     * It is suggested to call disconnect() after consuming the stream.
     * <p>
     * Disconnects the internal HttpURLConnection silently.
     *
     * @return response body stream
     * @see #disconnect()
     */
    public InputStream asStream() {
        if (this.streamConsumed)
            throw new IllegalStateException("Stream has already been consumed.");

        return this.inStream;
    }

    public String asString() {
        return this.asString(StandardCharsets.UTF_8);
    }

    public String asString(String charset) {
        return this.asString(Charset.forName(charset));
    }

    /**
     * Returns the response body as string.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as string @throws
     */
    public String asString(Charset charset) {
        if (StringUtils.isNotEmpty(this.responseAsString))
            return this.responseAsString;

        try (InputStream stream = asStream()) {
            if (Objects.isNull(stream))
                return null;

            BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset));
            StringBuilder buf = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null)
                buf.append(line);

            this.responseAsString = buf.toString();
            streamConsumed = true;
            this.disconnect();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
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
    public T asJson() {
        return this.asJson(StandardCharsets.UTF_8);
    }

    public T asJson(TypeToken<T> typeToken) {
        return this.asJson(StandardCharsets.UTF_8, typeToken);
    }

    /**
     * Returns the response body as json.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as json @throws
     */
    public T asJson(String charset) {
        return this.asJson(Charset.forName(charset));
    }

    public T asJson(Charset charset) {
        return this.asJson(charset, new TypeToken<T>(){});
    }

    public T asJson(Charset charset, TypeToken<T> typeToken){
        String result = this.asString(charset);
        return JsonUtils.fromJson(result, typeToken);
    }

    public void disconnect() {
        if (con != null)
            con.disconnect();
    }

    public int getStatusCode() {
        return responseCode;
    }

}
