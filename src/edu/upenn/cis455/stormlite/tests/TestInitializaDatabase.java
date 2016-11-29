package edu.upenn.cis455.stormlite.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import edu.upenn.cis455.database.DBInstance;
import edu.upenn.cis455.database.DBManager;
import edu.upenn.cis455.database.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import edu.upenn.cis455.database.DBInstance;
import edu.upenn.cis455.database.DBManager;
import edu.upenn.cis455.database.Node;

public class TestInitializaDatabase {
	
	public static void main(String[] args) throws IOException {
		
        DBManager.createDBInstance("graphStore");
        
        // store nodes in BDB before start, should be done during crawling phase
        DBInstance graphData = DBManager.getDBInstance("graphStore");
        
        File links = new File("data1/links.txt");
        BufferedReader reader = new BufferedReader(new FileReader(links));
        
        // TODO: try decay factor d = 0.85
        // TODO: add self-loop to page without outgoing links 
        
        while (true) {
        	
        	String line = reader.readLine();        	
        	if (line == null) break;        	
        	String[] parts = line.split(" -> ");        	
        	String src = parts[0];
        	// TODO: use sha1 to hash src url to ...
        	
        	String[] neighbors = parts[1].split(", ");
        	Node srcNode = new Node(src);      	
        	if (neighbors.length == 0) {
        		// add self-loop if page is a sink
        		srcNode.addNeighbor(src);	
        	}
        	else {
	        	for (String neighborId: neighbors) {
	        		srcNode.addNeighbor(neighborId);
	        	}
        	}
        	
        	graphData.addNode(srcNode);
        }
        
        reader.close();
	}

}
