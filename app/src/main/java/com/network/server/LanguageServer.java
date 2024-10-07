package com.network.server;
import com.network.server.words.*;
import com.network.server.resolve.*;
import com.network.server.base.*;

public class LanguageServer
{
	public static LanguageServer connect(String language)
	{
		BaseResolve resolve;
		switch(language)
		{
			case "java":
				resolve = new JavaResolve();
				break;
			default:
			    resolve = new TextResolve();
		}
		return new LanguageServer(resolve);
	}
	
	private LanguageServer(BaseResolve resolve){
		mResolve = resolve;
	}
	
	public void sendTextChanged(CharSequence text, int start, int before, int after){
		
	}
	
	public Highlight[] colorize(CharSequence text, int start, int end){
		return mResolve.colorize(text, start, end);
	}

	public CharSequence format(CharSequence text){
		return mResolve.format(text);
	}
	
	public CompletionItem[] complete(CharSequence text, int index){
		return mResolve.complete(text, index);
	}
	
	private BaseResolve mResolve;
}
