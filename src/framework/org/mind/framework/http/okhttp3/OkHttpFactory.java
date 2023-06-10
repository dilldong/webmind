package org.mind.framework.http.okhttp3;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.Dispatcher;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.RequestException;
import org.mind.framework.server.GracefulShutdown;
import org.mind.framework.server.ShutDownSignalEnum;
import org.mind.framework.util.JsonUtils;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
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
    private static final OkHttpClient HTTP_CLIENT;
    private static final Converter.Factory GSON_CONVERTER_FACTORY = GsonConverterFactory.create(JsonUtils.getSingleton());

    private static final Converter<ResponseBody, RequestError> ERROR_BODY_CONVERTER =
            (Converter<ResponseBody, RequestError>)
                    GSON_CONVERTER_FACTORY.responseBodyConverter(RequestError.class, new Annotation[0], null);

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

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(200);
        dispatcher.setMaxRequests(200);
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectionSpecs(CONNECTION_SPEC_LIST)
                        .dispatcher(dispatcher)
//                        .followSslRedirects(false)
//                        .followRedirects(false)
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .pingInterval(20, TimeUnit.SECONDS);// websocket自动发送 ping 帧，直到连接失败或关闭

        if (log.isDebugEnabled()) {
            builder.addInterceptor(
                    new HttpLoggingInterceptor(log::debug)
                            .setLevel(HttpLoggingInterceptor.Level.BASIC));
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

            RequestError apiError = getError(response);
            throw new RequestException(apiError);
        } catch (IOException e) {
            throw new RequestException(e);
        }
    }

    /**
     * Extracts and converts the response error body into an object.
     */
    public static RequestError getError(Response<?> response) throws IOException {
        Objects.requireNonNull(response.errorBody());
        return ERROR_BODY_CONVERTER.convert(response.errorBody());
    }

    /**
     * Returns the shared OkHttpClient instance.
     */
    public static OkHttpClient client() {
        return HTTP_CLIENT;
    }

    public static String request(Request request) throws IOException {
        return request(request, header -> {});
    }

    public static String request(Request request, Consumer<Headers> processHeaders) throws IOException {
        try (okhttp3.Response response = client().newCall(request).execute()) {
            processHeaders.accept(response.headers());
            ResponseBody responseBody = response.body();
            if (response.isSuccessful())
                return Objects.isNull(responseBody)? StringUtils.EMPTY : responseBody.string();

            String text = Objects.isNull(responseBody)? "N/A" : responseBody.string();
            throw new RequestException("Invalid response received: " + response.code() + "; " + text);
        }
    }

    public static <T> T request(Request request, Class<T> clazz) throws IOException {
        return request(request, TypeToken.get(clazz));
    }

    public static <T> T request(Request request, TypeToken<T> typeRef) throws IOException {
        String json = request(request);
        if(StringUtils.isEmpty(json))
            return null;

        return JsonUtils.fromJson(json, typeRef);
    }

    private static void gracefulShutdown() {

        GracefulShutdown shutdown =
                new GracefulShutdown(
                        "OkHttp-Graceful",
                        Thread.currentThread(),
                        HTTP_CLIENT.dispatcher().executorService())
                .waitTime(20L, TimeUnit.SECONDS);

        shutdown.registerShutdownHook(signal -> {
            if(signal == ShutDownSignalEnum.IN) {
                log.info("Close OkHttpClient ....");
            } else if(signal == ShutDownSignalEnum.OUT){
                log.info("Clear OkHttpClient connections ....");
                // 清理连接池中的所有连接
                HTTP_CLIENT.connectionPool().evictAll();

                // 取消调度器中的所有请求
                HTTP_CLIENT.dispatcher().cancelAll();
            }
        });
    }
}
