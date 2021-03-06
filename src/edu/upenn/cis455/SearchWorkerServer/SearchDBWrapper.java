package edu.upenn.cis455.SearchWorkerServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.log4j.Logger;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.EntityCursor;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;
import edu.upenn.cis455.SearchWorkerServer.PageRank;
import edu.upenn.cis455.SearchWorkerServer.URLhashing;
import edu.upenn.cis455.SearchWorkerServer.Word;

/**
 * Basic class to connect Berkeley DB, including add and get User, Page, etc. from Database.
 * @author cis555
 *
 */
public class SearchDBWrapper {
	
	private static String envDirectory = null;
	
	private static Environment myEnv;
	private static EntityStore store;
	
	private static SearchDBWrapper DBinstance = null;
	
	static Logger log = Logger.getLogger(SearchDBWrapper.class);
	PrimaryIndex<String, URLhashing> hashingIndex;
	PrimaryIndex<String, Word> wordIndex;
	PrimaryIndex<String, PageRank> pagerankIndex;
	
	/* TODO: write object store wrapper for BerkeleyDB */
	private SearchDBWrapper(String envDirectory){
		//Initialize myEnv
		this.envDirectory = envDirectory;
		try{
			EnvironmentConfig envConfig = new EnvironmentConfig();
			//Create new myEnv if it does not exist
			envConfig.setLockTimeout(500, TimeUnit.MILLISECONDS);
			envConfig.setAllowCreate(true);
			//Allow transactions in new myEnv
			envConfig.setTransactional(true);
			//Create new myEnv
			File dir = new File(envDirectory);
			if(!dir.exists())
			{
				dir.mkdir();
				dir.setReadable(true);
				dir.setWritable(true);
			}
			myEnv = new Environment(dir,envConfig);
			
			//Create new entity store object
			StoreConfig storeConfig = new StoreConfig();
			storeConfig.setAllowCreate(true);
			storeConfig.setTransactional(true);
			store = new EntityStore(myEnv,"DBEntityStore",storeConfig);
			
			hashingIndex  = store.getPrimaryIndex(String.class, URLhashing.class);
			wordIndex     = store.getPrimaryIndex(String.class, Word.class);
			pagerankIndex = store.getPrimaryIndex(String.class, PageRank.class);
			
		}
		catch(DatabaseException e)
		{
			e.printStackTrace();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public synchronized static SearchDBWrapper getInstance(String envDirectory) {
		if(DBinstance == null) {
			close();
			DBinstance = new SearchDBWrapper(envDirectory);
		}
		return DBinstance;
	}
	
	public void sync() {
		if(store != null) store.sync();
		if(myEnv != null) myEnv.sync();
	}
	
	public Environment getEnvironment() {
		return myEnv;
	}
	
	public EntityStore getStoreUser() {
		return store;
	}
	
	public EntityStore getStoreCrawler() {
		return store;
	}
	
	//Close method
	public synchronized static void close() {
		
		//Close store first as recommended
		if(store!=null) {
			try{
				store.close();
			}
			catch(DatabaseException e)
			{
				e.printStackTrace();
			}
		}
		
		
		if(myEnv!=null) {
			try{
				myEnv.close();
			}
			catch(DatabaseException e)
			{
				e.printStackTrace();
			}
		}
		DBinstance = null;
	}
	
	/* Hashing converting */
	
	public URLhashing getURLHashing(String hashing) {
		return hashingIndex.get(hashing);
	}
	
	public void putURLHashing(String hashing, String url) {
		URLhashing h = new URLhashing(hashing, url);
		hashingIndex.put(h);
	}
	
	public long getHashingSize(){
		return hashingIndex.count();
	}
	
	public String convertHashing(String hashing){
		URLhashing h = getURLHashing(hashing);
		if(h == null) return null;
		return h.getUrl();
	}
	
	/* Word Looking Up */
	
	public Word getWord(String word) {
		Word w = null;
		synchronized(wordIndex){
			w = wordIndex.get(word);
		}
		return w;
	}
	
	public void putWord(Word word) {
		synchronized(wordIndex) {
			wordIndex.put(word);
		}
	}
	
	public void removeWord(String word){
		synchronized(wordIndex) {
			wordIndex.delete(word);
		}
	}
	
	public List<String> getWordList(){
		synchronized(wordIndex) {
			List<String> res = new ArrayList<String>();
			EntityCursor<Word> entities = this.wordIndex.entities();
			for(Word w : entities) {
				res.add(w.getWord());
			}
			entities.close();
			return res;
		}
	}
	
	public boolean containsWord(String word) {
		synchronized(wordIndex) {
			return wordIndex.contains(word);
		}
	}
	
	public long wordListSize() {
		synchronized(wordIndex) {
			return wordIndex.count();
		}
	}
	
	/* PageRank Look Up */
	
	public void putPageRank(PageRank pg) {
		synchronized(pagerankIndex){
			pagerankIndex.put(pg);
		}
	}
	
	public void putPageRank(String url, Double pagerank) {
		PageRank pg = new PageRank(url, pagerank);
		synchronized(pagerankIndex){
			pagerankIndex.put(pg);
		}
	}
	
	public PageRank getPageRank(String url) {
		synchronized(pagerankIndex){
			return pagerankIndex.get(url);
		}
	}
	
	public Double getPageRankValue(String url) {
		PageRank pg = getPageRank(url);
		return pg.getPagerank();
	}
	
	public static void generateHashingDB(String dbPath, String fileName) throws IOException{
		SearchDBWrapper db = SearchDBWrapper.getInstance(dbPath);
		File file = new File(fileName);
		FileReader rf = new FileReader(fileName);
		BufferedReader bf = new BufferedReader(rf);
		String line = null;
		int count = 0;
		while((line = bf.readLine()) != null) {
			count++;
			String[] split = line.split(" ");
			db.putURLHashing(split[0], split[1]);
			if(count % 1000 == 0) System.out.println(count); 
		}
		System.out.println(count);
		db.close();
	}
	
	public static void generatePageRankDB(String dbPath, String fileName) throws IOException {
		SearchDBWrapper db = SearchDBWrapper.getInstance(dbPath);
		File file = new File(fileName);
		FileReader rf = new FileReader(fileName);
		BufferedReader bf = new BufferedReader(rf);
		String line = null;
		int count = 0;
		while((line = bf.readLine()) != null) {
			count++;
			String[] split = line.split(" ");
			String url = split[0];
			String pagerankStr = split[1];
			Double pagerankValue = Double.parseDouble(pagerankStr);
			db.putPageRank(url, pagerankValue);
			if(count % 1000 == 0) System.out.println(count); 
		}
		System.out.println(count);
		db.close();
	}
	
	public static void generatePageRankInWord(String dbPath) {
		SearchDBWrapper db = SearchDBWrapper.getInstance(dbPath);
		List<String> wordList = db.getWordList();
		int count = 0;
		for(String word : wordList){
			count++;
			if(count % 1000 == 0) System.out.println(count);
			Word w = db.getWord(word);
			for(String url : w.getWeight().keySet()) {
				Double pg = db.getPageRankValue(url);
				Double weight_pg = w.getWeight(url) * pg;
				w.weightWithPG.put(url, weight_pg);
			}
			db.putWord(w);
		}
		db.close();
	}

	public static void main(String[] args) throws IOException{
//		generateHashingDB("./IndexerDB/indexer2", "HashingTransfer.txt");
		generatePageRankDB("./DistributedDB/indexer4", "PageRankReadIn.txt");
		generatePageRankInWord("./DistributedDB/indexer4");
//		SearchDBWrapper db = SearchDBWrapper.getInstance("./IndexerDB/indexer0");
//		System.out.println(db.convertHashing("c0c9f27c439972c6b0d3976de6226c08fc310cc6"));
//		System.out.println(db.getPageRankValue(DigestUtils.sha1Hex("https://maps.google.com/")));
//		db.close();
//		long time1 = System.currentTimeMillis();
//		System.out.println(db.convertHashing("42b66ab5120b79f3e5fbb80385bd745af737c1d7"));
//		long time2 = System.currentTimeMillis();
//		System.out.println(time2 - time1 + "ms");
	}
}