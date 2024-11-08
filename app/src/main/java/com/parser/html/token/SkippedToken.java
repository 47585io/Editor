package com.parser.html.token;

public class SkippedToken extends Token
{
	public SkippedToken(int start, int end, int kind){
		super(start, end, kind);
	}
}
