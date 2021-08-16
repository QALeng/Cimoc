package com.haleydu.cimoc.source;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.haleydu.cimoc.core.Manga;
import com.haleydu.cimoc.model.Chapter;
import com.haleydu.cimoc.model.Comic;
import com.haleydu.cimoc.model.ImageUrl;
import com.haleydu.cimoc.model.Source;
import com.haleydu.cimoc.parser.MangaParser;
import com.haleydu.cimoc.parser.NodeIterator;
import com.haleydu.cimoc.parser.SearchIterator;
import com.haleydu.cimoc.soup.Node;
import com.haleydu.cimoc.utils.StringUtils;


import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by QALeng on 2021/8/15.
 */

public class XManHua  extends MangaParser {
    public static final int TYPE = 103;
    public static final String DEFAULT_TITLE = "X漫画";
    public final String host="https://www.xmanhua.com/";
    public final String searchUrl = host+"search?title=%s";
//    public final HttpRequestHelper httpRequestHelper=new HttpRequestHelper();
    private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
//            .cookieJar(new CookieJar() {
//                @Override
//                public void saveFromResponse(HttpUrl httpUrl, List<Cookie> list) {
//                    cookieStore.put(httpUrl.host(), list);
//                }
//
//                @Override
//                public List<Cookie> loadForRequest(HttpUrl httpUrl) {
//                    List<Cookie> cookies = cookieStore.get(httpUrl.host());
//                    return cookies != null ? cookies : new ArrayList<Cookie>();
//                }
//            })
            .build();
//            .connectTimeout(1500, TimeUnit.MILLISECONDS)
//            .readTimeout(1500, TimeUnit.MILLISECONDS)
//            .build();

