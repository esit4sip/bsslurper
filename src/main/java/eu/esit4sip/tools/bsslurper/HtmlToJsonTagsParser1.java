package eu.esit4sip.tools.bsslurper;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.net.ssl.SSLContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

/*
 * A class that parses html pages to json format using Jsoup and 
 * Jackson API.
 * 
 * */

public class HtmlToJsonTagsParser1 {

	/* Path of XWiki main tags page */
	public static final String path = "Main/Tags";
	public static final String schemeANDhost = "https://wiki.esit4sip.eu";

	/* html element ids/classes for parsing */
	public static final String queryElement_tags_content = "xwikicontent";
	public static final String queryElement_tags_pages = "xitemcontainer";
	public static final String queryElement_title = "wikiexternallink";
	public static final String queryElement_tags = "xdocTags";
	public static CloseableHttpClient httpclient;
	/* Store urls of tag pages */
	public static ArrayList<String> tagsUrl = new ArrayList<String>();
	/* Store urls of pages that host actual tags */
	public static ArrayList<String> tagsPageUrl = new ArrayList<String>();
	/* Page urls for actual tags' pages - avoid duplicates */
	public static ArrayList<String> uniqueArray = new ArrayList<String>();
	/* Map to hold actual tags' pages content */
	public static Map<String, Object> map = new LinkedHashMap<>();
	public static Integer selector;

