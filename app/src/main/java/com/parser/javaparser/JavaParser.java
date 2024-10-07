package com.parser.javaparser;
import java.util.regex.*;
import com.network.server.base.*;
import java.util.*;
import android.text.*;
import com.parser.javaparser.base.*;

public class JavaParser
{
	public static List<Token> parser(String code)
	{
		return new JavaLexer(code).tokens();
	}
}
