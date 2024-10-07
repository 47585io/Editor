package com.network.server.words;

import java.util.HashMap;

public class WordLib
{
	private String mName;
	private TrieTree mTree;
	private HashMap<String, WordData> mKeyToWord;

	public WordLib(String name){
		mName = name;
		mTree = new TrieTree();
		mKeyToWord = new HashMap<>();
	}
	
	public void putWord(String word, WordData data){
		mTree.insert(word);
		data.parent = this;
		mKeyToWord.put(word, data);
	}
	
	public void removeWord(String word){
		mTree.delete(word);
		WordData data = mKeyToWord.remove(word);
		if(data != null){
			data.parent = null;
		}
	}
	
	public boolean hasWord(String word){
		return mKeyToWord.containsKey(word);
	}
	
	public boolean startsWith(String prefix){
		return mTree.searchPrefix(prefix) != null;
	}
	
	public WordData getWordData(String word){
		return mKeyToWord.get(word);
	}
	
	public String[] postWords(String prefix){
		return mTree.postWords(prefix);
	}
	
	public String getName(){
		return mName;
	}
}
