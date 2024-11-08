package com.parser.javaparser.base;

public class Token
{
	public int start;
	public int end;
	public int type;
	
	public Token(int start, int end, int type){
		this.start = start;
		this.end = end;
		this.type = type;
	}
	
	public static class TokenType
	{
		public static final int UNKNOWN = 0;
		public static final int COMMENT = 1;
		public static final int STRING_LITERAL = 2;
		public static final int CHAR_LITERAL = 4;
		public static final int NUMBER = 8;
		public static final int OPERATOR = 16;
		public static final int KEYWORD = 32;
		public static final int CONSTANT = 64;
		public static final int IDENTIFIER = 128;
		public static final int VARIABLE = 256;
		public static final int FUNCTION = 512;
		public static final int TYPE = 1024;
		public static final int OBJECT = 2048;
	}
}
