package com.parser.javaparser;
import com.parser.javaparser.base.*;
import com.parser.javaparser.base.Token.*;
import java.util.*;
import java.util.regex.*;
import java.lang.invoke.*;
import android.text.*;

public class JavaLexer
{
	private String mText;
	private List<Token> mTokens;
	
	public JavaLexer(String text){
		mText = text;
		mTokens = new LinkedList<>();
		spiltText();
		analyzeTokens();
	}
	public List<Token> tokens(){
		return mTokens;
	}
	
	/**
	 * 将源文本以空白字符或运算符分隔为一个个词的Token，存储在cachedTokens中
	 * 无效的空白字符被舍弃，单个运算符被算作一个Token，整段字符串和注释被算作一个Token
	 */
	private void spiltText()
	{
		TimeTrack track = new TimeTrack("JavaLexer spiltText", mText.length(), 10000);
		int index = 0;
		while(index < mText.length())
		{
			//寻找下一个词的起始位置
			int start = findTokenStart(index);
			if (start == mText.length()){
				break;
			}
			//寻找当前的词的末尾位置
			int end = findTokenEnd(start);
			//添加Token
			mTokens.add(new Token(start, end, 0));
			//跳跃到当前词的末尾
			index = end;
			track.trackTo(index);
		}
	}
	
	private void spiltText2()
	{
		TimeTrack track = new TimeTrack("JavaLexer spiltText", mText.length(), 10000);
		final int length = mText.length();
		for(int index = 0; index < length; ++index)
		{
			char ch = mText.charAt(index);
			if(operators.indexOf(ch) > -1){
				mTokens.add(new Token(index, index+1, TokenType.OPERATOR));
			}
			track.trackTo(index);
		}
	}
	
	private int findTokenStart(int index){
		// 跳过空白字符
        while (index < mText.length() && Character.isWhitespace(mText.charAt(index))) {
            index++;
        }
		return index;
	}
	
	private int findTokenEnd(int index)
	{
		//注释
		if(index < mText.length() - 1 && mText.charAt(index) == '/'){
			if(mText.charAt(index + 1) == '/'){
				int find = mText.indexOf('\n', index + 2);
				return find == -1 ? mText.length() : find;
			}
			if(mText.charAt(index + 1) == '*'){
				int find = mText.indexOf("*/", index + 2);
				return find == -1 ? mText.length() : find + 2; // +2 to include "*/"
			}
		}
		//字符串
		if(mText.charAt(index) == '"')
		{
			boolean ignored = false;
			int end = index + 1;
            for (; end < mText.length(); end++){
				char ch = mText.charAt(end);
				if(ch == '\n'){
					return end;
				}
				if(ch == '"' && !ignored){
					return end + 1;
				}
				
				if(ch == '\\'){
					ignored = !ignored;
				}else{
					ignored = false;
				}
            }
			return mText.length();
        }
		//字符
		if(mText.charAt(index) == '\'')
		{
			int count = 0;
			boolean ignored = false;
			int end = index + 1;
			for (; end < mText.length(); end++){
				char ch = mText.charAt(end);
				if(ch == '\n'){
					return end;
				}
				if(ch == '\'' && !ignored){
					return end + 1;
				}
				if(count == 1){
					return end;
				}

				if(ch == '\\'){
					ignored = !ignored;
				}else{
					ignored = false;
				}
				if(!ignored){
					count++;
				}
            }
			return mText.length();
		}
		//数字
		if(mText.charAt(index) == '.' || Character.isDigit(mText.charAt(index))){
			Matcher ma = Pattern.compile("((\\d+\\.(\\d*)?|\\.\\d+)([eE][+-]?\\d+)?[fF]?|(0b[01]+|0o[0-7]+|0x[0-9a-fA-F]+|[0-9]+))").matcher(mText);
			if(ma.find(index) && ma.start() == index){
				return ma.end();
			}
		}
		//运算符
		if(operators.indexOf(mText.charAt(index)) > -1){
			return index + 1; //运算符情况较复杂，此时还不能判定连续的运算符是否为一个
		}
		//标识符(只要不为空白字符或者运算符就可以)
		//可能是字母，汉字，数字或下划线，但它们都不是分隔符
		while (index < mText.length() && !Character.isWhitespace(mText.charAt(index))
			   && operators.indexOf(mText.charAt(index)) < 0) {
            index++;
        }
		return index;
	}
	
	/* 遍历令牌列表，根据令牌本身的词或和它相邻的词来确定它的类型 */
	private void analyzeTokens(){
		preAnalyze();
		postAnalyze();
	}
	
	/** 
	 * 遍历令牌列表，对每个词单独进行预分析，
	 * 将确定注释，字符和字符串，运算符，数字，关键字，保留字
	 * 对于其它的满足标识符命名条件的词在此时暂时定为标识符，用于在以后更改为变量，函数，类型等
	 * 单个字符的运算符以后可能会考虑相邻的运算符来合并，不能识别的内容将是错误的
	 */
	private void preAnalyze()
	{
		TimeTrack track = new TimeTrack("JavaLexer analyze", mTokens.size(), 1000);
		int i = 0;
		for(Token token : mTokens)
		{
			String text = mText.substring(token.start, token.end);
			if (text.startsWith("//") || text.startsWith("/*")) {
                token.type = TokenType.COMMENT;
            } else if (text.charAt(0) == '"') {
                token.type = TokenType.STRING_LITERAL;
            } else if (text.charAt(0) == '\'') {
                token.type = TokenType.CHAR_LITERAL;
			} else if(text.matches("(0b[01]+|0o[0-7]+|0x[0-9a-fA-F]+|[0-9]+)") 
					||text.matches("(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?[fF]?")){
				token.type = TokenType.NUMBER;
			} else if (operators.indexOf(text.charAt(0)) > -1) {
                token.type = TokenType.OPERATOR;
            } else if (keywords.contains(text)) {
                token.type = TokenType.KEYWORD;
            } else if (constants.contains(text)){
				token.type = TokenType.CONSTANT;
			} else if (text.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                token.type = TokenType.IDENTIFIER;
			} else {
				token.type = TokenType.UNKNOWN;
			}
			track.trackTo(i++);
		}
	}
	
