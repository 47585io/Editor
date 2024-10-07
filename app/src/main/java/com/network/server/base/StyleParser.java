package com.network.server.base;
import android.graphics.*;

public class StyleParser
{
	/*  */
	public static String buildBackgroundColor(int color){
		return null;
	}
	
	/*  */
	public static String buildForegroundColor(int color){
		return "";
	}
	
	public static int parseForegroundColor(String style){
		return 0;
	}
	public static int parseBackgroundColor(String style){
		return 0;
	}
	
	public static final String FGCOLOR_PREFIX = "color:";
	
	public static final String BGCOLOR_PREFIX = "bgcolor:";
	
	public static final String UNDERLINE_PREFIX = "";
	
	public static final char SPILT_CHAR = ';';
	
	
	//public static final int TYPE_NUMBER = 0;
	
	//public static final int TYPE_STRING = 1;
	
	public static final int TYPE_KEYWORD = 2;
	
	//public static final int TYPE_GLOBAL = 3;
	
	public static final int TYPE_VARIABLE = 4;
	
	public static final int TYPE_FUNCTION = 5;
	
	public static final int TYPE_CLASS = 6;
}
