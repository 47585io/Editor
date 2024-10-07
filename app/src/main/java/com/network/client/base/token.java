package com.network.client.base;

public class token
{
	public int start;
	public int end;
	public CharSequence src;

	public token(int start, int end, CharSequence src){
		this.start = start;
		this.end = end;
		this.src = src;
	}
}
