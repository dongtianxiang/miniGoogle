package edu.upenn.cis.stormlite.spouts;

public class RankSpout extends RankFileSpout {
	@Override
	public String getFilename() {
		return "ranks.txt";
	}

}
