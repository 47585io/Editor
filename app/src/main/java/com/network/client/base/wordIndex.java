package com.network.client.base;

public class wordIndex
{
	public Object span;
	public int start;
	public int end;
	public int flags;

	public wordIndex(Object span, int start, int end, int flags){
		this.span = span;
		this.start = start;
		this.end = end;
		this.flags = flags;
	}
}