	/* Method to parse-store html main tags page to JAVA ArrayList */
	public static void parse_html_to_list() throws URISyntaxException, IOException, Exception {
		String tags_url = null;
		URIBuilder builder = new URIBuilder();
		builder.setScheme("https").setHost("wiki.esit4sip.eu").setPath("/bin/view/" + path);
		URI uri = builder.build();
		HttpGet httpget = new HttpGet(uri);
		httpget.addHeader(HttpHeaders.ACCEPT, "application/xml");

		ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

			@Override
			public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
				int status = response.getStatusLine().getStatusCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					return entity != null ? EntityUtils.toString(entity) : null;
				} else {
					throw new ClientProtocolException("Unexpected response status: " + status);
				}
			}

		};

		String responseBody = httpclient.execute(httpget, responseHandler);
		Document document = Jsoup.parse(new String(responseBody));
		Element el_tags_content = document.getElementById(queryElement_tags_content);
		Elements el_tag_url = el_tags_content.select("a[href]");

		for (Element link : el_tag_url) {

			tags_url = link.attr("href");
			tagsUrl.add(schemeANDhost + tags_url);

		}
	}
	
	
	
	
	
	

	/*
	 * *Method that: a) Jsoup parsing each tag page and stores them to JAVA
	 * ArrayList and b) Jsoup parsing actual tags' pages and stores them to a json file
	 * 
	 */
	public static void jsoupParsing(String response, Integer parserSelector) {
		Document document;
		List<String> list = new ArrayList<String>();
		ObjectWriter om = new ObjectMapper().writer().with(SerializationFeature.INDENT_OUTPUT)
				.withDefaultPrettyPrinter();


		/* Tags' pages parsing and storing */
		if (parserSelector == 0) {

			document = Jsoup.parse(new String(response));
			Elements el_tags_pages = document.getElementsByClass(queryElement_tags_pages);

			Elements el_tag_url = el_tags_pages.select("a[href]");
			for (Element link : el_tag_url) {

				String tags_url = link.attr("href");
				tagsPageUrl.add(schemeANDhost + tags_url);

			}

		}
		/*
		 * Actual tags' pages content parsing and storing: Parses and stores the
		 * following: a. Page name b. Page url c. Page title (retains html
		 * format) d. Tags' names
		 */
		if (parserSelector == 1) {

			document = Jsoup.parse(new String(response));

			String page_name = document.title().replace(".WebHome", "").replace("- XWiki", "");
			

			map.put("Page name", page_name);

			Element pages_url = document.select("link[rel=canonical]").first();

			String page_url = pages_url.attr("href");

			map.put("URL", schemeANDhost + page_url);
			Elements content_element_title = document.getElementsByClass(queryElement_title);

			String page_title = content_element_title.outerHtml();

			map.put("Page title", page_title);

			Element content_element_tags = document.getElementById(queryElement_tags);

			String tag_names = content_element_tags.text().replaceFirst("Tags:", "");

			list.add(tag_names);

			map.put("Tag name", list);
		    

			try {
				
				/*Writes the Map content to a json file */

				om.writeValue(new FileOutputStream(Util.getOutputFile("tags-out.json"), true), map);
				System.out.println("tags-out.json" + " - was successfully written");

			} catch (JsonParseException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (JsonMappingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				// e1.printStackTrace();
			}

			changeFileFormat(Util.getOutputFile("tags-out.json"));
		}
	}

	/*
	 * An extension of Thread class that performs httpget method for multiple
	 * urls
	 */
	static class GetThread extends Thread {

		private final HttpContext context;
		private final HttpGet httpget;
		private final int id;

		public GetThread(CloseableHttpClient httpClient, HttpGet httpget, int id) {

			this.context = new BasicHttpContext();
			this.httpget = httpget;
			this.id = id;

		}

		/*
		 * Executes the GetMethod and calls the appropriate parsing method for
		 * each of two cases
		 */

		@Override
		public void run() {

			String responseBody = null;

			try {
				httpget.getURI();
				httpget.addHeader(HttpHeaders.ACCEPT, "application/xml");
				System.err.println("Getting: " + httpget.getURI());

				ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

					@Override
					public String handleResponse(final HttpResponse response)
							throws ClientProtocolException, IOException {
						int status = response.getStatusLine().getStatusCode();
						if (status >= 200 && status < 300) {
							HttpEntity entity = response.getEntity();
							return entity != null ? EntityUtils.toString(entity) : null;
						} else {
							throw new ClientProtocolException("Unexpected response status: " + status);
						}
					}

				};

				responseBody = httpclient.execute(httpget, responseHandler, context);
				String u = httpget.getURI().getPath();
				System.out.println("Path " + u);
				u = u.replace("^.*/([^/]+)/([^/]+)$","\\1_\\2.html");
				System.out.println("Saving " + u);
				Writer out = new OutputStreamWriter(new FileOutputStream(Util.getOutputFile(u)));
				out.write(responseBody);
				out.flush(); out.close();

				if (getSelector(selector) == 0) {
					jsoupParsing(responseBody, 0);
				} else {

					jsoupParsing(responseBody, 1);

				}

			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {

				httpget.releaseConnection();
			}

		}

	}

	/* Initiates multi-threading functionality */

	public static void initiateThreading(ArrayList<String> alist) throws InterruptedException {
		/* Create a thread for each supplied URI */
		GetThread[] threads = new GetThread[alist.size()];
		for (int i = 0; i < threads.length; i++) {
			HttpGet httpget = new HttpGet(alist.get(i));
			threads[i] = new GetThread(httpclient, httpget, i + 1);
		}

		/* Start the threads */
		for (int j = 0; j < threads.length; j++) {
			threads[j].start();
		}

		/* Join the threads */
		for (int j = 0; j < threads.length; j++) {
			threads[j].join();
		}
	}


	/**/

	public static void changeFileFormat(File file) {

		FileReader fr = null;
		try {
			fr = new FileReader(file);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		String line = "";
		String oldFileText = "";
		String replace = null;
		String search_1 = "\\}";
		String newLine = "\r\n";
		String search_2 = "\\{";
		String replacement = ",";
		try {
			BufferedReader br = new BufferedReader(fr);

			while ((line = br.readLine()) != null) {
				oldFileText += line + "\r\n";

			}
			fr.close();
		} catch (Exception e) {
			System.out.println("Problem reading file.");
		}

		replace = oldFileText.replaceAll(search_1 + newLine + search_2, replacement);

		FileOutputStream fileOut = null;
		try {
			fileOut = new FileOutputStream(Util.getOutputFile("tags-out.json"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			fileOut.write(replace.getBytes());

			fileOut.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	
	/* Deletes each time the json file prior its re-creation */

	public static Boolean deleteFile(String fileName) {

		try {

			File file = Util.getOutputFile(fileName);

			if (file.delete())
				return true;

		} catch (Exception e) {

			e.printStackTrace();

		}
		return false;
	}

	/*
	 * Stores in a JAVA ArrayList unique tag pages urls - prevents duplicate
	 * values
	 */

	public static ArrayList<String> preventDuplicates(ArrayList<String> alist) {
		ArrayList<String> result = new ArrayList<>();

		HashSet<String> hset = new HashSet<>();

		for (String item : alist) {

			if (!hset.contains(item)) {
				result.add(item);
				hset.add(item);
			}
		}

		return result;
	}

	/* Getters and Setters methods */

	public static void setSelector(Integer sel) {
		selector = sel;
	}

	public static Integer getSelector(Integer sel) {
		return sel;
	}

	public static ArrayList<String> getArray(ArrayList<String> array) {
		return array;
	}

	public static void setArray(ArrayList<String> array) {
		uniqueArray = array;
	}

	public final static void main(String[] args) throws Exception {

		deleteFile("tags-out.json");

		setSelector(0);
		httpclient = Util.createTolerantHttpClient();

		try {
			/* no threading */
			parse_html_to_list();

			/* Multi-threading */

			initiateThreading(tagsUrl);

			ArrayList<String> uniqueList = preventDuplicates(tagsPageUrl);
			/*
			 * for (String element : uniqueList) { System.out.println(element);
			 * }
			 */

			setArray(uniqueList);
			setSelector(1);

			initiateThreading(uniqueList);
			
			

		} finally {
			httpclient.close();
		}

	}
}
