package com.stephentu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.stephentu.util.Function1;
import com.stephentu.util.Tuple2;

public class SimpleSidereel {

  static class LinkTask implements Runnable {
    private final List<String> linksToFollow;
    private final CountDownLatch latch;
    LinkTask(List<String> linksToFollow) {
      this(linksToFollow, null);
    }
    LinkTask(List<String> linksToFollow, CountDownLatch latch) {
      this.linksToFollow = linksToFollow;
      this.latch = latch;
    }
    private void doRun() throws IOException {
      for (String link : linksToFollow) {
        Document innerDoc; 
        if (link.startsWith("/"))
          innerDoc = Jsoup.connect("http://www.sidereel.com" + link).get();
        else if (link.startsWith("http"))
          innerDoc = Jsoup.connect(link).get();
        else {
          System.err.println("cannot handle link: " + link); 
          continue;
        }
        Element trueLink = innerDoc.select("div[class^=playground] strong a").first();
        if (trueLink != null) {
          String trueURL = trueLink.attr("href");
          System.out.println(trueURL);
        } else 
          System.out.println("Bad redirect: " + link);
      }
      if (latch != null)
        latch.countDown();
    }
    public void run() {
      try { doRun(); } 
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** 
   * does idx lie within [start, end]
   */
  private static <T> boolean checkBounds(List<T> l, int idx) {
    return idx >= 0 && idx <= l.size();
  }

  /**
   * Return a slice from [start, end) of orig
   */
  private static <T> List<T> sliceOf(List<T> orig, int start, int end) {
    if (!checkBounds(orig, start) || !checkBounds(orig, end))
      throw new IllegalArgumentException("bad index given");
    List<T> cpy = new ArrayList<T>();
    for (int idx = start; idx < end; idx++)
      cpy.add(orig.get(idx));
    return cpy;
  }
  
  private static void episodeURLDump(String showName, int season, int episode, boolean forceNoParallel) throws IOException, InterruptedException {
    System.out.println("----------------------------------");
    System.out.println("Show:    " + showName);
    System.out.println("Season:  " + season);
    System.out.println("Episode: " + episode);
    System.out.println("----------------------------------");
    
    Document doc = Jsoup.connect(String.format("http://www.sidereel.com/%s/season-%d/episode-%d/search", showName, season, episode)).get();
    
    List<Document> docsToCheck = new ArrayList<Document>();
    docsToCheck.add(doc);
    
    Elements pagination = doc.select("div[class=pagination]");
    if (!pagination.isEmpty()) {
      Elements pageNums = pagination.select("span:matchesOwn(\\d+),a:matchesOwn(\\d+)");
      if (!pageNums.isEmpty()) {
        int maxSoFar = 1;
        for (Element pgNElem : pageNums) {
          maxSoFar = Math.max(maxSoFar, Integer.parseInt(pgNElem.text()));
        }
        for (int i = 2; i <= maxSoFar; i++) {
          docsToCheck.add(Jsoup.connect(String.format("http://www.sidereel.com/%s/season-%d/episode-%d/search?page=%d", showName, season, episode, i)).get());
        }
      }
    }
    
    List<String> linksToFollow = new ArrayList<String>();
    for (Document docToCheck : docsToCheck) {
      Elements links = docToCheck.select("li[class^=link_] h2 a");
      for (Element elem : links) {
        linksToFollow.add(elem.attr("href"));
      }
    }
    if (linksToFollow.isEmpty())
      System.out.println("Sorry, could not find any links");
    else {
      int numProc = Runtime.getRuntime().availableProcessors();
      int numThreads = numProc * 2;
      if (!forceNoParallel && linksToFollow.size() >= numThreads) {
        // execute on numProc threads
        ExecutorService exec = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        int linksPerThread = linksToFollow.size() / numThreads;
        int lastIdx = 0;
        for (int i = 0; i < numThreads - 1; i++) {
          int endIdx = lastIdx + linksPerThread;
          exec.execute(new LinkTask(sliceOf(linksToFollow, lastIdx, endIdx), latch));
          lastIdx = endIdx;
        }
        exec.execute(new LinkTask(sliceOf(linksToFollow, lastIdx, linksToFollow.size()), latch));
        latch.await();
        exec.shutdown();
      } else
        new LinkTask(linksToFollow).run();
    }
  }
  
  private static final Pattern seasonUrlPattern = Pattern.compile("/\\w+/season-(\\d+)");
  private static final Pattern episodeUrlPattern = Pattern.compile("/\\w+/season-\\d+/episode-(\\d+)");
  
  private static SortedMap<Integer, SortedMap<Integer, Tuple2<Element, String>>> retrieveEpisodeSummaries(String showName, Function1<Integer, Boolean> seasonInclusionCriteria) throws IOException {
    
    Document doc = Jsoup.connect(String.format("http://www.sidereel.com/%s", showName)).get();

    Elements seasons = doc.select("div[class^=season-header] a[class^=expand-season-trigger]");
    
    SortedMap<Integer, Element> seasonMap = new TreeMap<Integer, Element>();
    for (Element e : seasons) {
      // parse the season # out from the element
      Matcher m = seasonUrlPattern.matcher(e.attr("href"));
      if (!m.matches())
        throw new RuntimeException("Found invalid URL from season element: " + e.attr("href"));
      int seasonNumber = Integer.parseInt(m.group(1));
      if (seasonInclusionCriteria.apply(seasonNumber))
        seasonMap.put(seasonNumber, e);
    }
    
    // (season) -> (episode -> (element for this <season, episode>, episode description))
    SortedMap<Integer, SortedMap<Integer, Tuple2<Element, String>>> seasonEpisodeMap = new TreeMap<Integer, SortedMap<Integer, Tuple2<Element, String>>>();
    // TODO: parallelize
    for (Map.Entry<Integer, Element> entry : seasonMap.entrySet()) {
      String seasonFragmentUrl = "http://www.sidereel.com" + entry.getValue().attr("data-load-from");
      Document seasonDoc = Jsoup.connect(seasonFragmentUrl).get();
      Elements episodes = seasonDoc.select("div[class^=episode-header] a[class^=expand-episode-trigger]");
      SortedMap<Integer, Tuple2<Element, String>> episodeMap = new TreeMap<Integer, Tuple2<Element, String>>();
      for (Element e : episodes) {
        Matcher m = episodeUrlPattern.matcher(e.attr("href"));
        if (!m.matches())
          throw new RuntimeException("Found invalid URL from episode element: " + e.attr("href"));
        int episodeNumber = Integer.parseInt(m.group(1));
        String episodeFragmentUrl = "http://www.sidereel.com" + e.attr("data-load-from");
        Document episodeDoc = Jsoup.connect(episodeFragmentUrl).get();
        Elements summaries = episodeDoc.select("div[class^=episode-summary]");
        episodeMap.put(episodeNumber, new Tuple2<Element, String>(e, summaries.first().text()));
      }
      seasonEpisodeMap.put(entry.getKey(), episodeMap);
    }

    return seasonEpisodeMap;
  }
  
  private static void printEpisodeSummaries(SortedMap<Integer, SortedMap<Integer, Tuple2<Element, String>>> summaries) {
    for (Map.Entry<Integer, SortedMap<Integer, Tuple2<Element, String>>> e0 : summaries.entrySet()) {
      System.out.println(String.format("==== Season %d ====", e0.getKey()));
      int maxLen = e0.getValue().lastKey().toString().length();
      for (Map.Entry<Integer, Tuple2<Element, String>> e1 : e0.getValue().entrySet()) {
        System.out.println(String.format("Episode %" + maxLen + "d: %s", e1.getKey(), e1.getValue()._2));
      }
    }
  }
  
  private static void episodeSummaries(String showName) throws IOException {
    printEpisodeSummaries(retrieveEpisodeSummaries(showName, new Function1<Integer, Boolean>() {
      @Override
      public Boolean apply(Integer a0) { return true; }
    }));
  }
  
  private static void episodeSummariesForSeason(String showName, final int season) throws IOException {
    printEpisodeSummaries(retrieveEpisodeSummaries(showName, new Function1<Integer, Boolean>() {
      @Override
      public Boolean apply(Integer a0) { return season == a0; }
    }));
  }
  
  private static void printUsageAndExit(int code) {
    System.err.println("Usage: java com.stephentu.SimpleSidereel <show name> [season] [episode] [--noparallel]");
    System.exit(code);
  }

  public static void main(String[] args) throws Exception {
    
    String showName;
    
    if (args.length == 0) 
      printUsageAndExit(1);
    
    showName = args[0];
    
    if (args.length < 3) {
      int season;
      if (args.length >= 2) {
        season = Integer.parseInt(args[1]);
        if (season <= 0) {
          System.err.println("Season number must be >= 1");
          System.exit(2);
        }
        episodeSummariesForSeason(showName, season);
      } else 
        episodeSummaries(showName);
    } else {
      int season, episode;
      boolean forceNoParallel = false;
      
      season = Integer.parseInt(args[1]);
      episode = Integer.parseInt(args[2]);
      
      if (season <= 0 || episode <= 0) {
        System.err.println("Season and episode numbers must be >= 1");
        System.exit(2);
      }
  
      if (args.length >= 4 && args[3].equals("--noparallel")) {
        System.out.println("Disabling parallelism");
        forceNoParallel = true;
      }
      episodeURLDump(showName, season, episode, forceNoParallel);
    }
  }
}
