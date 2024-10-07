package com.network.client;
import android.os.*;
import android.text.*;
import com.network.client.base.*;
import com.network.server.*;
import com.network.server.base.*;
import com.editor.view.*;
import java.util.*;
import java.util.concurrent.*;

public class EditorManger implements TextWatcher
{
	private ResponseParser mResponseParser;
	private LanguageServer mLanguageServer;
	
	private Editor mCurrentEditor;
	private int modifyVersion;
	private int mPrivateFlags;
	private Stack<token> mLast, mNext;
	
	private Future[] taskFutures;
	private ThreadPoolExecutor mThreadPool;
	private static final Handler mHandler = new Handler(Looper.getMainLooper());
	
	private static final int COLORIZE = 0;
	private static final int FORMAT = 1;
	private static final int COMPLETE = 2;
	
	private static final int ColorizeMask = 1;
	private static final int FormatMask = 2;
	private static final int CompleteMask = 4;
	private static final int URMask = 8;
	
	public EditorManger(LanguageServer server, Editor editor){
		mResponseParser = new ResponseParser();
		mLanguageServer = server;
		mCurrentEditor = editor;
		taskFutures = new Future[3];
		editor.setTextWatcher(this);
	}
	public void setRunTaskInThread(ThreadPoolExecutor pool){
		mThreadPool = pool;
	}
	
	public void beforeTextChanged(CharSequence text, int start, int before, int after)
	{
		modifyVersion++;
		//取消上次任务
		
		//将本次修改保存为token
		if((mPrivateFlags & URMask) != URMask)
		{
			token token = null;
			if(mLast.size() > 0){
				token = mLast.peek();
			}

			//文本修改前，判断一下本次修改位置是否与上次token相连
			if(token==null || !expandToken(text,start,before,after,token)){
				//如果不相连，制作一个token，并保存到mLast
				token = makeToken(text,start,before,after);
				mLast.push(token);
			}
		}
	}

	public void onTextChanged(CharSequence text, int start, int before, int after)
	{
		if(after > 0){
			startColorize(start, start + after);
			startComplete(start + after);
		}
	}

	public void afterTextChanged(Editable p1){}
	
	private void startColorize(final int start, final int end)
	{
		Future currentTask = taskFutures[COLORIZE];
		if(currentTask != null && !currentTask.isDone() && !currentTask.isCancelled()){
			//上个任务没有执行完成，取消继续执行
			currentTask.cancel(true);
		}
		
		if(mThreadPool != null){
			//记录本次的版本号
			final int version = modifyVersion;
			Runnable total = new Runnable(){
				public void run()
				{
					final Highlight[] response = mLanguageServer.colorize(mCurrentEditor.getText(), start, end);
					mHandler.post(new Runnable(){
						public void run(){
							if(version == modifyVersion){
								//最后提交时校验版本号，因为在子线程中执行时文本可能已经修改
								responseColorize(response);
							}
						}
					});
				}
			};
			taskFutures[COLORIZE] = mThreadPool.submit(total);
		}else{
			responseColorize(mLanguageServer.colorize(mCurrentEditor.getText(), start, end));
		}
	}
	private void startFormat(){
		
	}
	private void startComplete(final int index)
	{
		Future currentTask = taskFutures[COMPLETE];
		if(currentTask != null && !currentTask.isDone() && !currentTask.isCancelled()){
			//上个任务没有执行完成，取消继续执行
			currentTask.cancel(true);
		}

		if(mThreadPool != null){
			//记录本次的版本号
			final int version = modifyVersion;
			Runnable total = new Runnable(){
				public void run()
				{
					final CompletionItem[] response = mLanguageServer.complete(mCurrentEditor.getText(), index);
					mHandler.post(new Runnable(){
						public void run(){
							if(version == modifyVersion){
								//最后提交时校验版本号，因为在子线程中执行时文本可能已经修改
								responseComplete(response);
							}
						}
					});
				}
			};
			taskFutures[COMPLETE] = mThreadPool.submit(total);
		}else{
			responseComplete(mLanguageServer.complete(mCurrentEditor.getText(), index));
		}
	}
	
	private void responseColorize(Highlight[] response){
		
	}
	private void responseFormat(CharSequence response){
		
	}
	private void responseComplete(CompletionItem[] response){
		
	}
	
	public void uedo(){
		
	}
	public void redo(){
		
	}
	
	/* 根据文本变化来制作一个新的token */
	private token makeToken(CharSequence text, int start, int before, int after){
		return new token(start, start+after, TextUtils.substring(text, start, start+before));
	}
	/* 检查token是否可以扩展，如果可以就扩展token的范围 */
	private boolean expandToken(CharSequence text, int start, int before, int after, token token)
	{
		return false;
	}
	/* 应用token到文本，并返回相反操作的token */
	private token applyToken(Editable editor, token token){
		CharSequence src = TextUtils.substring(editor, token.start, token.end);
		editor.replace(token.start, token.end, token.src);
		return new token(token.start, token.start + token.src.length(), src);
	}
}
