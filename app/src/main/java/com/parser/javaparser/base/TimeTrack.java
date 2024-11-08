package com.parser.javaparser.base;
import android.util.*;

public class TimeTrack
{
	private int timer; //当前达到第几块
	private final int block; //每块的长度
	private final String title;
	
	/* 将长度为total的任务每隔once分成一块 */
	public TimeTrack(String title, int total, int once){
		this.title = title;
		this.block = once == 0 ? 1 : once;
		Log.w(title, "run total " + total + " and count " + total / block);
	}
	
	/* 跟踪到指定长度的位置，每达到下一块的位置就报告一次 */
	public void trackTo(int to){
		int now = to / block;
		if(timer != now){
			timer = now;
			Log.w(title, "total update to " + to + ", bili update to " + now);
		}
	}
}
