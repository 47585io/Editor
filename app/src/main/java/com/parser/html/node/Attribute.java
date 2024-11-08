package com.parser.html.node;
import com.parser.html.token.*;

public class Attribute extends Node
{
	/* 属性的名字在源文本中的范围 */
	private Token key;
	
	/* 属性的值在源文本中的范围 */
	private Token value;
}
