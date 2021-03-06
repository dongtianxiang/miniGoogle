package edu.upenn.cis455.crawler.bolts;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import edu.upenn.cis.stormlite.infrastructure.*;
import edu.upenn.cis.stormlite.bolts.IRichBolt;
import edu.upenn.cis.stormlite.bolts.OutputCollector;
import edu.upenn.cis.stormlite.routers.StreamRouter;
import edu.upenn.cis.stormlite.tuple.Fields;
import edu.upenn.cis.stormlite.tuple.Tuple;
import edu.upenn.cis.stormlite.tuple.Values;
import edu.upenn.cis455.crawler.PageDownloader;
import edu.upenn.cis455.crawler.RobotCache;
import edu.upenn.cis455.crawler.URLFrontierQueue;
import edu.upenn.cis455.crawler.XPathCrawler;

/**
 * Bolt Component used to connect URL and get the document
 * @author cis555
 *
 */
public class CrawlerBolt implements IRichBolt{
	static Logger log = Logger.getLogger(CrawlerBolt.class);
	
	Fields schema = new Fields("url", "URLStream");
	
	String executorId = UUID.randomUUID().toString();
	
	private OutputCollector collector;
	
	private URLFrontierQueue urlQueue;
	
    public CrawlerBolt() {
    	log.debug("Starting CrawlerBolt");
    	this.urlQueue = XPathCrawler.urlQueue;
    }
    
    /**
     * Used for debug purposes, shows our exeuctor/operator's unique ID
     */
	@Override
	public String getExecutorId() {
		return executorId;
	}
	
    /**
     * Lets the downstream operators know our schema
     */
	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(schema);
	}
	
    /**
     * Shutdown, just frees memory
     */
	@Override
	public void cleanup() {}
	
	
	private String removeHashTagInURL(String link) {
		String[] split = link.split("#");
		return split[0];
	}
	
	private String removeQueryString(String link) {
		String[] split = link.split("\\?");
		return split[0];
	}
	
	private boolean isMailTo(String link) {
		if(link.startsWith("mailto")) return true;
		return false;
	}
	
    /**
     * Process a tuple received from the stream, incrementing our
     * counter and outputting a result
     */
	@Override
	public void execute(Tuple input) {
		long start = System.currentTimeMillis();
		String curURL = input.getStringByField("url");
		Connection conn = null;
		
		try {
			RobotCache.setCurrentTime(curURL);  
			if(!urlQueue.filter(curURL)) {     // HEAD REQUEST set the last visited time
				log.debug(curURL + "  --> not passing filter, not crawling this URL");
				return;
			}
			
			if(!RobotCache.checkDelay(curURL)) {
				log.info(curURL + "  --> delay check failed in CrawlerBolt, not crawling this URL");
				return;
			}
			
			conn = Jsoup.connect(curURL);
			conn.userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36");
			Response rep = conn.header("User-Agent", "cis455crawler").execute();
			Document doc = rep.parse();
			String contentType = rep.contentType();
			
			long workTime = System.currentTimeMillis();
			
//			collector.emit(new Values<Object>(curURL, doc, contentType));
//			long end = System.currentTimeMillis();
//			log.info(curURL + " crawled ---> working Time: " + (workTime - start) + " ms " 
//					+ "emit time: " + (end - workTime) + " ms");
			log.info(curURL + " crawled ---> crawling Time: " + (workTime - start) + " ms ");
			start = System.currentTimeMillis();
						
			/* Part of previous Download Bolt */
			String type = contentType;
			String url = curURL;
			RobotCache.setCurrentTime(url);
			/* The Downloader itself updated the last visited time */
			PageDownloader.download(url, doc, type);
			
			long downloadTime = System.currentTimeMillis();
			
//			urlQueue.putIntoVisitedURL(url, RobotCache.getLastVisited(url));  // VisitedURL last modified time updating not necessary here.
			int executedSize = urlQueue.addExecutedSize();
			log.info("----> " + url + ": Downloading");
			log.info("size: " + doc.toString().length());
			log.info(executedSize);
			
			Queue<String> linklist = new LinkedList<String>();
			Elements links = doc.select("a[href]");
			for (Element link : links) {
				String urlExtracted = link.attr("abs:href");      // get extracted URL
				if(isMailTo(urlExtracted)) continue;
				urlExtracted = removeHashTagInURL(urlExtracted);  // remove hashtag from links
				urlExtracted = removeQueryString(urlExtracted);   // remove query strings
				
				linklist.add(urlExtracted);
			}
			
			long parsingTime = System.currentTimeMillis();			
			collector.emit(new Values<Object>(url, linklist));			
			long end = System.currentTimeMillis();
			log.info(url + " downloaded ---> Downloading Time: " + (downloadTime - start) + " ms " 
					+ "Parsing time: " + (parsingTime - downloadTime) + " ms " + "emit time: " + (end - downloadTime) + " ms");		
			
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			log.error(curURL);
			log.error(sw.toString()); // stack trace as a string
		} 
				
	}
	
    /**
     * Initialization, just saves the output stream destination
     */
	@Override
	public void prepare(Map<String, String> stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}
	
	/**
	 * Called during topology setup, sets the router to the next
	 * bolt
	 */
	@Override
	public void setRouter(StreamRouter router) {
		this.collector.setRouter(router);
	}


	/**
	 * The fields (schema) of our output stream
	 */
	@Override
	public Fields getSchema() {
		return schema;
	}

}
