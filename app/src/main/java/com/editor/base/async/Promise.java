package com.editor.base.async;
import java.util.*;

public class Promise<T>
{
	private T value;
	private boolean isResolved;
	private List<Callback<T>> callbacks;

	public Promise(){
		callbacks = new ArrayList<>();
	}
	public Promise(T value){
		this.value = value;
		this.isResolved = true;
	}

	public boolean isResolved(){
		return isResolved;
	}
	public T getValue(){
		return value;
	}

	//解决Promise并调用所有注册的回调
	public void resolve(T result)
	{
		if(!isResolved) {
            this.value = result;
            this.isResolved = true;
            for(Callback<T> callback : callbacks) {
                callback.resolve(result);
            }
        }
	}

	//注册一个回调函数
	public Promise<T> then(final Callback<T> callback)
	{
		final Promise<T> nextPromise = new Promise<>();
		if(isResolved){
			callback.resolve(value);
			nextPromise.resolve(value);
		}
		else{
			callbacks.add(new Callback<T>(){
					public void resolve(T result){
						callback.resolve(result);
						nextPromise.resolve(result);
					}
				});
		}
		return nextPromise;
	}

	public static interface Callback<T>
	{
		public void resolve(T result)
	}
}
