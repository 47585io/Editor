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
import com.parser.javaparser.base.*;
import com.parser.javaparser.base.Token.*;
import java.util.concurrent.*;
import com.editor.base.async.*;
import com.editor.text.*;

/*
 * SpiltLayout在拖动轴时会重布局，此时ViewPager子元素的大小改变了，但是它的滚动没变
 * ViewPager和SpiltLayout必须合理拦截事件，比如点击长按不要拦截
 * ViewPager和SpiltLayout都需要检查手势才能拦截，其中一项不满足立刻中断(柔性的检查就是每次都检查，只要满足了就拦截)
 * 
 * 为什么不禁状态栏？因为这会导致Title永远无法完全收起，很丑
 * 为什么不禁导航栏？因为编辑时软键盘会重新抬起导航栏，没用
 * 而且有时候禁用状态栏和导航栏后不好恢复，不同手机设计不同，有的手机禁掉后直接空出一片黑，还不如设置同样的主题色
 * 而且又不是游戏，禁用状态栏和导航栏后不好操作，应该程序也需要监听返回键之类的
 *
 * 有些编辑器是标题加可滑动侧边栏，我是仿AIDE的标题加可滑动底部栏，因为EditorPager会左右滑动，不建议把这个手势抢了
 * 侧边栏和底部栏本质上是一样的，只是切换页面方式不一样，侧边栏内容可仿vscode的左侧一列按扭对应一个个页面
 * 底部栏是使用一个ViewPager来滑动每个页面，每个页面标题需列在顶部
 * 最后为了节省空间，我额外让标题也可滑动
 */
 
public class WorkBench extends Activity implements Runnable
{
	private SplitterLayout mRoot;
	
	private RelativeLayout mTitle;
	private Spinner mEditorList;
	private LinearLayout mButtonBar;
	
	private SplitterLayout mBody;
	private ViewPager mEditorPages;
	private ViewPager mDownBar;
	
	private TitleManger titleManger;
	private AbFileHandler fileHandler;
	private AbEditorPageHandler pageHandler;
	
	private ListView mFileListView;
	private Handler mHandler;
	private ExecutorService mExecutorService;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mHandler = new Handler();
		mExecutorService = Executors.newCachedThreadPool();
		runOnUiThread(this);
		
		StringBuilder sb = new StringBuilder();
		while(sb.length() < 5000){
			sb.append("invalid");
		}
		EditableList sp = new EditableList(sb);
		int[] spanStarts = new int[]{0};
		int[] spanEnds = new int[]{5005};
		setSpans(sp, spanStarts, spanEnds);
		String log = nextSpans(sp, -1, 5006, CharacterStyle.class);
		Log.w("nextSpans", log);
    }
	
	private static void setSpans(Spannable sp, int[] spanStarts, int[] spanEnds){
		for(int i = 0; i < spanStarts.length; ++i){
			sp.setSpan(new Object(), spanStarts[i], spanEnds[i], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}
	
	private String nextSpans(Spanned sp, int start, int limit, Class kind)
	{
		StringBuilder sb = new StringBuilder();
		int next = start;
		do{
			next = sp.nextSpanTransition(next, limit, kind);
			sb.append(next);
			sb.append(", ");
		}while(next < limit);
		return sb.toString();
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mExecutorService.shutdown();
	}

	@Override
	public void run()
	{
		initViewTree();
		fileHandler = new AbFileHandler();
		pageHandler = new AbEditorPageHandler();
		titleManger = new TitleManger();
		
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
		
		
		mFileListView.setDivider(null);
		mDownBar.addPage(new ViewPager.PageData(mFileListView, "FileList", null));
		
		mRoot.requestLayout();
		mRoot.invalidate();
	}
	
	private void initViewTree()
	{
		setContentView(R.layout.workbench);
		mRoot = findViewById(R.id.WorkBench);
		
		mTitle = mRoot.findViewById(R.id.Title);
		mEditorList = mTitle.findViewById(R.id.EditorList);
		mButtonBar = mTitle.findViewById(R.id.ButtonBar);
		
		mBody = mRoot.findViewById(R.id.Body);
		mEditorPages = mBody.findViewById(R.id.EditorPager);
		mDownBar = mBody.findViewById(R.id.DownBar);
	}
	
	private void configTitle()
	{
		
	}
	
	private void creatDownBarPages()
	{
		
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
		titleManger.changeOrenitation();
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
				Promise<Editor> promise = createEditorS(file);
				promise.then(new Promise.Callback<Editor>(){

						@Override
						public void resolve(Editor editor)
						{
							ArrayAdapter<String> adapter = (ArrayAdapter) mEditorList.getAdapter();
							adapter.add(file.getName());
							adapter.notifyDataSetChanged();
							try{
								mEditorPages.addPage(new ViewPager.PageData(editor, label, null));
							}catch(Error e){}
							Toast.makeText(WorkBench.this, label+" loaded", 0).show();
						}
					});
			}else{
				mEditorPages.tabPage(index);
			}
		}
		
		private Promise<Editor> createEditorS(final File file)
		{
			final Promise promise = new Promise<>();
			mExecutorService.submit(new Runnable(){

					@Override
					public void run()
					{
						try{
							final Editor editor = createEditor(file);
							mHandler.post(new Runnable(){

									@Override
									public void run()
									{
										//editor.refresh();
										promise.resolve(editor);
									}
								});
						}catch(Exception E){
							E.printStackTrace();
						}
					}
				});
			return promise;
		}
		
		private Editor createEditor(File file)
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
			final int size = his.size();
			Log.w("colorize", "token parsed, size " + his.size());
			
			int once = size / 100;
			once = once == 0 ? 1 : once;
			int last = 0;
			for(int i = 0; i < size; ++i)
			{
				int now = i / once;
				if (last != now){
					last = now;
					Log.w("colorize", "span seted size " + i + ", span update at " + now + "%");
				}
				
				Token hi = his.get(i);
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
	
	private class TitleManger
	{
		private boolean change;
		private int titleHeight = -1;
		private int oldParentHeight = -1;
		private float oldParentBili;
		
		public TitleManger(){
			View tilte = findViewById(R.id.Title);
			tilte.measure(0, 0);
			titleHeight = tilte.getMeasuredHeight();
			mRoot.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){

					@Override
					public void onGlobalLayout()
					{
						int measure = mRoot.getMeasuredHeight();
						onMeasure(measure);
					}
				});
		}
		
		public void onMeasure(int height)
		{
			if(oldParentHeight == -1){
				oldParentHeight = height;
				oldParentBili = (float)titleHeight / height;
				mRoot.setSeparatorRange(0, oldParentBili);
				mRoot.setSeparator(oldParentBili);
			}else if(change){
				float range = (float)titleHeight / height;
				float self = oldParentHeight * oldParentBili;
				float bili = self / height;
				mRoot.setSeparatorRange(0, range);
				mRoot.setSeparator(bili);
				change = false;
			}else{
				oldParentHeight = height;
				oldParentBili = mRoot.getSeparator();
			}
		}
		
		public void changeOrenitation(){
			change = true; 
		}
	}
	
	private class DownBarPageCreator
	{
		private ListView mFileListView;
		
		
	}
	
}
