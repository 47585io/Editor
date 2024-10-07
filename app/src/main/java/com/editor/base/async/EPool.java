package com.editor.base.async;
import java.util.*;

/**
 * 简单的元素缓存池，申明一下，这样做的原因是：
 * 元素可以重复用，而不是创建之后马上销毁，太浪费了
 */
public abstract class EPool<T>
{
	private ArrayList<T> Es; //元素缓存池
	private int uSize;       //缓存池中被使用的元素个数
	private int taskCount;   //正在使用缓存池的任务数

	private final int maxCount;  //缓存池中最多拥有的元素个数
	private final int onceCount; //缓存池中元素不足时，需要添加的元素个数

	public EPool(int maxCount, int onceCount){
		Es = new ArrayList<>();
		uSize = 0;
		taskCount = 0;
		this.maxCount = maxCount;
		this.onceCount = onceCount;
	}

	//返回池子末尾的元素给你使用，该元素被标记
	synchronized public T get()
	{
		//从池子中获取一个元素，如果池子元素不足，创建一些
		//如果超出最大的数量，直接创建一个返回
		if(uSize + 1 > maxCount)
			return create();
		if(uSize + 1 > Es.size())
			put();

		//每拿走一个元素，指针向后偏移一步
		//这可以确保你不会拿到同样的元素
		T E = Es.get(uSize++);
		return E;
	}
	
	//获取并重置元素
	public T getAndReset(){
		T E = get();
		resetE(E);
		return E;
	}

	//开始一次记录
	synchronized public void start(){		
		taskCount++;
	}
	//结束一次记录
	synchronized public void stop()
	{
		taskCount--;
		if(taskCount == 0){
			//必须保证所有的任务都完成了，才收回元素
			//将指针向前偏移来回收元素
		    uSize = 0; 
		}
	}

	//向池子中放入一些新的元素
	private void put() {
		int eSize = Es.size();
		int size = eSize + onceCount > maxCount ? maxCount - eSize : onceCount;
		for(int i = 0 ; i < size; ++i){
			Es.add(create());
		}
	}

	//创建一个元素
	public abstract T create();

	//重置元素
	public abstract void resetE(T E);

	public int taskCount(){
		return taskCount;
	}
	public int uSize(){
		return uSize;
	}

	public String toString()
	{
		String src = "I'm " + hashCode();
		if(taskCount == 0)
		    src += ", isStop With " + taskCount;
		else
			src += ", isStart With " + taskCount;

		src += " tasks , I hava " + Es.size();
		src += " Elements, Used " + uSize + "Elements";
		return src;
	}
}
