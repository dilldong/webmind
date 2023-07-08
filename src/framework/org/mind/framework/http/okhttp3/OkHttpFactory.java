package org.mind.framework.http.okhttp3;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.Dispatcher;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.GzipSource;
import okio.Okio;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.RequestException;
import org.mind.framework.http.NoContentResponse;
import org.mind.framework.server.GracefulShutdown;
import org.mind.framework.server.ShutDownSignalEnum;
import org.mind.framework.server.WebServerConfig;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.JsonUtils;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author dp
 * @version 1.0
 * @date 2021-08-24
 */
@Slf4j
public class OkHttpFactory {
    private static final String CONTENT_ENCODING = "Content-Encoding";

    private static final OkHttpClient HTTP_CLIENT;
    private static final ThreadLocal<Integer> CONTENT_LENGTH_LOCAL = new ThreadLocal<>();
    private static final Converter.Factory GSON_CONVERTER_FACTORY = GsonConverterFactory.create(JsonUtils.getSingleton());

    // json media type
    public static final MediaType JSON_MEDIA = MediaType.parse("application/json;charset=UTF-8");

    /**
     * Copied from {@link ConnectionSpec.APPROVED_CIPHER_SUITES}.
     */
    @SuppressWarnings("JavadocReference")
    private static final CipherSuite[] DEFAULT_CIPHER_SUITES =
            new CipherSuite[]{
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,

                    // Note that the following cipher suites are all on HTTP/2's bad cipher suites list.
                    // We'll
                    // continue to include them until better suites are commonly available. For example,
                    // none
                    // of the better cipher suites listed above shipped with Android 4.4 or Java 7.
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                    CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,

                    // Additional CipherSuites
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                    CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
                    CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256
            };

    private static final ConnectionSpec DEFAULT_CIPHER_SUITE_SPEC =
            new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .cipherSuites(DEFAULT_CIPHER_SUITES)
                    .build();

    /**
     * The list of {@link ConnectionSpec} instances used by the connection.
     */
    private static final List<ConnectionSpec> CONNECTION_SPEC_LIST =
            Arrays.asList(DEFAULT_CIPHER_SUITE_SPEC, ConnectionSpec.CLEARTEXT);

    static {
        log.info("Init OkHttpClient ....");

        WebServerConfig config = WebServerConfig.INSTANCE;
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(config.getMaxRequestsPerHost());
        dispatcher.setMaxRequests(config.getMaxRequests());

        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectionSpecs(CONNECTION_SPEC_LIST)
                        .dispatcher(dispatcher)
//                        .followSslRedirects(false)
//                        .followRedirects(false)
                        .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                        .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                        .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                        .pingInterval(config.getPingInterval(), TimeUnit.SECONDS);// websocket自动发送 ping 帧，直到连接失败或关闭

        if (log.isDebugEnabled()) {
            builder.addInterceptor(
                    new HttpLoggingInterceptor(log::debug)
                            .setLevel(HttpLoggingInterceptor.Level.BODY));
        }

        HTTP_CLIENT = builder.build();
        gracefulShutdown();
    }

    public static <V> V createService(Class<V> serviceClass, HttpOption option) {
        AbstractRequestInterceptor interceptor = null;
        if (option.isSignature()) {
            interceptor = option.getInterceptor();
            if (Objects.nonNull(interceptor))
                interceptor.setOption(option);
        }

        return createService(serviceClass, option, interceptor);
    }

    public static <V> V createService(Class<V> serviceClass, HttpOption option, Interceptor newInterceptor) {
        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
                .baseUrl(option.getRestHost())
                .addConverterFactory(GSON_CONVERTER_FACTORY);

        if (Objects.isNull(newInterceptor)) {
            retrofitBuilder.client(HTTP_CLIENT);
        } else {
            OkHttpClient adaptedClient = HTTP_CLIENT.newBuilder()
                    .addInterceptor(newInterceptor)
                    .build();
            retrofitBuilder.client(adaptedClient);
        }

        return retrofitBuilder.build().create(serviceClass);
    }

    /**
     * Execute a REST call and block until the response is received.
     */
    public static <V> V execute(Call<V> call) {
        try {
            Response<V> response = call.execute();
            if (response.isSuccessful())
                return response.body();

            throw new RequestException(getError(response));
        } catch (IOException e) {
            throw new RequestException(e);
        }
    }

