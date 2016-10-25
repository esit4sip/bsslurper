package eu.esit4sip.tools.bsslurper;

import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntegratedTagAndPageFetcher {


    static HttpClient client = Util.createTolerantHttpClient();
    static String baseUrl = "https://wiki.esit4sip.eu";
    static Pattern internalImgPattern = Pattern.compile("<img src=\"([^\"]*/([^/\"]*))\""),
        internalLinkPattern = Pattern.compile("<a href=\"(" + baseUrl  + "/bin/view/[^\"]*)\"( |>)");

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

        byte[] byteBuff = new byte[10240];
        public void fetchFile(String filePath, String receivePath) throws IOException {
            System.out.println("Saving file " + filePath);
            HttpResponse response = client.execute(new HttpGet(baseUrl + filePath));
            InputStream inS = response.getEntity().getContent();
            String dir = receivePath.substring(0, receivePath.lastIndexOf('/'));
            System.out.println("Mkdir dir " + dir);
            new File(dir).mkdirs();
            FileOutputStream fOut = new FileOutputStream(receivePath);
            int r = 0;
            while((r=inS.read(byteBuff))>-1) {
                fOut.write(byteBuff, 0, r);
            }
            fOut.flush(); fOut.close();
        }
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
                    // first reformulate and fetch pictures
                    StringBuffer buff = new StringBuffer(body);
                    Matcher matcher = internalImgPattern.matcher(buff);
                    int off = 0;
                    while(matcher.find(off)) {
                        String fileName = matcher.group(2), filePath = matcher.group(1);
                        fileName = fileName.replaceAll("\\??width=[0-9]+\\&?","");
                        super.fetchFile(filePath, "out/pictures/" + fileName);
                        fileName = "data/pictures/" + fileName;
                        buff.replace(matcher.start(), matcher.end(), "<img src=\"" + fileName + "\" ");
                        off = matcher.start()+ fileName.length();
                    }

                    // then reformulate internal links
                    matcher = internalLinkPattern.matcher(buff);
                    System.out.println("Pattern: " + internalLinkPattern);
                    off = 0;
                    while(matcher.find(off)) {
                        System.out.println("Found href " + matcher.group());
                        buff.replace(matcher.start(), matcher.end(), "<a onclick=\"displayArticle('" +
                            Util.computePageFromUrl(matcher.group(1),baseUrl).replaceAll("__","_") + ".html'); return false;\"" + matcher.group(2));
                        off = matcher.start()+1;
                    }

                    body = buff.toString();

                    // simply store the page content
                    File file = Util.getOutputFile(
                            Util.computePageFromUrl(currentPath, baseUrl));
                    Writer out = new OutputStreamWriter( new FileOutputStream(file));
                    //out.write("----\ntitle: " + file.getName() + "\n----\n");
                    int len = body.length();

                    int l = body.indexOf("<h1 id=\"HLinks\"><span>Links</span></h1>");
                    if(l>-1) body = body.substring(0,l);
                    l = body.indexOf("<h1 id=\"HMetadata\"><span>Metadata</span></h1>");
                    if(l>-1) body = body.substring(0,l);
                    l = body.indexOf("<h1 id=\"HInformation\"><span>Information</span></h1>");
                    if(l>-1) body = body.substring(0,l);

                    out.write(body);
                    out.flush();
                    out.close();

                    // put the start of the content into the content, max 100 chars
                    // first remove the <h1> in there
                    int p = body.indexOf("</h1>");
                    if(p>-1) body = body.substring(p+"</h1>".length());
                    // then remove all tags
                    body = body.replaceAll("<[^>]*>","");
                    body = body.replaceAll("\r|\n|\t|&nbsp;"," ");
                    if(body.startsWith("Description:")) body = body.substring("Description:".length());
                    if(body.length()>100) {
                        // get last period
                        for(int i=0; i<100; ) {
                            p = i;
                            i = body.indexOf(".",i+1);
                        }
                        // include the period
                        p = p+1;
                        if(p>100 || p<50) p = 100;
                        body = body.substring(0,p);
                        if(p<100) body = body + "...";
                    }
                    pageContent.put(file.getName(), body);

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
                        if(title.endsWith(".WebHome")) title = title.substring(0, title.length()-".WebHome".length());
                        if(title.endsWith(".WebHome)")) title = title.substring(0, title.length()-".WebHome)".length());
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
    Map<String, String> pageTitles = new TreeMap<String, String>(), pageContent = new TreeMap<String,String>();



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

        Set<String> tagsSet = new TreeSet(tagsPages.keySet());
        for(String tag: tagsSet) {
            Set<String> newPages = new TreeSet<String>();
            for(String page: tagsPages.get(tag)) {
                newPages.add(Util.getOutputFile(Util.computePageFromUrl(page, baseUrl)).getName());
            }
            tagsPages.put(tag, newPages);
        }
        Map<String,String> pageTitles2 = new TreeMap<String,String>();
        for(Map.Entry<String,String>p: pageTitles.entrySet()) {
            pageTitles2.put(Util.getOutputFile(Util.computePageFromUrl(p.getKey(), baseUrl)).getName(), p.getValue());
        }


        System.out.println(" ================ tags =============== ");
        System.out.println(topicDivisions);
        System.out.println(" ================ tagsPages =============== ");
        System.out.println(tagsPages);
        System.out.println(" ================ pages =============== ");
        System.out.println(pageTitles2);

        // output the json objects (all-tags.json from topicDivisions, tagsPages.json, pages.json

        // JSON Tags for the web-site are like
        // [  {"groupname":"Domain", "tags": ["math":"Mathematics", ...]},
        //   ...]
        List<Map<String,Object>> tagsJson = new LinkedList<Map<String,Object>>();
        for(String groupName: topicDivisions.keySet()) {
            Map<String, Object> group = new HashMap<String,Object>();
            group.put("groupname", groupName);
            Map<String,String> tags = new TreeMap<String,String>();
            //List<String> tags = new LinkedList<String>();
            for(Map.Entry<String,String> entry: topicDivisions.get(groupName).entrySet()) {
                tags.put(entry.getKey(), entry.getValue());
                //tags.add(entry.getKey());
            }
            group.put("tags", tags);
            tagsJson.add(group);
        }
        writeMap("tags.json", tagsJson);

        // JSON articles.json sample:
        // [  {"name":"Article 1", "description":"Lorem", "url":"article1.html", "tags": ["mathematics", "Tag 12", "tablets"] }, ...]

        List<Map<String,Object>> articlesJSON = new LinkedList<Map<String,Object>> ();
        for(Map.Entry<String,String> pageAndTitle: pageTitles2.entrySet()) {
            // now fetch the tags
            Map<String, Object> article = new HashMap<String,Object>();
            String pageName = pageAndTitle.getKey();
            article.put("url", pageName);
            article.put("name", pageAndTitle.getValue());
            article.put("id", DigestUtils.md5Hex(pageName));
            List<String> tags = new LinkedList<String>();
            for(String tag: tagsPages.keySet()) {
                for(String p: tagsPages.get(tag)) {
                    if(pageName.equals(p)) tags.add(tag);
                }
            }
            article.put("tags", tags);
            article.put("description", pageContent.get(pageName));
            articlesJSON.add(article);
        }
        writeMap("articles.json", articlesJSON);
        //writeMap("pages.json", pageTitles2);
    }

    private void writeMap(String fileName, Object map) {
        try {
            File outputFile = Util.getOutputFile(fileName);
            System.out.println("Outputting: " + outputFile);
            Writer out = new OutputStreamWriter(new FileOutputStream(outputFile),"utf-8");
            JsonConfig config = new JsonConfig();
            // out.write("----\ntitle: " + fileName + "\n----\n");
            out.write(JSONSerializer.toJSON(map, config).toString());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    public static void main(String[] args) {
        new IntegratedTagAndPageFetcher().run();
    }


}
