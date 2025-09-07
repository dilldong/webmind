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
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.RequestException;
import org.mind.framework.http.NoContentResponse;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.web.server.GracefulShutdown;
import org.mind.framework.web.server.ShutDownSignalStatus;
import org.mind.framework.web.server.WebServerConfig;
import org.springframework.http.HttpHeaders;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author dp
 * @version 1.0
 * @date 2021-08-24
 */
@Slf4j
public class OkHttpFactory {

    // json media type
    public static final MediaType JSON_MEDIA = MediaType.parse(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);

    private static final OkHttpClient HTTP_CLIENT;

    // 线程本地变量，用于存储响应内容长度
    private static final ThreadLocal<Integer> CONTENT_LENGTH_LOCAL = new ThreadLocal<>();

    /**
     * 默认支持的密码套件列表
     * Copied from {@link ConnectionSpec.APPROVED_CIPHER_SUITES}.
     */
    private static final CipherSuite[] DEFAULT_CIPHER_SUITES =
            new CipherSuite[]{
                    // 现代安全的密码套件
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,

                    // 为了兼容性保留的密码套件（不推荐用于生产环境）
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
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        ThreadPoolExecutor executorService =
                ExecutorFactory.newThreadPoolExecutor(
                        Math.max(availableProcessors, 8),
                        Math.min(config.getMaxRequests(), availableProcessors * 16),
                        30L,
                        TimeUnit.SECONDS,
                        new SynchronousQueue<>(),
                        ExecutorFactory.newThreadFactory("okhttp3-group", "okhttp3-exec-"),
                        new ThreadPoolExecutor.CallerRunsPolicy());

        // 允许核心线程超时回收
        executorService.allowCoreThreadTimeOut(true);

        // 预启动核心线程
        executorService.prestartAllCoreThreads();

        // 配置请求分发器
        Dispatcher dispatcher = new Dispatcher(executorService);
        dispatcher.setMaxRequestsPerHost(config.getMaxRequestsPerHost());
        dispatcher.setMaxRequests(config.getMaxRequests());

        // 构建OkHttpClient
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectionSpecs(CONNECTION_SPEC_LIST)
                        .dispatcher(dispatcher)
//                        .followSslRedirects(false)
//                        .followRedirects(false)
                        .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                        .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                        .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS);

        // 配置WebSocket ping间隔
        if (config.getPingInterval() > 0)
            builder.pingInterval(config.getPingInterval(), TimeUnit.SECONDS);// websocket自动发送 ping 帧，直到连接失败或关闭

        // 开发环境添加日志拦截器
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
                .addConverterFactory(ConverterFactory.GSON_CONVERTER);

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
        try (InputStream in = requestStream(request, processHeaders)) {
            if (Objects.isNull(in))
                return StringUtils.EMPTY;

            return Okio.buffer(Okio.source(in)).readUtf8();
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
                    "Invalid response: " + response.code() + "; " + message,
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
        try (InputStream in = requestStream(request)) {
            if (Objects.isNull(in))
                return null;

            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonUtils.fromJson(reader, typeReference);
            }
        }
    }

    /**
     * 获取当前响应的内容长度
     */
    public static Integer getCurrentContentLength() {
        return CONTENT_LENGTH_LOCAL.get();
    }

    /**
     * 清除当前线程的内容长度缓存
     */
    public static void clearContentLength() {
        CONTENT_LENGTH_LOCAL.remove();
    }

    private static InputStream buildInputStream(Headers responseHeaders, ResponseBody responseBody) throws IOException {
        byte[] bytes;

        if (HttpUtils.GZIP.equals(responseHeaders.get(HttpHeaders.CONTENT_ENCODING))) {
            try (GzipSource gzipSource = new GzipSource(responseBody.source())) {
                bytes = Okio.buffer(gzipSource).readByteArray();
            }
            // The following methods are also available
            // return new GZIPInputStream(responseBody.byteStream());
        } else
            bytes = responseBody.bytes();

        // Set return content length
        CONTENT_LENGTH_LOCAL.remove();
        CONTENT_LENGTH_LOCAL.set(bytes.length);

        return new ByteArrayInputStream(bytes);
    }

    private static String buildResponseString(Headers responseHeaders, ResponseBody responseBody) throws IOException {
        if (Objects.isNull(responseBody))
            return StringUtils.EMPTY;

        if (HttpUtils.GZIP.equals(responseHeaders.get(HttpHeaders.CONTENT_ENCODING))) {
            // 处理GZIP压缩的响应
            try (GzipSource gzipSource = new GzipSource(responseBody.source())) {
                return Okio.buffer(gzipSource).readUtf8();
            }
        }

        return responseBody.string();
    }

    /**
     * Close OkHttpClient gracefully
     */
    private static void gracefulShutdown() {
        GracefulShutdown shutdown =
                GracefulShutdown.newShutdown("OkHttp-Graceful", HTTP_CLIENT.dispatcher().executorService());

        shutdown.awaitTime(15L, TimeUnit.SECONDS)
                .registerShutdownHook(signal -> {
                    if (signal == ShutDownSignalStatus.IN) {
                        log.info("Cancel OkHttpClient connections ....");
                        HTTP_CLIENT.dispatcher().cancelAll();
                    } else if (signal == ShutDownSignalStatus.OUT)
                        CONTENT_LENGTH_LOCAL.remove();
                });
    }

    private static class ConverterFactory {
        private static final Converter.Factory GSON_CONVERTER = GsonConverterFactory.create(JsonUtils.getSingleton());
    }
}
