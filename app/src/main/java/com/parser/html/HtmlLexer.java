package com.parser.html;
import java.util.*;
import com.parser.html.token.*;

public class HtmlLexer implements TokenStream
{
	private int mCurrentPosition;
	private String mText;
	private ArrayList<Token> mTokens;
	
	public HtmlLexer(String text){
		mCurrentPosition = 0;
		mText = text;
		mTokens = new ArrayList<>();
		spiltText();
	}
	
	/**
	 * 将源文本以指定字符分隔为一个个词的Token，存储在mTokens中
	 */
	private void spiltText()
	{
		int pos = 0;
		while(true){
			Token next = nextToken(pos);
			mTokens.add(next);
			if(next.kind == TokenKind.EndOfFile){
				break;
			}
			pos = next.end;
		}
	}
	
	private Token nextToken(int pos)
	{
		if(pos >= mText.length()){
			return new Token(pos, pos, TokenKind.EndOfFile);
		}
		while(true){
			char charCode = mText.charAt(pos);
			
		}
	}
	
	private void mergeTokens(){
		
	}

	@Override
	public Token scanNextToken()
	{
		if(mCurrentPosition >= mTokens.size()){
			return mTokens.get(mTokens.size()-1);
		}
		return mTokens.get(mCurrentPosition++);
	}

	@Override
	public int getCurrentPosition(){
		return mCurrentPosition;
	}

	@Override
	public void setCurrentPosition(int pos){
		this.mCurrentPosition = pos;
	}

	@Override
	public int getEndOfFilePosition(){
		return mTokens.size() - 1;
	}

	@Override
	public ArrayList<Token> getTokensArray(){
		return mTokens;
	}
}