    public XManHua(Source source) {
        init(source, null);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true);
    }

    @Override
    public Request getSearchRequest(String keyword, int page) throws UnsupportedEncodingException {
        if (page != 1) return null;
        String url = StringUtils.format(searchUrl, keyword);
        return new Request.Builder()
                .url(url)
                .addHeader("user-agent","Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Mobile Safari/537.36")
                .build();
    }

    /**
     * 获取搜索结果迭代器，这里不直接解析成列表是为了多图源搜索时，不同图源漫画穿插的效果
     *
     * @param html 页面源代码
     * @param page 页码，可能对于一些判断有用
     */
    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        Node body = new Node(html);
        return new NodeIterator(body.list("body > div.manga-list > a")) {
            @Override
            protected Comic parse(Node node) {
                final String cid = node.attr("href");
                final String title = node.attr("title");
                final String cover = node.attr("a > img", "src");
                final String update = "";
                final String author = node.text("a > p:nth-child(3) ");
                return new Comic(TYPE, cid, title, cover, update, author);
            }
        };
    }

    @Override
    public String getUrl(String cid) {
        return host + cid;
    }


    @Override
    public Request getInfoRequest(String cid) {
        if (cid.indexOf(host) == -1) {
            cid = host.concat(cid);
        }
        return new Request.Builder().url(cid).build();
    }

    /**
     * 解析详情
     *
     * @param html  页面源代码
     * @param comic 漫画实体类，需要设置其中的字段
     */
    @Override
    public Comic parseInfo(String html, Comic comic) throws UnsupportedEncodingException {
        Node body = new Node(html);
        String title = body.text("body > div.detail-info-1 > div > div > p.detail-info-title");
        String cover = body.attr("body > div.detail-info-1 > div > div > img.detail-info-cover","src");
        String update = body.text("body > div.container > div.detail-list-form-title");
        String author = body.text("body > div.detail-info-1 > div > div > p.detail-info-tip > span:nth-child(1)");
        String intro = body.text("body > div.detail-info-2 > div > div > p");
        boolean status = false;
        comic.setInfo(title, cover, update, intro, author, status);
        return comic;
    }

    /**
     * 解析章节列表
     *
     * @param html 页面源代码
     */
    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic) {
        List<Chapter> list = new LinkedList<>();
        int i=0;
        for (Node node : new Node(html).list("#chapterlistload > a")) {
            String title = node.text();
            String path = node.attr("href");

            list.add(new Chapter(Long.parseLong(sourceComic + "000" + i++), sourceComic, title, path));
        }
        return list;
    }


    @Override
    public Request getImagesRequest(String cid, String path) {
        path = StringUtils.format(host+"%s",  path);
        return new Request.Builder().url(path).build();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) {
        List<ImageUrl> list = new ArrayList<>();
        try{
            Node htmlNode=new Node(html);
            List<Node> nodeList =htmlNode.list("body > div.reader-bottom > div > div.reader-bottom-page-list >a ");
            String imageHtmlUrl="https://xmanhua.com/"+nodeList.get(0).attr("href")+"chapterimage.ashx";

            Collection<String> imageUrlList=new ArrayList<>();

            String[] splitResult=html.split("head");
            String needStr=splitResult[1];
            // mid count cid
            String[] patterStrArr={"var\\s*COMIC_MID\\s*=\\s*([0-9]+)","var\\s*XMANHUA_IMAGE_COUNT\\s*=\\s*([0-9]+)","var\\s*XMANHUA_CID\\s*=\\s*([0-9]+)"};
            Pattern pattern;
            List<String> matchs=new ArrayList<>();
            for(String one:patterStrArr){
                pattern=Pattern.compile(one);
                final Matcher matcher = pattern.matcher(needStr);
                if(matcher.find()){
                     matchs.add(matcher.group(1));
                }
            }

            List<Integer> params=new ArrayList<>();
            for(int i=1;i<=Integer.parseInt(matchs.get(1));i=i+2){
                params.add(i);
//               if(i<2){
//                   i=3;
//               }else{
//                   i+=15;
//               }
            }

            // 获取image url

            TreeMap<Integer,String> imageUrlMap=new TreeMap<>();
            String imageUrlFormat= String.format("https://image.xmanhua.com/1/%s/%s/%%s.%%s", matchs.get(0),matchs.get(2));

            String imagePatternStr="([0-9]+)_([0-9]+)";
            Pattern imagePattern=Pattern.compile(imagePatternStr);

            for(Integer one:params){
//                stringMap.put("page",one.toString());
                Request request=new Request.Builder()
                        .url(String.format(imageHtmlUrl+"?cid=%s&_cid=%s&page=%s",matchs.get(2),matchs.get(2),one))
                        .addHeader("referer","https://xmanhua.com/"+nodeList.get(0).attr("href"))
                        .addHeader("user-agent","Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Mobile Safari/537.36")
                        .build();
                String evalStr=getResponseBody(okHttpClient,request);
                if(evalStr==null|| "".equals(evalStr)){
                    continue;
                }else{
                    String [] splitArr=evalStr.split("',[0-9]+,[0-9]+,'\\|\\|");
                    String[]  solveArr=splitArr[1].split("'\\.split")[0].split("\\|");
                    String imageType="jpg";
                    for(String oneSolve:solveArr){
                        Matcher matcher = imagePattern.matcher(oneSolve);
                        if(matcher.find()){
                            imageUrlMap.put(Integer.parseInt(matcher.group(1)),String.format(imageUrlFormat,matcher.group(),imageType));
                        }
                    }
                    for(Map.Entry<Integer,String> oneEntry:imageUrlMap.entrySet()){
                        System.out.println(oneEntry.getKey()+"----"+oneEntry.getValue());
                    }
                }
            }

            imageUrlList = imageUrlMap.values();

            Long comicChapter = chapter.getId();
            int i = 0;
            for(String imageUrl:imageUrlList){
                Long id = Long.parseLong(comicChapter + "000" + i);
                list.add(new ImageUrl(id, comicChapter, ++i, imageUrl, false));
            }
            return list;
        }catch (Exception e){
            return null;
        }
    }

    @Override
    public Request getCheckRequest(String cid) {
        return getInfoRequest(cid);
    }

    @Override
    public Headers getHeader() {
        return Headers.of("Referer", "http://m.517manhua.com/");
    }

    public static String getResponseBody(OkHttpClient client, Request request) throws Manga.NetworkErrorException {
        return getResponseBody(client, request, true);
    }

    private static String getResponseBody(OkHttpClient client, Request request, boolean retry) throws Manga.NetworkErrorException {
        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                byte[] bodybytes = response.body().bytes();
                String body = new String(bodybytes);
                Matcher m = Pattern.compile("charset=([\\w\\-]+)").matcher(body);
                if (m.find()) {
                    body = new String(bodybytes, m.group(1));
                }
                return body;
            } else if (retry)
                return getResponseBody(client, request, false);
        } catch (Exception e) {
            e.printStackTrace();
            if (retry)
                return getResponseBody(client, request, false);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        throw new Manga.NetworkErrorException();
    }


}
