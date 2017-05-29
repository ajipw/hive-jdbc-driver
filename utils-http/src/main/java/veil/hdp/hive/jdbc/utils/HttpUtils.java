package veil.hdp.hive.jdbc.utils;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import veil.hdp.hive.jdbc.AuthenticationMode;
import veil.hdp.hive.jdbc.HiveDriverProperty;
import veil.hdp.hive.jdbc.HiveException;
import veil.hdp.hive.jdbc.HiveSQLException;
import veil.hdp.hive.jdbc.thrift.HiveThriftException;

import java.util.Properties;

public class HttpUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);

    /*
        todo: see if any of these optimizations can/should be applied

        From Thrift THttpClient

        * When using HttpClient, the following configuration leads to 5-15%
        * better performance than the HttpURLConnection implementation:
        *
        * http.protocol.version=HttpVersion.HTTP_1_1
        * http.protocol.content-charset=UTF-8
        * http.protocol.expect-continue=false
        * http.connection.stalecheck=false
     */

    public static CloseableHttpClient buildClient(Properties properties) throws HiveSQLException {

        AuthenticationMode authenticationMode = AuthenticationMode.valueOf(HiveDriverProperty.AUTHENTICATION_MODE.get(properties));

        HttpRequestInterceptor httpRequestInterceptor = null;

        try {
            switch (authenticationMode) {

                case NONE:
                    httpRequestInterceptor = buildBasicInterceptor(properties);
                    break;
                case KERBEROS:
                    httpRequestInterceptor = buildKerberosInterceptor(properties);
                    break;
            }
        } catch (HiveException e) {
            throw new HiveSQLException(e);
        }

        // todo: i'm really not sure what these values should be and they should probably be passed in configs
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);
        cm.setDefaultMaxPerRoute(20);

        HttpClientBuilder clientBuilder = HttpClients.custom();

        clientBuilder.setConnectionManager(cm);
        clientBuilder.addInterceptorFirst(httpRequestInterceptor);
        clientBuilder.addInterceptorLast(new XsrfRequestInterceptor());

        return clientBuilder.build();
    }

    private static HttpRequestInterceptor buildKerberosInterceptor(Properties properties) {

        boolean isCookieReplayEnabled = HiveDriverProperty.HTTP_COOKIE_REPLAY_ENABLED.getBoolean(properties);

        if (log.isDebugEnabled()) {
            log.debug("Cookie Replay is enabled!");
        }

        CookieStore cookieStore = null;

        if (isCookieReplayEnabled) {
            cookieStore = new BasicCookieStore();
        }

        return new KerberosRequestInterceptor(properties, cookieStore);
    }

    private static HttpRequestInterceptor buildBasicInterceptor(Properties properties) {
        return new BasicRequestInterceptor(properties);
    }

    public static TTransport createHttpTransport(Properties properties, CloseableHttpClient httpClient) {
        String host = HiveDriverProperty.HOST_NAME.get(properties);
        int port = HiveDriverProperty.PORT_NUMBER.getInt(properties);
        boolean sslEnabled = HiveDriverProperty.HTTP_SSL_ENABLED.getBoolean(properties);
        String endpoint = HiveDriverProperty.HTTP_ENDPOINT.get(properties);

        String scheme = sslEnabled ? "https" : "http";

        try {
            return new THttpClient(scheme + "://" + host + ':' + port + '/' + endpoint, httpClient);
        } catch (TTransportException e) {
            throw new HiveThriftException(e);
        }

    }
}
