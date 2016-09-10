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
                    System.out.println("(" + getClass().getSimpleName() + ") Fetching " + currentPath);
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
                        if(text.equals("...")) continue;
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
                    // TODO: remove the part after <h1>Information</h1>, or Links or Metadata

                    Writer out = new OutputStreamWriter(
                            new FileOutputStream(
                                    Util.getOutputFile(
                                    Util.computePageFromUrl(currentPath, baseUrl))));
                    int len = body.length();
                    int l = Math.max(body.indexOf("<h1 id=\"HLinks\"><span>Links</span></h1>"), len),
                        m = Math.max(body.indexOf("<h1 id=\"HMetadata\"><span>Metadata</span></h1>"), len),
                        i = Math.max(body.indexOf("<h1 id=\"HInformation\"><span>Information</span></h1>"), len);
                    if(l<len || m<len || l<len)
                        body.substring(Math.min(l, Math.min(m,i)));

                    out.write(body);
                    out.flush();
                    out.close();
                } else {
                    // grasp the title
                    Document doc = Jsoup.parse(body);
                    Element elt = doc.getElementsByTag("title").first();
                    String name = Util.computePath(currentPath, baseUrl);
                    if(elt!=null) {
                        String title = elt.text();
                        if(title.endsWith(" - XWiki"))
                            title = title.substring(0, title.length()-" - XWiki".length());
                        title = title .replaceAll("\\([^)]*\\)", "");
                        title = title.trim();
                        pageTitles.put(name, title);
                    } else
                        pageTitles.put(name, "--missing-title--");
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
            f = new PagesFetcher("/bin/view" + pagePath);
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

        //writeMap("tags.json", topicDivisions);
        //writeMap("tagsPages.json", tagsPages);
        //writeMap("pages.json", pageTitles);
        //net.sf.json.JSONSerializer.toJSON(topicDivisions);
    }


    void writeMap(String name, Object obj) {
        try {
            JsonGenerator generator = new JsonFactory().createGenerator(
                    Util.getOutputFile(name), JsonEncoding.UTF8);
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
                generator.writeStartArray();
                for(Object o : coll) {
                    generator.writeArrayFieldStart("");
                    writeObject(generator, o);
                }
                generator.writeEndArray();
            } else if(obj instanceof String) {
                generator.writeString((String) obj);
            } else if (obj instanceof Integer) {
                generator.writeNumber((Integer) obj);
            } else if(obj==null) generator.writeNull();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        new IntegratedTagAndPageFetcher().run();
    }


}
