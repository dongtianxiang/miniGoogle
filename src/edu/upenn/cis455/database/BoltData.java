package edu.upenn.cis455.database;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;

@Entity
public class BoltData {
	
	@PrimaryKey
	private String boltID;
	
	Map<String, List<String>> table;
	
	public BoltData() {
		
	}
	
	public BoltData(String boltID) {	
		table = new HashMap<>();
		this.boltID = boltID;
	}
	
	
	public void addValue(String key, String value) {
		
		if (!table.containsKey(key)) {
			table.put(key, new LinkedList<>());
		}		
		table.get(key).add(value);		
	}
	
	public void removeKey(String key) {		
		if (table.containsKey(key)) {
			table.remove(key);
		}
	}
	
	public List<String> getValues(String key) {		
		return table.get(key);
	}
}
