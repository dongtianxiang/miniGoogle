package edu.upenn.cis.stormlite.bolts.pagerank;

import java.util.Map;
import java.util.UUID;
import org.apache.log4j.Logger;
import edu.upenn.cis.stormlite.bolts.IRichBolt;
import edu.upenn.cis.stormlite.bolts.OutputCollector;
import edu.upenn.cis.stormlite.infrastructure.Job;
import edu.upenn.cis.stormlite.infrastructure.OutputFieldsDeclarer;
import edu.upenn.cis.stormlite.infrastructure.TopologyContext;
import edu.upenn.cis.stormlite.routers.StreamRouter;
import edu.upenn.cis.stormlite.tuple.Fields;
import edu.upenn.cis.stormlite.tuple.Tuple;

public class PRMapBolt implements IRichBolt {
	
	public static Logger log = Logger.getLogger(PRMapBolt.class);
	public static Map<String, String> config;
    public String executorId = UUID.randomUUID().toString();
	public Fields schema = new Fields("key", "value"); 
	public Job mapJob;
	public OutputCollector collector;
	public Integer eosNeeded = 0;
	public double d;	
	public String serverIndex = null;

	@Override
	public String getExecutorId() {
		return executorId;
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(schema);
	}

	@Override
	public void cleanup() {
		// DO NOTHING
	}

	@Override
	public void execute(Tuple input) {
		
		if (!input.isEndOfStream()) {
			
			String[] destANDval = input.getStringByField("value").split(":");			
			String dest = destANDval[0];
			String val  = destANDval[1];	
			Double valNum = Double.parseDouble(val) * d;
			mapJob.map(dest, valNum.toString() , collector);
		}
		else {
    		eosNeeded--;    		
    		if (eosNeeded == 0) {
//    			log.info("Mapping phase completed");	    			
    		}
    		
    		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		collector.emitEndOfStream();
		}
	}

	@Override
	public void prepare(Map<String, String> stormConf, TopologyContext context, OutputCollector collector) {

        this.collector = collector;
        config = stormConf;
        d = Double.parseDouble(config.get("decayFactor"));
        config.put("status", "MAPPING");
        
        if (!stormConf.containsKey("mapClass")) {
        	throw new RuntimeException("Mapper class is not specified as a config option");
        }
        else {
        	
        	String mapperClass = stormConf.get("mapClass");        	
        	try {        		
				mapJob = (Job)Class.forName(mapperClass).newInstance();				
			} 
        	catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {				
				e.printStackTrace();
				throw new RuntimeException("Unable to instantiate the class " + mapperClass);
			}
        }
        
        if (!stormConf.containsKey("spoutExecutors")) {
        	throw new RuntimeException("Mapper class doesn't know how many input spout executors");
        }
        
		int numMappers = Integer.parseInt(stormConf.get("mapExecutors"));	
		int numSpouts  = Integer.parseInt(stormConf.get("spoutExecutors"));		
		int numWorkers = Integer.parseInt(stormConf.get("workers"));
        eosNeeded = ((numWorkers - 1) * numMappers  + 1) * numSpouts;	
        log.info("Num EOS required for MapBolt: " + eosNeeded);
	}

	@Override
	public void setRouter(StreamRouter router) {
		collector.setRouter(router);
	}

	@Override
	public Fields getSchema() {
		return schema;
	}

}
