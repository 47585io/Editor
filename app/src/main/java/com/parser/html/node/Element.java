package com.parser.html.node;
import java.util.*;
import com.parser.html.token.*;

public class Element extends Node
{
	/* 标签的名字在源文本中的范围 */
	private Range name;
	
	/* 标签的属性在源文本中的范围 */
	private ArrayList<Attribute> attrs;
	
	/* 子元素 */
	private ArrayList<Node> children;
}
