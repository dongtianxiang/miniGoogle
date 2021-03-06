package edu.upenn.cis455.crawler.info;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;

import edu.upenn.cis455.crawler.URLFrontierQueue;

/**
 * Class to store robot information from specific hostname.
 * @author cis555
 *
 */
public class Robot {
	String url;
	String hostName;
	String protocol;
	InputStream inputStream;
	List<String> disallowList = new ArrayList<String>();
	List<String> allowList = new ArrayList<String>();
	int crawlDelay = 0;
	long lastVisited = 0;
	static Logger log = Logger.getLogger(Robot.class);

	
	public Robot(String url){		
		try {
			URL robotUrl = new URL(getRobotURL(url));
			hostName = robotUrl.getHost();
			protocol = robotUrl.getProtocol();
			if(protocol.equals("http")){
				URLConnection urlConnection = robotUrl.openConnection();
				urlConnection.connect();
				inputStream = urlConnection.getInputStream();
			}
			else if(protocol.equals("https")){
				HttpsURLConnection urlConnection = (HttpsURLConnection)robotUrl.openConnection();
				urlConnection.connect();
				inputStream = urlConnection.getInputStream();
			}
			parseInputStream();
			setLastVisited();
		} 
		catch (MalformedURLException e) { 
		    e.printStackTrace();
		} 
		catch (IOException e) {   
		    System.out.println("IOException when getting Robots.txt in " + url);
		}
	}
	
	private String getRobotURL(String url){
		URLInfo urlinfo = new URLInfo(url);
		String hostName = urlinfo.getHostName();
		String protocol = urlinfo.getProtocol();
		String robotURL = protocol + "://" + hostName + "/robots.txt";
		return robotURL;
	}
	
	/**
	 * Parse the robots.txt file info
	 * @throws IOException
	 */
	private void parseInputStream() throws IOException{
		if(inputStream != null){
			BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
			String s;
			while((s = br.readLine()) != null){
				s = s.toLowerCase();
				if(s.toLowerCase().equals("user-agent: *")) break;
				else if(s.toLowerCase().equals("user-agent: cis455crawler")){
					break;
				}
			}
			
			while((s = br.readLine()) != null){
				s = s.toLowerCase();
			    if(s.startsWith("disallow: ")){
			    	disallowList.add(s.substring(10));
			    }
			    else if(s.startsWith("allow: ")){
			    	allowList.add(s.substring(7));
			    }
			    else if(s.startsWith("crawl-delay")){
			    	crawlDelay = Integer.parseInt(s.substring(13));
			    }
			    else if(s.startsWith("user-agent")) break;    // reach the end of this agent info
			}
			
			while(s != null){
				s = s.toLowerCase();
				if( s.startsWith("user-agent: cis455crawler") ) {
					disallowList.clear();
					allowList.clear();
					crawlDelay = 0;
					while((s = br.readLine()) != null){
						s = s.toLowerCase();
						if(s.startsWith("disallow: ")){
					    	disallowList.add(s.substring(10));
					    }
					    else if(s.startsWith("allow: ")){
					    	allowList.add(s.substring(7));
					    }
					    else if(s.startsWith("crawl-delay")){
					    	crawlDelay = Integer.parseInt(s.substring(13));
					    }
					    else if(s.startsWith("user-agent")) break;
					}
				}
				if(s == null) break;
				s = br.readLine();
			}

		}
	}
	
	/**
	 * To check if the crawling URL valid, by comparing the given URL with info get from robots.txt.
	 * The URL ends with "/" means general matching, otherwise means exact matching.
	 * @param url The URL to check
	 * @return True means allowed. False means disallowed.
	 */
	public boolean isURLValid(String url){
		URLInfo urlinfo = new URLInfo(url);
		String path = urlinfo.getFilePath();
		for(String allowpath:allowList){
			if(allowpath.endsWith("/")){
				//allowpath = allowpath.substring(0, allowpath.length() - 1);
				if(!path.equals(allowpath) && path.contains(allowpath)) return true;
			}
			else{
				if(path.equals(allowpath)) return true;
			}
		}
		for(String disallowpath:disallowList){
			if(disallowpath.endsWith("/")){
				//disallowpath = disallowpath.substring(0, disallowpath.length() - 1);
				if(!path.equals(disallowpath) && path.contains(disallowpath)){
					log.debug(url + ": Restricted. Not downloading");
					return false;
				}
			}
			else{
				if(path.equals(disallowpath)) {
					log.debug(url + ": Restricted. Not downloading");
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Set the last Crawling time
	 */
	public void setLastVisited(){
		Calendar cal = Calendar.getInstance();
    	lastVisited = cal.getTime().getTime();	
	}
	
	/**
	 * Get the last Crawling time
	 * @return
	 */
	public long getLastVisited(){
		return lastVisited;
	}
	
	/**
	 * Get the required delay duration
	 * @return
	 */
	public int getCrawlDelay(){
		return crawlDelay;
	}
	
	public static void main(String[] args){
		Robot r = new Robot("https://www.facebook.com");
		for(String str : r.allowList) {
			System.out.println("Allow :" + str);
		}
		
		for(String str : r.disallowList) {
			System.out.println("Disallow : " + str);
		}
		System.out.println(r.isURLValid("https://www.facebook.com/"));;
	}
}
