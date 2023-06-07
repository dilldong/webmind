package org.mind.framework.http.okhttp3;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.mind.framework.exception.RequestException;
import org.mind.framework.util.JsonUtils;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author dp
 * @version 1.0
 * @date 2021-08-24
 */
@Slf4j
public class OkHttpClientFactory {
    private static final OkHttpClient sharedClient;
    private static final Converter.Factory converterFactory = GsonConverterFactory.create(JsonUtils.getSingleton());

    private static final Converter<ResponseBody, RequestError> errorBodyConverter =
            (Converter<ResponseBody, RequestError>)
                    converterFactory.responseBodyConverter(RequestError.class, new Annotation[0], null);

    static {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(200);
        dispatcher.setMaxRequests(200);
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
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

        sharedClient = builder.build();
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
                .addConverterFactory(converterFactory);

        if (Objects.isNull(newInterceptor)) {
            retrofitBuilder.client(sharedClient);
        } else {
            OkHttpClient adaptedClient = sharedClient.newBuilder()
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
        return errorBodyConverter.convert(response.errorBody());
    }

    /**
     * Returns the shared OkHttpClient instance.
     */
    public static OkHttpClient client() {
        return sharedClient;
    }
}
