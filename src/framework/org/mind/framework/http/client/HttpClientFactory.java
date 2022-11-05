package org.mind.framework.http.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.mind.framework.service.ExecutorFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
public class HttpClientFactory {
    private static final CloseableHttpClient httpClient;
    private static HttpClientBuilder httpClientBuilder;
    private static Thread shutdownThread;

    static {
        log.info("Init CloseableHttpClient ....");
        //注册访问协议相关的Socket工厂
        Registry<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", SSLConnectionSocketFactory.getSocketFactory())
                        .build();

        //HttpConnection 工厂:配置写请求/解析响应处理器
        HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connectionFactory =
                new ManagedHttpClientConnectionFactory(
                        DefaultHttpRequestWriterFactory.INSTANCE,
                        DefaultHttpResponseParserFactory.INSTANCE);

        //DNS 解析器
        DnsResolver dnsResolver = SystemDefaultDnsResolver.INSTANCE;

        PoolingHttpClientConnectionManager manager =
                new PoolingHttpClientConnectionManager(socketFactoryRegistry, connectionFactory, dnsResolver);

        //默认为Socket配置
        SocketConfig defaultSocketConfig = SocketConfig.custom().setTcpNoDelay(true).build();
        manager.setDefaultSocketConfig(defaultSocketConfig);

        //设置整个连接池的最大连接数
        manager.setMaxTotal(200);

        //每个路由的默认最大连接，每个路由实际最大连接数由DefaultMaxPerRoute控制，而MaxTotal是整个池子的最大数
        //设置过小无法支持大并发(ConnectionPoolTimeoutException) Timeout waiting for connection from pool
        manager.setDefaultMaxPerRoute(20);

        //在从连接池获取连接时，连接不活跃多长时间后需要进行一次验证，默认为2s
        manager.setValidateAfterInactivity(5_000);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(5_000)// 从连接池获取连接的等待超时时间
                .setConnectTimeout(15_000)// 等待数据超时时间
                .setSocketTimeout(15_000)// 连接超时时间
                .build();

        /*
         * 服务端支持TLS1.2+
         */
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.1,TLSv1.3");
        httpClientBuilder = HttpClients.custom()
                .useSystemProperties()
                .setConnectionManager(manager)
                .setConnectionManagerShared(true)//连接池不是共享模式
                .evictExpiredConnections()// 定期回收过期连接
                .evictIdleConnections(15, TimeUnit.SECONDS)//定期回收空闲连接,15s
                .setDefaultRequestConfig(requestConfig)
                .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE) //连接重用策略，即是否能keepAlive
                .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE) //长连接配置，即获取长连接生产多长时间
                .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false)); //设置重试次数，默认是3次，当前禁用掉（根据需要开启）

        httpClient = httpClientBuilder.build();

        // When the JVM stops or restarts, closing the connection-pool releases the connection
        shutdownThread = ExecutorFactory.newThread(() -> close());
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }

    public static CloseableHttpClient client() {
        return httpClient;
    }

    public static HttpClientBuilder newBuilder() {
        return httpClientBuilder;
    }

    private static void close() {
        log.info("Close HttpClient ....");
        HttpClientUtils.closeQuietly(httpClient);
        httpClientBuilder = null;

        if(Objects.nonNull(shutdownThread)){
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownThread);
            }catch (IllegalStateException e){}
        }
    }
}
