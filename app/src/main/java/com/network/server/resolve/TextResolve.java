package com.network.server.resolve;
import java.util.*;
import com.network.server.base.*;

public class TextResolve extends BaseResolve
{

	@Override
	public void parse(CharSequence text, int start, int end)
	{
		// TODO: Implement this method
	}

	@Override
	public Highlight[] colorize(CharSequence text, int start, int end)
	{
		ArrayList<Highlight> hights = new ArrayList<>();
		for(int i = start; i < end; ++i){
			if(isFuhao(text.charAt(i))){
				hights.add(new Highlight(i, i+1, ""));
			}
		}
		return null;
	}
	
	private static boolean isFuhao(char c){
		return Arrays.binarySearch(fuhao, c) > -1;
	}
	
	public static final char[] fuhao = new char[]{
		'(',')','{','}','[',']',
		'=',';',',','.',':',
		'+','-','*','/','%',
		'^','|','&','<','>','?','@',
		'!','~','\'','\n',' ','\t','#','"','\''
	};
	public static final char[] spilt = new char[]{
		'\n',' ','\t','<','>',
	};

	static{
		Arrays.sort(fuhao);
		Arrays.sort(spilt);
	}
}
