package eu.esit4sip.tools.bsslurper;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

public class Util {

    public static CloseableHttpClient createTolerantHttpClient()  {
        try {
		/* Trust self-signed certificates */
            SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
		/* Allow TLSv1 protocol only */
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[] { "TLSv1" }, null,
                    SSLConnectionSocketFactory.getDefaultHostnameVerifier());
            Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create().register("https", sslsf)
                    .build();
		/*Register a pooling connection manager*/
            HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
            CloseableHttpClient httpclient = HttpClients.custom().setConnectionManager(cm).build();
            return httpclient;
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Can't build tolerant httpclient. ", e);
        }
    }

}
