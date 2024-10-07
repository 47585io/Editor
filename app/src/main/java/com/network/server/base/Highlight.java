package com.network.server.base;

public class Highlight 
{
    public int start;        // 高亮区域的起始位置
    public int end;          // 高亮区域的结束位置
    public String style;     // 类型样式，如颜色、字体样式等

    public Highlight(int start, int end, String style) {
        this.start = start;
        this.end = end;
        this.style = style;
	}
}