    /**
     * Extracts and converts the response error body into an object.
     */
    public static RequestError getError(Response<?> response) throws IOException {
        ResponseBody errBody = response.errorBody();
        if (Objects.isNull(errBody))
            return RequestError.newInstance(response.code(), response.message());

        return RequestError.newInstance(response.code(), errBody.string());
    }

    /**
     * Returns the shared OkHttpClient instance.
     */
    public static OkHttpClient client() {
        return HTTP_CLIENT;
    }

    /**
     * Execute Http request and return a NoContentResponse
     */
    public static NoContentResponse requestNobody(Request request) throws IOException {
        try (okhttp3.Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            return new NoContentResponse(
                    response.headers(),
                    response.code(),
                    Objects.isNull(body) ? null : body.contentType(),
                    Objects.isNull(body) ? 0L : body.contentLength());
        }
    }

    /**
     * Execute Http request and return a String
     */
    public static String requestString(Request request) throws IOException {
        return requestString(request, null);
    }

    public static String requestString(Request request, Consumer<Headers> processHeaders) throws IOException {
        InputStream in = requestStream(request, processHeaders);
        if (Objects.isNull(in))
            return StringUtils.EMPTY;

        try {
            return Okio.buffer(Okio.source(in)).readUtf8();
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    /**
     * Execute Http request and return a InputStream
     */
    public static InputStream requestStream(Request request) throws IOException {
        return requestStream(request, null);
    }

    public static InputStream requestStream(Request request, Consumer<Headers> processHeaders) throws IOException {
        try (okhttp3.Response response = client().newCall(request).execute()) {
            if (Objects.nonNull(processHeaders))
                processHeaders.accept(response.headers());
            ResponseBody responseBody = response.body();

            if (response.isSuccessful()) {
                return Objects.isNull(responseBody) ?
                        null :
                        buildInputStream(response.headers(), responseBody);
            }

            String message = Objects.isNull(responseBody) ?
                    "N/A" :
                    buildResponseString(response.headers(), responseBody);
            throw new RequestException(
                    "Invalid response received: " + response.code() + "; " + message,
                    RequestError.newInstance(response.code(), message));
        }
    }

    /**
     * Execute Http request and return a json serialized object
     */
    public static <T> T request(Request request, Class<T> clazz) throws IOException {
        return request(request, TypeToken.get(clazz));
    }

    /**
     * Execute Http request and return a json serialized object
     */
    public static <T> T request(Request request, TypeToken<T> typeReference) throws IOException {
        InputStream in = requestStream(request);
        if (Objects.isNull(in))
            return null;

        try (InputStreamReader reader = new InputStreamReader(in)) {
            return JsonUtils.fromJson(reader, typeReference);
        }
    }

    private static InputStream buildInputStream(Headers responseHeaders, ResponseBody responseBody) throws IOException {
        ByteArrayInputStream in;
        if (HttpUtils.GZIP.equals(responseHeaders.get(CONTENT_ENCODING))) {
            in = new ByteArrayInputStream(
                    Okio.buffer(new GzipSource(
                                    responseBody.source()))
                            .readByteArray());
            // The following methods are also available
            // return new GZIPInputStream(responseBody.byteStream());
        } else
            in = new ByteArrayInputStream(responseBody.bytes());

        // Set return content length
        CONTENT_LENGTH_LOCAL.remove();
        CONTENT_LENGTH_LOCAL.set(in.available());

        return in;
    }

    private static String buildResponseString(Headers responseHeaders, ResponseBody responseBody) throws IOException {
        if (HttpUtils.GZIP.equals(responseHeaders.get(CONTENT_ENCODING)))
            return Okio.buffer(new GzipSource(responseBody.source())).readUtf8();

        return responseBody.string();
    }

    /**
     * Close OkHttpClient gracefully
     */
    private static void gracefulShutdown() {
        GracefulShutdown shutdown =
                new GracefulShutdown(
                        "OkHttp-Graceful",
                        Thread.currentThread(),
                        HTTP_CLIENT.dispatcher().executorService())
                        .waitTime(20L, TimeUnit.SECONDS);

        shutdown.registerShutdownHook(signal -> {
            if (signal == ShutDownSignalEnum.IN) {
                log.info("Close OkHttpClient ....");
            } else if (signal == ShutDownSignalEnum.OUT) {
                log.info("Clear OkHttpClient connections ....");
                // 清理连接池中的所有连接
                HTTP_CLIENT.connectionPool().evictAll();

                // 取消调度器中的所有请求
                HTTP_CLIENT.dispatcher().cancelAll();

                CONTENT_LENGTH_LOCAL.remove();
            }
        });
    }
}
