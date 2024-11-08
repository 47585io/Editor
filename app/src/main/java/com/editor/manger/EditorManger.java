package com.editor.manger;
import java.util.*;

/**
 * 主进程和子进程都加载同一个文件，并以相同的url标识它
 * 主进程的编辑器文本更改时，通知子进程修改那边的内容
 * 要保存文件时，可以向子进程发送保存文本的请求，文本的保存操作将由子进程完成
 * 类似于这样的耗时操作都可以通过这样的方法从主进程中分离
 * 子进程通常是不断监听主进程的下一条消息并执行
 * 主进程的UI虽不会卡死，但也不会立即更新，因为在子进程未执行完请求前不会发送响应的通知
 */
/**
 * 批处理请求
 * 服务端可以异步执行多个请求，只要它们不会相互干扰，例如多个读操作
 * 另外，服务端返回的响应的顺序可以不按请求顺序排列，只要不对结果产生影响
 */
public class EditorManger
{
	private int modifyVersion;
	private Stack<Token> mLast;
	private Stack<Token> mNext;
	
	private int mEditorId;
	
	
	/**
	 * 服务端返回了一个修改的解析结果，该结果对应着指定版本的修改
	 * 客户端应检查该解析结果中的某部分是否仍然有效，并尽可能设置
	 */
	private void responseModify(int oldStart, int oldEnd, int newStart, int newEnd, int version)
	{
		//遍历修改栈，向前回退到可以设置的位置
		boolean isActive = true;
		int nowVersion = modifyVersion;
		while(isActive && version++ != nowVersion){
			Token token = getToken(version);
			//原来的位置在现在的哪里
			isActive = checkRangeActive(0, 0, null);
		}
		
		//如果范围已经失效则取消设置
		if(isActive){
			applyModify(oldStart, oldEnd, newStart, newEnd);
		}
	}
	
	private void applyModify(int oldStart, int oldEnd, int newStart, int newEnd){
		
	}
	
	private Token getToken(int version){
		return null;
	}
	private boolean checkRangeActive(int start, int end, Token token){
		return false;
	}
	private int updatePoint(int point, Token token){
		//
		return 0;
	}
	
	private static class Token
	{
		//private int version;
		private int start;
		private int end;
		private CharSequence text;
	}
}
