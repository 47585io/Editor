package com.parser.html.token;

public class TokenKind
{
	public static final int EndOfFile = 256;
	
	public static final int TagOpener = 0;
	
	public static final int TagCloser = 1;
	
	public static final int ClosingTagMarker = 2;
	
	public static final int Name = 4;
	
	public static final int Value = 8;
	
	public static final int Equals = 16;
	
	public static final int Quote = 32;
	
	public static final int PlainText = 64;
	
	public static final int EntityReference = 128;
}
