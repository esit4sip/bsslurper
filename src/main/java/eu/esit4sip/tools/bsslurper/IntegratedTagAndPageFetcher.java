package eu.esit4sip.tools.bsslurper;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

public class IntegratedTagAndPageFetcher {


    HttpClient client = Util.createTolerantHttpClient();
    String baseUrl = "https://wiki.esit4sip.eu";

    abstract class Spider {
        Spider(List<String> paths) {
            this.paths = paths;
        }
        List<String> paths;
        String currentPath;


        public void run() {
            try {

                for(String path : paths) {
                    currentPath = baseUrl + path;
                    System.out.println("Fetching " + currentPath);
                    HttpResponse response = client.execute(new HttpGet(baseUrl + path));
                    processResponse(EntityUtils.toString(response.getEntity()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        abstract void processResponse(String body);
    }

    class TagsListFetcher extends Spider {
        TagsListFetcher(String tagsList) { super(Arrays.asList(tagsList));}
        @Override
        void processResponse(String body) {
            Document doc = Jsoup.parse(body);

            Map<String,String> currentTerms = null;
            for(Element child :doc.getElementsByTag("body").first().children()) {
                if("h2".equals(child.nodeName())) {
                    currentTerms = new HashMap<String,String>();
                    topicDivisions.put(child.text(), currentTerms);
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

        }
    }

    class TagPagesFetcher extends Spider {
        TagPagesFetcher(List<String> list) { super(list);}
        @Override
        void processResponse(String body) {
            String tag = currentPath.replaceAll(".*tag=(.*)","$1");
            Document document = Jsoup.parse(body);
            Element content = document.getElementsByClass("xcontent").first();
            Elements as = content.getElementsByTag("a");
            if(as!=null) for(Element a: as) {
                String url = baseUrl + a.attr("href");
                if(url.contains("/bin/export")) continue;
                String path = Util.computePath(url, baseUrl);
                // only accept experience reports
                if(!path.startsWith("/ExperienceReports") &&
                    !path.startsWith("/Scenarios/") &&
                    !path.startsWith("/Patterns/")) {
                    continue;
                }
                // do not accept parametrized docs
                if(path.contains("?"))
                    continue;
                Set<String> tagPages = tagsPages.get(tag);
                if(tagPages == null) {
                    tagPages = new TreeSet<String>();
                    tagsPages.put(tag, tagPages);
                }
                tagPages.add(path);
            }
        }
    }

    class PagesFetcher extends Spider {
        PagesFetcher(String url) { super(Arrays.asList(url, url + "?xpage=plain"));}
        @Override
        void processResponse(String body) {
            try {
                if(currentPath.endsWith("xpage=plain")) {
                    // simply store the page content
                    // TODO: remove the part after Info
                    Writer out = new OutputStreamWriter(
                            new FileOutputStream(Util.computePageFromUrl(currentPath, baseUrl)));
                    out.write(body);
                    out.flush();
                    out.close();
                } else {
                    // grasp the title
                    Document doc = Jsoup.parse(body);
                    Element elt = doc.getElementsByTag("title").first();
                    if(elt!=null)
                        pageTitles.put(currentPath, elt.text());
                    else
                        pageTitles.put(currentPath, "--missing-title--");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }




    // data structures

    Map<String, Set<String>> tagsPages = new TreeMap<String, Set<String>>();
    Map<String, Map<String,String>> topicDivisions = new TreeMap<String, Map<String, String>>();
    Map<String, String> pageTitles = new TreeMap<String, String>();



    public void run() {
        // first fetch the tags
        Spider f = new TagsListFetcher("/bin/view/BabySteps/NavigationTags/?xpage=plain");
        f.run();

        // then fetch the tags and the corresponding pages' URLs
        for(String headLine: topicDivisions.keySet()) {
            for(String tag: topicDivisions.get(headLine).keySet()) {
                f = new TagPagesFetcher(Arrays.asList("/bin/view/Main/Tags?do=viewTag&tag=" + tag));
                f.run();
            }
        }

        // collect all pages in a set
        Set<String> pages = new TreeSet<String>();
        for(String tag: tagsPages.keySet()) {
            for(String page: tagsPages.get(tag))
                pages.add(page);
        }

        // now fetch all pages (and their title)
        for(String pagePath: pages) {
            f = new PagesFetcher(pagePath);
            f.run();
        }

        System.out.println(" ================ tags =============== ");
        System.out.println(topicDivisions);
        System.out.println(" ================ tagsPages =============== ");
        System.out.println(tagsPages);
        System.out.println(" ================ pages =============== ");
        System.out.println(pageTitles);

        // TODO: replace tagsPages with mapped URLs (as file names)
        // TODO: output the json objects (all-tags.json from topicDivisions, tagsPages.json, pages.json
    }


    void writeMap(String name, Object obj) {
        try {
            JsonGenerator generator = new JsonFactory().createGenerator(
                    Util.getOutputFile("all-tags.json"), JsonEncoding.UTF8);
            writeObject(generator, obj);
            generator.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    void writeObject(JsonGenerator generator, Object obj) {
        try {
            if(obj instanceof Map) {
                Map m = (Map) obj;
                generator.writeStartObject();
                for(Object e : m.entrySet()) {
                    Map.Entry entry = (Map.Entry) e;
                    generator.writeObjectFieldStart((String) entry.getKey());
                    writeObject(generator, entry.getValue());
                    generator.writeEndObject();
                }
                generator.writeEndObject();
            }
            else if(obj instanceof Collection) {
                Collection coll = (Collection) obj;
                // TODO: complemeent
            }
            // TODO: also do the other types (string especially
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        new IntegratedTagAndPageFetcher().run();
    }


}
