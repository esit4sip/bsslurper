import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/*Parsing html to json (Tags parsing)- no html formatting*/

public class HtmlToJsonTagsParser_ver_1_0 {

	/* Enter the respective path of XWiki tags page */
	public static String path = "Main/Tags";

	/* html element id for tags page content */
	public static String queryElement_tags_content = "xwikicontent";
	public static CloseableHttpClient httpclient;

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

	/* A method to generate json from xml */
	public static void xml_to_json(String file, String tag_name, String tag_url)
			throws IOException {
		JsonFactory factory = new JsonFactory();
		JsonGenerator generator = factory.createGenerator(new File(file),
				JsonEncoding.UTF8);
		generator.writeStartObject();
		generator.writeStringField("Tag name", tag_name);
		generator.writeStringField("Tag URL", tag_url);
		generator.writeEndObject();
		generator.close();
		System.out.println(readFile(file));
	}

	/* Method to parse html fragments to json */
	public static void html_to_json() throws URISyntaxException, IOException,
			Exception {
		String tags_url = null;
		String tags_name = null;
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost("wiki.esit4sip.eu")
				.setPath("/bin/view/" + path);
		URI uri = builder.build();
		HttpGet httpget = new HttpGet(uri);
		httpget.addHeader(HttpHeaders.ACCEPT, "application/xml");

		ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

			@Override
			public String handleResponse(final HttpResponse response)
					throws ClientProtocolException, IOException {
				int status = response.getStatusLine().getStatusCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					return entity != null ? EntityUtils.toString(entity) : null;
				} else {
					throw new ClientProtocolException(
							"Unexpected response status: " + status);
				}
			}

		};

		String responseBody = httpclient.execute(httpget, responseHandler);
		Document document = Jsoup.parse(new String(responseBody));
		Element el_tags_content = document
				.getElementById(queryElement_tags_content);
		Elements el_tag_url = el_tags_content.select("a[href]");

		for (Element link : el_tag_url) {

			tags_name = link.text();
			tags_url = link.attr("href");

			/* Parse xml to json method call - tags parsing */
			xml_to_json("output.json", tags_name, "https://wiki.esit4sip.eu"
					+ tags_url);
		}
	}

	public final static void main(String[] args) throws Exception {

		/* Trust self-signed certificates */
		SSLContext sslcontext = SSLContexts.custom()
				.loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
		/* Allow TLSv1 protocol only */
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
				sslcontext, new String[] { "TLSv1" }, null,
				SSLConnectionSocketFactory.getDefaultHostnameVerifier());
		httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
		try {
			/* Call the main parsing method */
			html_to_json();

		} finally {
			httpclient.close();
		}

	}

}
