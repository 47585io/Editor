package com.network.server.resolve;
import com.network.server.base.*;
import com.network.server.words.*;
import java.util.*;
import com.editor.base.array.*;

public abstract class BaseResolve
{
	protected WordLib mWords;
	
	public BaseResolve(){
		mWords = new WordLib("");
	}
	
	public abstract void parse(CharSequence text, int start, int end)
	
	public Highlight[] colorize(CharSequence text, int start, int end){
		return EmptyArray.emptyArray(Highlight.class);
	}
	
	public CharSequence format(CharSequence text){
		return text;
	}
	
	public CompletionItem[] complete(CharSequence text, int index){
		return EmptyArray.emptyArray(CompletionItem.class);
	}
	
	public static int getLineStart(CharSequence text, int offset){
		return 0;
	}
	public static int getLineEnd(CharSequence text, int offset){
		return 0;
	}
	public static int getWordStart(CharSequence text, int offset){
		return 0;
	}
	public static int getWordEnd(CharSequence text, int offset){
		return 0;
	}
}
