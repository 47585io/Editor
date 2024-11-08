package com.parser.html.token;

public class Token
{
	public int start;
	public int end;
	public int kind;
	
	public Token(int start, int end, int kind){
		this.start = start;
		this.end = end;
		this.kind = kind;
	}
}
