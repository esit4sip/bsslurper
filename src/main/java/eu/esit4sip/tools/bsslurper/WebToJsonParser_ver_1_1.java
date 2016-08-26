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

/*Parsing html to json retaining html format (Tags and Main document parsing per page)*/

public class WebToJsonParser_ver_1_1 {

	/* Enter the respective path of a XWiki page */
	 public static String path ="ExperienceReports/Lyceum+of+Aradippou";

	/* html element id for XWiki main document in a page */
	public static String queryElement_content="xwikicontent";
	/* html element id for tags in a page */
	public static String queryElement_tags = "xdocTags";
	/* html element id for page title */
	public static String queryElement_title = "wikiexternallink";
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
	public static void xml_to_json(String file, String pageName,
			Elements elements_title, Element element_res) throws IOException {
		JsonFactory factory = new JsonFactory();
		JsonGenerator generator = factory.createGenerator(new File(file),
				JsonEncoding.UTF8);
		generator.writeStartObject();
		generator.writeStringField("Page", pageName);
		generator.writeStringField("Title", elements_title.outerHtml());
		generator.writeStringField("Result", element_res.outerHtml());
		generator.writeEndObject();
		generator.close();
		System.out.println(readFile(file));
	}

	/* Method to parse html fragments to json (retain html formatting) */
	public static void html_to_json() throws URISyntaxException, IOException,
			Exception {

		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost("wiki.esit4sip.eu").setPath("/bin/view/"+path);
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
		String pageName = document.title();
		Elements content_element_title = document
				.getElementsByClass(queryElement_title);
		Element content_element_content = document
				.getElementById(queryElement_content);
		Element content_element_tags = document
				.getElementById(queryElement_tags);
		

		/* Parse xml to json method call - main document parsing */
		System.out.println("Main document results:");
		xml_to_json("output.json", pageName, content_element_title,	content_element_content);
		/* Parse xml to json method call - tags parsing */
		System.out.println("Tags results:");
		xml_to_json("output.json", pageName, content_element_title,	content_element_tags);

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