	private void postAnalyze()
	{
		checkType();
		for(int i = 0; i < mTokens.size(); ++i)
		{
			Token token = mTokens.get(i);
			String text = mText.substring(token.start, token.end);
			if(i > 0){
				if(tokenOperator(mTokens.get(i-1)) == '@' && token.type == TokenType.IDENTIFIER){
					token.type = TokenType.TYPE;
				}
			}
			if(i < mTokens.size() - 1)
			{
				Token next = mTokens.get(i + 1);
				String nText = mText.substring(next.start, next.end);
				if(token.type == TokenType.IDENTIFIER){
					if(bothWithOperator(i)){
						token.type = TokenType.VARIABLE;
						if(i > 0){
							Token prev = mTokens.get(i-1);
							if(prev.type == TokenType.IDENTIFIER || prev.type == TokenType.VARIABLE){
								prev.type = TokenType.TYPE;
							}
						}
					}
					char ch = nText.charAt(0);
					switch(ch){
						case '(':
							token.type = TokenType.FUNCTION;
							break;
						case '.':
							token.type = TokenType.OBJECT;
							break;
					}
				}
			}
		}
	}

	private void checkType()
	{
		//首先检查泛型和数组
		boolean isGeneric = false;
		for(int i = 0; i < mTokens.size(); ++i)
		{
			if(!isGeneric && i < mTokens.size() - 2)
			{
				//泛型检查
				if (tokenOperator(mTokens.get(i)) == '<' &&
				    mTokens.get(i+1).type == TokenType.IDENTIFIER &&
				    (tokenOperator(mTokens.get(i+2)) == '<' ||
					 tokenOperator(mTokens.get(i+2)) == '>' ||
					 tokenOperator(mTokens.get(i+2)) == ',')){
					isGeneric = true; // <T> or <T<...>> or <T,E> 
					//<T extends Object>，so extends也是等同于<
					//设置前面的那个标识符为类型
					if(i > 0 && mTokens.get(i-1).type == TokenType.IDENTIFIER){
						mTokens.get(i-1).type = TokenType.TYPE;
					}
				} //数组检查
				else if(mTokens.get(i).type == TokenType.IDENTIFIER &&
				    tokenOperator(mTokens.get(i+1)) == '[' && tokenOperator(mTokens.get(i+2)) == ']'){
					mTokens.get(i).type = TokenType.TYPE;
				}
			}
			//向后匹配所有泛型，然后终止
			if(isGeneric){
				//如果满足<T or ,T的规则就继续向后匹配，<T<T...
				if(!(tokenOperator(mTokens.get(i)) == '<' || tokenOperator(mTokens.get(i)) == ',')){
					isGeneric = false;
					continue;
				}
				if(i < mTokens.size() - 1 && mTokens.get(i+1).type == TokenType.IDENTIFIER){
					mTokens.get(++i).type = TokenType.TYPE;
				}
			}
		}
		
		//然后检查关键字处的类型
		boolean isImplementing = false;
		for(int i = 0; i < mTokens.size(); ++i)
		{
			if(!isImplementing && i < mTokens.size() - 1){
				String text = tokenString(mTokens.get(i));
				switch(text){
					case "class":
					case "interface":
					case "extends":
					case "new":
					case "instanceof":
						if(mTokens.get(i+1).type == TokenType.IDENTIFIER){
							mTokens.get(i+1).type = TokenType.TYPE;
						}
						break;
					case "implements":
						isImplementing = true;
						break;
				}
			}
			if(isImplementing){
				//允许出现泛型和数组符号，以及逗号，任何其它的符号出现则终止查找
				if(mTokens.get(i).type == TokenType.OPERATOR && 
				    "<>[].,".indexOf(tokenOperator(mTokens.get(i))) < 0){
					isImplementing = false;
					continue;
				}
				if(mTokens.get(i).type == TokenType.IDENTIFIER){
					mTokens.get(i).type = TokenType.TYPE;
				}
			}
		}
	}
	
	private boolean bothWithOperator(int i){
		return (i < mTokens.size() - 1 && mTokens.get(i+1).type == TokenType.OPERATOR)
		    || (i > 0 && mTokens.get(i-1).type == TokenType.OPERATOR); //instanceof运算符
	}
	private char tokenOperator(Token token){
		return token.type == TokenType.OPERATOR ? tokenString(token).charAt(0) : 0;
	}
	private String tokenString(Token token){
		return mText.substring(token.start, token.end);
	}
	
	private static final String[] keys = new String[]{
		"enum","assert",
		"package","import",
		"static","final","this","super",
		"try","catch","finally","throw","throws",
		"public","protected","private",
		"native","strictfp","synchronized","transient","volatile",
		"class","interface","abstract","implements","extends","new","instanceof",
		"byte","char","boolean","short","int","float","long","double","void",
		"if","else","while","do","for","switch","case","default","break","continue","return",
	};
	
	private static final String[] consts = new String[]{
		"null", "true", "false"
	};
	
	private static final HashSet<String> keywords = new HashSet<>(Arrays.asList(keys)); 

	private static final HashSet<String> constants = new HashSet<>(Arrays.asList(consts)); 
	
	private static final String operators = "(){}[]=.:,;+-*/%^|&<>?@!~'\"";
}
