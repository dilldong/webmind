package org.mind.framework.http;

import com.google.gson.reflect.TypeToken;
import org.mind.framework.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.zip.GZIPInputStream;

public class HttpResponse<T> {

    protected static final Logger log = LoggerFactory.getLogger(HttpResponse.class);

    protected int responseCode;
    protected String responseAsString = null;
    protected InputStream inStream;
    protected HttpURLConnection con;
    protected boolean streamConsumed = false;
    public static final String UTF8 = "UTF-8";
    public static final String GBK = "GBK";

    public HttpResponse() {

    }

    /**
     * Returns the response GZip stream.
     * @param con
     * @throws IOException
     */
    public HttpResponse(HttpURLConnection con) throws IOException {
        this.con = con;
        try {
            this.responseCode = con.getResponseCode();
        } catch (SocketTimeoutException e) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e1) {
            }

            this.responseCode = con.getResponseCode();
        }

        log.info("http response code: {}", this.responseCode);

        if ((inStream = con.getErrorStream()) == null)
            inStream = con.getInputStream();

        // the response is gzipped
        if (inStream != null && "gzip".equals(con.getContentEncoding()))
            inStream = new GZIPInputStream(inStream);
    }

    public String getResponseHeader(String name) {
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
        return this.asString(UTF8);
    }

    /**
     * Returns the response body as string.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as string @throws
     */
    public String asString(String charset) {
        if (this.responseAsString != null)
            return this.responseAsString;

        InputStream stream = asStream();
        if (stream == null)
            return null;

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset));
            StringBuffer buf = new StringBuffer();
            String line;

            while ((line = br.readLine()) != null)
                buf.append(line);

            this.responseAsString = buf.toString();

            stream.close();
            this.disconnect();
            streamConsumed = true;

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return responseAsString;
    }

    /**
     * Returns the response body as json.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as json @throws
     */
    public T asJson() {
        return this.asJson(UTF8);
    }

    /**
     * Returns the response body as json.<br>
     * Disconnects the internal HttpURLConnection silently. @return response
     * body as json @throws
     */
    public T asJson(String charset) {
        String result = this.asString(charset);

        return JsonUtils.fromJson(result, new TypeToken<T>() {
        });
    }

    public void disconnect() {
        if (con != null)
            con.disconnect();
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseAsString() {
        return responseAsString;
    }

}
