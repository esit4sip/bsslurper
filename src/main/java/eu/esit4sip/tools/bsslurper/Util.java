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
import java.io.*;
import java.net.URLDecoder;
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

    public static File getOutputFile(String name) {
        File parent = null;
        try {
            parent = new File("out");
            if(!parent.isDirectory()) parent.mkdir();
            name = name.replaceAll("^_*","").replaceAll("_*$","");
            name = name.replaceAll("\\+", "-");
            name = URLDecoder.decode(name, "utf-8");
            name = name.replaceAll("__","_");
            if(!name.contains(".")) name = name + ".html";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return new File(parent, name);
    }


    /* Read a file and return a String method */
    public static String readFile(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }


    static String computePath(String url, String baseUrl) {
        if(url.startsWith(baseUrl))
            url = url.substring(baseUrl.length());
        if(url.startsWith("/bin/view"))
            url = url.substring("/bin/view".length());
        if(url.contains("?")) url = url.substring(0, url.indexOf("?"));
        return url;
    }

    static String computePageFromUrl(String url, String baseUrl) {
        String path = computePath(url, baseUrl);
        return path.replaceAll("/", "__");
    }


}
