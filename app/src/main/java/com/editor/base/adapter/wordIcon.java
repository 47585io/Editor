package com.editor.base.adapter;

public class wordIcon
{
	public int icon;
	public CharSequence text;

	public wordIcon(int icon, CharSequence text){
		this.icon = icon;
		this.text = text;
	}
	public wordIcon(wordIcon item){
		this.icon = item.icon;
		this.text = item.text;
	}
	
	public void set(int icon, CharSequence text){
		this.icon = icon;
		this.text = text;
	}
	public void set(wordIcon item){
		this.icon = item.icon;
		this.text = item.text;
	}
}
