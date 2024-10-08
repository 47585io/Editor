package com.editor;

import android.app.*;
import android.os.*;
import android.text.*;
import android.util.*;
import com.editor.view.*;
import java.io.*;
import android.graphics.drawable.*;
import java.util.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import android.content.res.*;
import com.editor.base.files.*;
import com.editor.base.adapter.*;
import android.widget.AdapterView.*;
import com.parser.javaparser.*;
import com.network.server.base.*;
import com.parser.javaparser.base.*;
import com.parser.javaparser.base.Token.*;
import java.util.concurrent.*;

/*
 " SpiltLayout在拖动轴时会重布局，此时ViewPager子元素的大小改变了，但是它的滚动没变

*/

public class WorkBench extends Activity implements Runnable
{
	private AbFileHandler fileHandler;
	private AbEditorPageHandler pageHandler;
	
	private Spinner mEditorList;
	private ListView mFileListView;
	private ViewPager mEditorPages;
	
	private ExecutorService mExecutorService;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		//getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		runOnUiThread(this);
    }

	@Override
	public void run()
	{
		setContentView(R.layout.workbench);
		fileHandler = new AbFileHandler();
		pageHandler = new AbEditorPageHandler();
		
		mExecutorService = Executors.newCachedThreadPool();
		
		mEditorList = findViewById(R.id.EditorList);
		mEditorList.setAdapter(new ArrayAdapter(this, R.layout.text_list_item, R.id.list_item_text));
		mEditorList.setOnItemSelectedListener(new OnItemSelectedListener(){

				@Override
				public void onItemSelected(AdapterView<?> p1, View p2, int p3, long p4)
				{
					mEditorPages.tabPage(p3);
				}

				@Override
				public void onNothingSelected(AdapterView<?> p1)
				{
					// TODO: Implement this method
				}
			});
		
		ViewPager p = findViewById(R.id.DownBar);
		mFileListView.setDivider(null);
		p.addPage(new ViewPager.PageData(mFileListView, "", null));
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		/*if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE){
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
								 WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getWindow().getDecorView().setSystemUiVisibility(
				View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}*/
		super.onConfigurationChanged(newConfig);
	}
	
	public void test(Editable text){
		Random rand = new Random();
		for(int i = 0; i < text.length(); ++i){
			int color = rand.nextInt() | 0xff000000;
			text.setSpan(new ForegroundColorSpan(color), i, i+1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}
	
	private class AbEditorPageHandler implements ViewPager.onTabPageListener
	{
		public AbEditorPageHandler(){
			mEditorPages = findViewById(R.id.EditorPager);
			mEditorPages.setOnTabPageListener(this);
		}
		
		@Override
		public void onTabPage(int i)
		{
			if(mEditorList.getSelectedItemPosition() != i){
				mEditorList.setSelection(i);
			}
			mEditorPages.getChildAt(i).requestFocus();
		}
		
		public void commitFile(final File file)
		{
			final String label = file.getPath();
			int index = mEditorPages.findViewByLabel(label);
			if(index < 0){
				final Editor editor = creatEditor(file);
				ArrayAdapter<String> adapter = (ArrayAdapter) mEditorList.getAdapter();
				adapter.add(file.getName());
				adapter.notifyDataSetChanged();
				mEditorPages.addPage(new ViewPager.PageData(editor, label, null));
			}else{
				mEditorPages.tabPage(index);
			}
		}
		
		private Editor creatEditor(File file)
		{
			Editor editor = new Editor(WorkBench.this);
			byte[] bs = new byte[(int)file.length()];
			try{
				FileInputStream input = new FileInputStream(file);
				input.read(bs);
			}
			catch (IOException e){}

			String code = new String(bs);
			editor.setText(code);
			Editable text = editor.getText();
			colorize(code, text);
			//text.setSpan(new BackgroundColorSpan(0xffff0000), text.length() - 100, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			//test(editor.getText());
			
			return editor;
		}
		
		//移动到EditorManger
		public void colorize(String code, Editable editor)
		{
			List<Token> his = JavaParser.parser(code);
			for(Token hi : his){
				int color = color(hi.type);
				if (color != 0)
					editor.setSpan(new ForegroundColorSpan(color), hi.start, hi.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}

		public int color(int type)
		{
			int color = 0;
			switch(type){
				case TokenType.COMMENT:
					color = 0xff646b77;
					break;
				case TokenType.STRING_LITERAL:
				case TokenType.CHAR_LITERAL:
					color = 0xff98c379;
					break;
				case TokenType.OPERATOR:
					color = 0xff56b6c2;
					break;
				case TokenType.NUMBER:
				case TokenType.CONSTANT:
					color = 0xffd19a66;
					break;
				case TokenType.KEYWORD:
					color = 0xffc678dd;
					break;
				case TokenType.VARIABLE:
					color = 0xffe06c75;
					break;
				case TokenType.FUNCTION:
					color = 0xff61afef;
					break;
				case TokenType.TYPE:
				case TokenType.OBJECT:
					color = 0xffe5c07b;
					break;
			}
			return color;
		}
		
	}
	
	private class AbFileHandler implements FileList.FileChangeListener, OnItemClickListener
	{
		private FileList mFileList;
		private final int listItemOffset = 1;
		
		public AbFileHandler(){
			mFileList = new FileList();
			mFileList.setFileChangeListener(this);
			mFileListView = new ListView(WorkBench.this);
			mFileListView.setOnItemClickListener(this);
			mFileList.refreshData();
		}
		
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			File file = mFileList.selectedFile(position - listItemOffset);
			if(file.isFile() && file.canRead()){
				pageHandler.commitFile(file);
			}
		}
		
		@Override
		public void onFilesRefreshed(File[] files)
		{
			WordAdapter adapter = new WordAdapter(WorkBench.this, R.layout.icon_text_list_item);
			adapter.addItem(new wordIcon(R.drawable.folder_open, ".."));
			for(int i = 0; i < files.length; ++i){
				adapter.addItem(new wordIcon(getFileIcon(files[i]), files[i].getName()));
			}
			mFileListView.setAdapter(adapter);
			
		}

		@Override
		public void onFileCreated(int index, File file)
		{
			WordAdapter adapter = (WordAdapter) mFileListView.getAdapter();
			adapter.addItem(index + listItemOffset, new wordIcon(getFileIcon(file), file.getName()));
			adapter.notifyDataSetChanged();
		}

		@Override
		public void onFileDeleted(int index, File file)
		{
			WordAdapter adapter = (WordAdapter) mFileListView.getAdapter();
			adapter.removeItem(index + listItemOffset);
			adapter.notifyDataSetChanged();
		}

		@Override
		public void onFileRenamed(int index, File file)
		{
			WordAdapter adapter = (WordAdapter) mFileListView.getAdapter();
			wordIcon item = adapter.getItem(index + listItemOffset);
			item.set(getFileIcon(file), file.getName());
			adapter.notifyDataSetInvalidated();
		}
		
		private int getFileIcon(File file)
		{
			if(file.isDirectory()){
				return R.drawable.folder;
			}
			int icon;
			String name = file.getName();
			int suffixStart = name.lastIndexOf('.');
			String suffix = suffixStart < 0 ? "" : name.substring(suffixStart);
			switch(suffix){
				case ".java":
					icon = R.drawable.file_type_java;
					break;
				case ".txt":
					icon = R.drawable.file_type_txt;
					break;
				default: icon = R.drawable.file_type_unknown;
			}
			return icon;
		}
	}
}
