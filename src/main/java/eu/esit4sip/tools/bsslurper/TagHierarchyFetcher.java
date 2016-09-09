package eu.esit4sip.tools.bsslurper;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that fetches from the navigation-keys page and outputs a structured list of tags.
 */
public class TagHierarchyFetcher {

    static final String tagsPagUrl = "https://wiki.esit4sip.eu/bin/view/BabySteps/NavigationTags/WebHome?xpage=plain";

    public static void main(String[] args) throws Throwable {

        HttpGet httpget = new HttpGet(tagsPagUrl);
        CloseableHttpClient httpclient = Util.createTolerantHttpClient();
        CloseableHttpResponse response = httpclient.execute(httpget);
        String tagPage = EntityUtils.toString(response.getEntity());

        Document doc = Jsoup.parse(tagPage);

        Element body = doc.getElementsByTag("body").get(0);
        Map<String, Map<String,String>> map = new HashMap<String, Map<String, String>>();
        Map<String,String> currentTerms = null;
        for(Element child :body.children()) {
            if("h2".equals(child.nodeName())) {
                currentTerms = new HashMap<String,String>();
                map.put(child.text(), currentTerms);
            } else if("ul".equals(child.nodeName()) && currentTerms!=null) {
                for(Element li: child.children()) {
                    String text = li.text();
                    int p = text.indexOf(":"), q = text.indexOf(")", p+1);
                    if(p==-1 || q == -1) {
                        System.err.println("Term badly formed: " + text);
                    } else {
                        currentTerms.put(text.substring(0,p).trim(),
                                text.substring(q+1).trim());
                    }
                }
            }
        }

        JsonGenerator generator = new JsonFactory().createGenerator(
                Util.getOutputFile("all-tags.json"), JsonEncoding.UTF8);
        generator.writeStartObject();
        for(Map.Entry<String,Map<String,String>> e : map.entrySet()) {
            generator.writeObjectFieldStart(e.getKey());
            for(Map.Entry<String,String> term: e.getValue().entrySet()) {
                generator.writeObjectField(term.getKey(), term.getValue());
            }
            generator.writeEndObject();
        }
        generator.writeEndObject();

        generator.close();

    }
}
