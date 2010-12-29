package com.stephentu;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SimpleSidereel {

  public static void main(String[] args) throws Exception {
    
    //String showName = "The_Big_Bang_Theory";
    //int season = 22;
    //int episode = 1;
    
    String showName;
    int season, episode;
    
    if (args.length < 3) {
      System.err.println("Usage: java com.stephentu.SimpleSidereel [show name] [season] [episode]");
      System.exit(1);
    }
    
    showName = args[0];
    season = Integer.parseInt(args[1]);
    episode = Integer.parseInt(args[2]);
    
    if (season <= 0 || episode <= 0) {
      System.err.println("Season and episode numbers must be >= 1");
      System.exit(2);
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
      //System.out.println(pagination);
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
    
    boolean emittedLinks = false;
    for (Document docToCheck : docsToCheck) {
      Elements links = docToCheck.select("li[class^=link_] h2 a");
      //System.out.println(links);
      for (Element elem : links) {
        //System.out.println("Accessing: " + elem.attr("href"));
        Document innerDoc = Jsoup.connect("http://www.sidereel.com" + elem.attr("href")).get();
        Element trueLink = innerDoc.select("div[class^=playground] strong a").first();
        emittedLinks = true;
        if (trueLink != null) {
          String trueURL = trueLink.attr("href");
          System.out.println(trueURL);
        } else 
          System.out.println("Bad redirect: " + elem.attr("href"));
        //break;
      }
    }
    if (!emittedLinks)
      System.out.println("Sorry, could not find any links");
  }
}
