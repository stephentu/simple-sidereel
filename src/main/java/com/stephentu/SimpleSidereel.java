package com.stephentu;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

  public static void main(String[] args) throws Exception {
    
    String showName;
    int season, episode;
    boolean forceNoParallel = false;
    
    if (args.length < 3) {
      System.err.println("Usage: java com.stephentu.SimpleSidereel [show name] [season] [episode] [--noparallel]");
      System.exit(1);
    }
    
    showName = args[0];
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
}
