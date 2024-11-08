package com.editor.view;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.util.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.editor.*;
import com.editor.text.*;
import java.util.*;
import android.graphics.drawable.*;
import android.animation.*;

public class Editor extends View
{
	private TextPaint mPaint;
	private EditableList mText;
	private BlockLayout mLayout;
	private myInputConnection mInput;

	private Cursor mCursor;
	private ScrollBars mScrollBars;
	private ChangeWatcher mChangeWatcher;

	/* defAttrs */
	private int textColor;
	private float textSize;
	private int cursorColor;
	private int selectionColor;
	private int scrollBarColor;
	private int highlightScrollBarColor;
	private int highlightLineColor;
	private int TabSize;
	private int lineNumberColor;
	private float lineSpacing;
	
	public Editor(Context cont){
		super(cont);
		init(cont, null);
		config();
	}
	protected void init(Context cont, AttributeSet attrs)
	{
		initAttrs(cont, attrs);
		mPaint = new TextPaint();
		mInput = new myInputConnection();
		mCursor = new Cursor(cont);
		mScrollBars = new ScrollBars();
		mChangeWatcher = new ChangeWatcher();
		setText("", 0, 0);
		
		getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
				@Override
				public void onGlobalLayout(){
					UILoad(getContext());
					getViewTreeObserver().removeOnGlobalLayoutListener(this);
				}
			});
	}
	private void initAttrs(Context cont, AttributeSet attrs)
	{
		TypedArray array;
		if(attrs != null){
			array = cont.obtainStyledAttributes(attrs, R.styleable.Editor);
		}else{
			array = cont.getTheme().obtainStyledAttributes(R.styleable.Editor);
		}
		textColor = array.getColor(R.styleable.Editor_textColor, textColor);
		textSize = array.getDimension(R.styleable.Editor_textSize, textSize);
		cursorColor = array.getColor(R.styleable.Editor_cursorColor, cursorColor);
		selectionColor = array.getColor(R.styleable.Editor_selectionColor, selectionColor);
		scrollBarColor = array.getColor(R.styleable.Editor_scrollBarColor, scrollBarColor);
		highlightScrollBarColor = array.getColor(R.styleable.Editor_highlightScrollBarColor, highlightScrollBarColor);
		highlightLineColor = array.getColor(R.styleable.Editor_highlightLineColor, highlightLineColor);
		TabSize = array.getInteger(R.styleable.Editor_TabSize, TabSize);
		lineNumberColor = array.getColor(R.styleable.Editor_lineNumberColor, lineNumberColor);
		lineSpacing = array.getFloat(R.styleable.Editor_lineSpacing, lineSpacing);
	}
	protected void config()
	{
		configPaint(mPaint);
		//setHorizontalScrollBarEnabled(true);
		//setVerticalScrollBarEnabled(true);
		//设置视图在任何情况下都可以获取焦点
		setFocusable(true);
		setFocusableInTouchMode(true);
		//设置在获取焦点时不用额外绘制高亮的矩形区域
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26
			setDefaultFocusHighlightEnabled(false);
		}
	}
	private void configPaint(TextPaint paint)
	{
		paint.setTextSize(textSize); 
		paint.setColor(textColor); 
		paint.setTypeface(Typeface.MONOSPACE);
		//:;!?-,. 这些字符前面出现\t时测量出问题
	}
	private void UILoad(Context context){
		mCursor.UIStart();
	}
	
	public void setText(CharSequence text){
		setText(text, 0, text.length());
	}
	public void setText(CharSequence text,int start,int end)
	{
		mText = new EditableList(text,start,end);
		mText.setTextWatcher(mChangeWatcher);
		if(mLayout != null){
			int tabSize = mLayout.getTabSize();
			int lineColor = mLayout.getLineNumColor();
			float lineSpacing = mLayout.getLineSpacing();
			mLayout = new BlockLayout(mText, mPaint, tabSize, lineColor, lineSpacing);
		}else{
			mLayout = new BlockLayout(mText, mPaint, TabSize, lineNumberColor, lineSpacing);
		}
		//scrollTo(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}
	
	public Editable getText(){
		return mText;
	}
	public TextPaint getPaint(){
		return mPaint;
	}
	public BaseLayout getLayout(){
		return mLayout;
	}
	
	@Override
	public boolean onCheckIsTextEditor(){
		return true;
	}
	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs){
		//返回与输入法建立连接的InputConnection
		return mInput;
	}

	public void setInputEnabled(boolean enabled){
		mInput.InputEnabled = enabled;
	}
	/* 让输入法与指定的View建立连接，并打开软键盘，View必须可以获取焦点 */
	final public static void openInputor(final Context context, final View editText)
	{
		//当View获取焦点时，系统会调用它的onCreateInputConnection与输入法建立连接
		//然后通过InputMethodManager来显示软键盘，进行实际的输入
		editText.requestFocus();
		InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.showSoftInput(editText, 0);
	}
	final public static void closeInputor(Context context, View editText)
	{
		editText.requestFocus();
		InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
	}

	/* 输入法想要输入时，会调用我们的某些方法，此时我们自动修改文本 */
	private final class myInputConnection implements InputConnection
	{
		/* 是否启用输入 */
		private boolean InputEnabled = true;
		
		public boolean sendKeyEvent(KeyEvent event)
		{
			//如果输入法要主动输入或删除，不应该调用此方法
			int keyCode = event.getKeyCode();
			int action = event.getAction();
			//手指抬起
			if(action == KeyEvent.ACTION_UP)
			{
				switch(keyCode)
				{
					case KeyEvent.KEYCODE_ENTER:
						//如果是一个换行键，无论如何都提交换行
						commitText(String.valueOf('\n'),0);
						break;
					case KeyEvent.KEYCODE_DEL:
						//如果是一个删除键，无论如何都删除字符
						deleteSurroundingText(1,0);
						break;
				}	
			}
			return true;
		}

		public boolean commitText(CharSequence text, int newCursorPosition)
		{
			if(InputEnabled){
				//根据要输入的文本，进行输入
				onInputContent(text,newCursorPosition,0,0);
			}
			return true;
		}

		public boolean deleteSurroundingText(int beforeLength, int afterLength)
		{
			if(InputEnabled){
				//准备删除字符
				onInputContent(null,0,beforeLength,afterLength);	
			}
			return true;
		}

		public boolean commitCompletion(CompletionInfo text){
			//用户选择了输入法的提示栏的一个单词，我们需要获取并插入
			return commitText(text.getText(),0);
		}	

		/* 以下函数懒得实现了 */
		public CharSequence getTextBeforeCursor(int p1, int p2){
			return null;
		}
		public CharSequence getTextAfterCursor(int p1, int p2){
			return null;
		}
		public CharSequence getSelectedText(int p1){
			return null;
		}
		public int getCursorCapsMode(int p1){
			return 0;
		}
		public ExtractedText getExtractedText(ExtractedTextRequest p1, int p2){
			return null;
		}
		public boolean deleteSurroundingTextInCodePoints(int p1, int p2){
			return false;
		}
		public boolean setComposingText(CharSequence p1, int p2){
			return false;
		}
		public boolean setComposingRegion(int p1, int p2){
			return false;
		}
		public boolean finishComposingText(){
			return false;
		}
		public boolean commitCorrection(CorrectionInfo p1){
			return false;
		}
		public boolean setSelection(int p1, int p2){
			return false;
		}
		public boolean performEditorAction(int p1){
			commitText("\n", 0);
			return false;
		}
		public boolean performContextMenuAction(int p1){
			return false;
		}
		public boolean clearMetaKeyStates(int p1){
			return false;
		}
		public boolean reportFullscreenMode(boolean p1){
			return false;
		}
		public boolean performPrivateCommand(String p1, Bundle p2){
			return false;
		}
		public boolean requestCursorUpdates(int p1){
			return false;
		}
		public boolean commitContent(InputContentInfo p1, int p2, Bundle p3){
			return false;
		}
		public boolean beginBatchEdit(){
			return false;
		}	
		public boolean endBatchEdit(){
			return false;
		}
		public Handler getHandler(){
			return Editor.this.getHandler();
		}
		public void closeConnection(){}
	}

	/* 输入内容时调用 */
	protected void onInputContent(CharSequence text, int newCursorPosition, int before, int after){
		mCursor.sendInputContent(text,newCursorPosition,before,after);
	}
	
	private static final long CURSOR_BLINK_INTERVAL = 500; // 光标闪烁间隔
	
	private final class Cursor
	{
		public int selectionStart, selectionEnd;
		public Path cursorPath;
		public Rect lineBounds;
		
		private boolean isCursorVisible = false;
		private Runnable cursorBlinkRunnable;
		private boolean isHandleVisible = false;
		private Runnable handleGoneRunnable;
		
		private Drawable leftHandle;
		private Drawable rightHandle;
		private Drawable middleHandle;
		
		private Drawable touchAt;
		private float currentX, currentY;
		
		public Cursor(Context context)
		{
			selectionStart = selectionEnd = 0;
			cursorPath = new Path();
			lineBounds = new Rect();
			
			Resources res = context.getResources();
			leftHandle = res.getDrawable(R.drawable.text_select_handle_left);
			rightHandle = res.getDrawable(R.drawable.text_select_handle_right);
			middleHandle = res.getDrawable(R.drawable.text_select_handle_middle);
			
			cursorBlinkRunnable = new Runnable() {
				@Override
				public void run() {
					isCursorVisible = !isCursorVisible;
					invalidate(); // 重新绘制
					postDelayed(this, CURSOR_BLINK_INTERVAL);
				}
			};
			
			handleGoneRunnable = new Runnable() {
				@Override
				public void run(){
					isHandleVisible = false;
					invalidate();
				}
			};
		}
		
		public void UIStart(){
			post(cursorBlinkRunnable);
		}
		private void initDrawable(BitmapDrawable drawable, int dstSize)
		{
			
		}
		
		public void setSelection(int start, int end)
		{
			start = checkOffset(start);
			end = checkOffset(end);
			if(start != selectionStart || end != selectionEnd){
				selectionStart = start;
				selectionEnd = end;
				mChangeWatcher.onSelectionChanged(mText, start, end);
			}
			
			//停止闪烁，进入等待
			if(selectionStart == selectionEnd){
				isCursorVisible = true;
				isHandleVisible = true;
				getHandler().removeCallbacks(cursorBlinkRunnable);
				getHandler().removeCallbacks(handleGoneRunnable);
				postDelayed(cursorBlinkRunnable, CURSOR_BLINK_INTERVAL * 2);
				postDelayed(handleGoneRunnable, CURSOR_BLINK_INTERVAL * 6);
			}
		}
		
		public void sendInputContent(CharSequence text, int newCursorPosition, int before, int after)
		{
			if(text != null){
				mText.replace(selectionStart, selectionEnd, text);
			}
			else if(before > 0 || after > 0){
				int len = mText.length();
				before = before > selectionStart ? selectionStart : before;
				after = selectionEnd+after > len ? len-selectionEnd : after;
				mText.delete(selectionStart-before, selectionEnd+after);
			}
		}
		
		public boolean isTouchCursorHandle(float x, float y)
		{
			float dx = x + getScrollX();
			float dy = y + getScrollY();
			int sx = (int)(dx / scaleFactor);
			int sy = (int)(dy / scaleFactor);
			int cursor = -1;
			if(selectionStart != selectionEnd){
				if(leftHandle.getBounds().contains(sx, sy)){
					touchAt = leftHandle;
					cursor = selectionStart;
				}else if(rightHandle.getBounds().contains(sx, sy)){
					touchAt = rightHandle;
					cursor = selectionEnd;
				}
			}else if(isHandleVisible){
				if(middleHandle.getBounds().contains(sx, sy)){
					touchAt = middleHandle;
					cursor = selectionStart;
				}
			}
			if(cursor > -1){
				currentX = mLayout.getOffsetHorizontal(cursor);
				currentY = mLayout.getOffsetVertical(cursor) + mLayout.getLineHeight()/2;
				return true;
			}
			return false;
		}
		
		public void handleTouchEvent(float dx, float dy)
		{
			currentX += (dx / scaleFactor);
			currentY += (dy / scaleFactor);
			int offset = mLayout.getOffsetForPosition(currentX, currentY);
			if(touchAt == middleHandle){
				setSelection(offset, offset);
			}else if(touchAt == leftHandle){
				setSelection(offset, selectionEnd);
			}else if(touchAt == rightHandle){
				setSelection(selectionStart, offset);
			}
		}
		
		public void drawBackground(Canvas canvas, Paint paint)
		{
			updatePath(canvas);
			int saveColor = paint.getColor();
			if(selectionStart == selectionEnd){
				paint.setColor(highlightLineColor);
				canvas.drawRect(lineBounds, paint);
			}else{
				paint.setColor(selectionColor);
				canvas.drawPath(cursorPath, paint);
			}
			paint.setColor(saveColor);
		}
		
		public void drawForeground(Canvas canvas, Paint paint)
		{
			int saveColor = paint.getColor();
			if(selectionStart == selectionEnd && isCursorVisible){
				paint.setColor(cursorColor);
				canvas.drawPath(cursorPath, paint);
			}
			paint.setColor(saveColor);
		}
		
		public void drawCursorHandle(Canvas canvas)
		{
			if(selectionStart == selectionEnd && isHandleVisible){
				int sx = (int) (mLayout.getOffsetHorizontal(selectionStart) * scaleFactor);
				int sy = (int) (mLayout.getOffsetVertical(selectionStart) * scaleFactor);
				int width = mLayout.getLineHeight();
				middleHandle.setBounds(sx - width / 2, sy + width, sx + width / 2, sy + width * 2);
				middleHandle.draw(canvas);
			}
			else if(selectionStart < selectionEnd){
				int sx = (int) (mLayout.getOffsetHorizontal(selectionStart) * scaleFactor);
				int sy = (int) (mLayout.getOffsetVertical(selectionStart) * scaleFactor);
				int ex = (int) (mLayout.getOffsetHorizontal(selectionEnd) * scaleFactor);
				int ey = (int) (mLayout.getOffsetVertical(selectionEnd) * scaleFactor);
				int width = mLayout.getLineHeight();
				leftHandle.setBounds(sx - width, sy + width, sx, sy + width * 2);
				rightHandle.setBounds(ex, ey + width, ex + width, ey + width * 2);
				leftHandle.draw(canvas);
				rightHandle.draw(canvas);
			}
		}
		
		public void updatePath(Canvas canvas)
		{
			Rect rect = lineBounds;
			if(canvas.getClipBounds(rect))
			{
				int firstLine = mLayout.getLineForVertical(rect.top);
				int lastLine = mLayout.getLineForVertical(rect.bottom);
				int start = mLayout.getLineStart(firstLine);
				int end = mLayout.getLineEnd(lastLine);
				
				cursorPath.rewind();
				lineBounds.setEmpty();
				
				//calc overlap range
				if(selectionStart <= end && selectionEnd >= start){
					if(selectionStart == selectionEnd){
						mLayout.getCursorPath(selectionStart, cursorPath);
						mLayout.getLineBounds(mLayout.getLineForVertical(getCursorOffsetVertical()), lineBounds);
					}else{
						int overlapStart = Math.max(start, selectionStart);
						int overlapEnd = Math.min(end, selectionEnd);
						mLayout.getSelectionPath(overlapStart, overlapEnd, cursorPath);
					}
				}
			}
		}
		
		public int getCursorOffsetVertical()
		{
			int follow = selectionEnd;
			if(touchState == SELECT_STATE){
				follow = selectionStart != cursorStart ? selectionStart : selectionEnd;
			}
			return mLayout.getOffsetVertical(follow);
		}
		private int checkOffset(int offset){
			int length = mText.length();
			return offset < 0 ? 0 : (offset > length ? length : offset);
		}
	}
	
	public void setSelection(int start, int end){
		mCursor.setSelection(start, end);
	}
	public void setSelection(int index){
		mCursor.setSelection(index, index);
	}
	public int getSelectionStart(){
		return mCursor.selectionStart;
	}
	public int getSelectionEnd(){
		return mCursor.selectionEnd;
	}
	public float getSelectionX(){
		return mLayout.getOffsetHorizontal(mCursor.selectionEnd) * scaleFactor;
	}
	public float getSelectionY(){
		return mLayout.getOffsetVertical(mCursor.selectionEnd) * scaleFactor;
	}
	public int getOffsetForPosition(float x, float y){
		return mLayout.getOffsetForPosition(x / scaleFactor, y / scaleFactor);
	}
	
	/* 文本变化时调用文本监视器的方法，并更改光标位置 */
	protected void beforeTextChanged(CharSequence text, int start, int before, int after){}

	protected void onTextChanged(CharSequence text, int start, int before, int after){
		int index = start + after;
		mCursor.setSelection(index, index);	
	}
	protected void afterTextChanged(Editable text){
		//文本变化后，刷新界面
		invalidate();
	}
	
	protected void onSelectionChanged(CharSequence text, int start, int end)
	{
		//光标移动到超出范围的地方，需要滚动视图
		//默认跟踪选择区域尾部，如果正在触摸选择，则跟踪当前手指位置
		int follow = end; 
		if(touchState == SELECT_STATE){
			follow = start != cursorStart ? start : end;
		}
		followCursor(follow);
		invalidate();
	}
	private void followCursor(int cursor)
	{
		float cursorX = mLayout.getOffsetHorizontal(cursor) * scaleFactor;
		float cursorY = mLayout.getOffsetVertical(cursor) * scaleFactor;
		int sx = getScrollX();
		int sy = getScrollY();
		int width = getWidth();
		int height = getHeight();
		
		int tox = sx;
		int toy = sy;
		final int insets = 100;
		if(cursorX - insets < sx){
			tox = (int)(cursorX - insets);
		}
		else if(cursorX + insets >= sx+width){
			tox = (int)(cursorX + insets - width);
		}
		if(cursorY - insets < sy){
			toy = (int)(cursorY - insets);
		}
		else if(cursorY + insets >= sy+height){
			toy = (int)(cursorY + insets - height);
		}	
		abortScrollTo(tox, toy);
	}
	
	private final class ScrollBars
	{
		public ScrollBar hScrollBar;
		public ScrollBar vScrollBar;
		public Paint scrollBarPaint;
		
		public ScrollBars()
		{
			hScrollBar = new HScrollBar();
			vScrollBar = new VScrollBar();
			scrollBarPaint = new Paint();
		}
		
		public void show(){
			hScrollBar.show();
			vScrollBar.show();
		}
		
		public boolean isTouchScrollBar(int x, int y){
			return hScrollBar.isTouchScrollBar(x, y) || vScrollBar.isTouchScrollBar(x, y);
		}
		public void executeTouchEvent(int dx, int dy){
			if(hScrollBar.isHighLight){
				hScrollBar.executeTouchEvent(dx, dy);
			}else if(vScrollBar.isHighLight){
				vScrollBar.executeTouchEvent(dx, dy);
			}
		}
		
		public void drawScrollBars(Canvas canvas){
			canvas.translate(getScrollX(), getScrollY());
			hScrollBar.drawScrollBar(canvas, scrollBarPaint);
			vScrollBar.drawScrollBar(canvas, scrollBarPaint);
		}
	}
	
	private class ScrollBar
	{
		protected boolean isBold;
		protected boolean isHighLight;
		protected float scrollBarAlpha; 
		protected Rect scrollBarRect;
		protected ValueAnimator fadeOutAnimator;
		
		public ScrollBar()
		{
			scrollBarRect = new Rect();
			fadeOutAnimator = ValueAnimator.ofFloat(1f, 0f);
			fadeOutAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
					@Override
					public void onAnimationUpdate(ValueAnimator p1){
						scrollBarAlpha = (float)p1.getAnimatedValue();
						invalidate();
					}
				});
			fadeOutAnimator.addListener(new Animator.AnimatorListener(){
					@Override
					public void onAnimationStart(Animator p1){}

					@Override
					public void onAnimationEnd(Animator p1){
						clearState();
					}

					@Override
					public void onAnimationCancel(Animator p1){}

					@Override
					public void onAnimationRepeat(Animator p1){}
				});
			fadeOutAnimator.setStartDelay(500);
		}
	
		public void show(){
			scrollBarAlpha = 1f;
			computeBounds();
			if(fadeOutAnimator.isRunning()){
				fadeOutAnimator.cancel();
			}
			fadeOutAnimator.start();
		}
		public void showFixed(){
			
		}
		
		public void computeBounds(){
			
		}
		public void toBold(){
			isBold = true;
			computeBounds();
		}
		public void clearState(){
			isHighLight = isBold = false;
		}
		
		/* 方法计算给定滚动量(offset)在宽度线上的点(point) */
		protected int calcPoint(int range, int extent, int display, int offset){
			//我们算出当前滚动量在实际总滚动量中的比例(offset / (range - display))
			//然后将它映射到宽度线的剩余长度上(display * con * (1 - ext))
			float ext = (float)extent / range;
			float con = (float)offset / (range - display);
			return (int)(display * con * (1 - ext));
		}
		
		/* 方法计算给定滚动条(extent)在宽度线上的长度(len) */
		protected int calcLen(int range, int extent, int display){
			float ext = (float)extent / range;
			return (int)(display * ext);
		}

		/* 方法计算给定宽度线上的点(point)对应的滚动量(offset) */
		protected int calcScroll(int range, int extent, int display, int point){
			//我们算出点在剩余宽度中的比例(point / ((1 - ext) * display))，
			//然后乘上它对应的实际总滚动量(range - display)
			//假设宽度线长为1，滑块卡为0.1，那么剩余0.9，对于点0.9在剩余空间中为0.9 / 0.9 = 1
			float ext = (float)extent / range;
			float bili = point / ((1 - ext) * display);
			return (int)(bili * (range - display));
		}
		
		public void drawScrollBar(Canvas canvas, Paint paint){
			paint.setColor(calcColor());
			canvas.drawRect(scrollBarRect, paint);
		}
		
		protected int calcColor(){
			int color = isHighLight ? highlightScrollBarColor : scrollBarColor;
			int alpha = (int)(Color.alpha(color) * scrollBarAlpha);
			return (color & 0x00FFFFFF) | (alpha << 24);
		}
		
		protected int calcThickness(){
			float bili = isBold ? BOLD_SCROLLBAR_THICKNESS : SCROLLBAR_THICKNESS;
			return (int)(Math.min(getWidth(), getHeight()) * bili);
		}
		
		public boolean isTouchScrollBar(int x, int y){
			isHighLight = false;
			if(isBold && scrollBarRect.contains(x, y)){
				isHighLight = true;
				return true;
			}
			return false;
		}
		public void executeTouchEvent(int dx, int dy){

		}
	}
	
	private class HScrollBar extends ScrollBar
	{
		@Override
		public void computeBounds()
		{
			int width = getWidth(), height = getHeight();
			int thickness = calcThickness();
			int range = computeHorizontalScrollRange();
			int offset = computeHorizontalScrollOffset();
			int extent = computeHorizontalScrollExtent();
			int point = calcPoint(range, extent, width, offset);
			int len = calcLen(range, extent, width);
			scrollBarRect.set(point, height-thickness, point+len, height);
		}

		@Override
		public void executeTouchEvent(int dx, int dy)
		{
			int point = scrollBarRect.left + dx;
			int offset = calcScroll(computeHorizontalScrollRange(), 
			    computeHorizontalScrollExtent(), getWidth(), point);
			scrollTo(getContentLeft()+offset, getScrollY());
		}
	}
	
	private class VScrollBar extends ScrollBar
	{
		@Override
		public void computeBounds()
		{
			int width = getWidth(), height = getHeight();
			int thickness = calcThickness();
			int range = computeVerticalScrollRange();
			int offset = computeVerticalScrollOffset();
			int extent = computeVerticalScrollExtent();
			int point = calcPoint(range, extent, height, offset);
			int len = calcLen(range, extent, height);
			scrollBarRect.set(width-thickness, point, width, point+len);
		}

		@Override
		public void executeTouchEvent(int dx, int dy)
		{
			int point = scrollBarRect.top + dy;
			int offset = calcScroll(computeVerticalScrollRange(),
			    computeVerticalScrollExtent(), getHeight(), point);
			scrollTo(getScrollX(), getContentTop()+offset);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		mScrollBars.show();
	}

	@Override
	protected void onScrollChanged(int sx, int sy, int oldx, int oldy)
	{
		super.onScrollChanged(sx, sy, oldx, oldy);
		if(sx != oldx && canShowHorizontalScrollBar()){
			mScrollBars.hScrollBar.show();
		}
		if(sy != oldy && canShowVerticalScrollBar()){
			mScrollBars.vScrollBar.show();
		}
	}
	
	private final class ChangeWatcher implements TextWatcher, SelectionWatcher
	{
		private ArrayList<TextWatcher> textWatchers;
		private ArrayList<SelectionWatcher> selectionWatchers;

		public ChangeWatcher(){
			textWatchers = new ArrayList<>();
			selectionWatchers = new ArrayList<>();
		}
		
		@Override
		public void onSelectionChanged(CharSequence text, int start, int end)
		{
			Editor.this.onSelectionChanged(text, start, end);
			for(int i = 0; i < selectionWatchers.size(); ++i){
				selectionWatchers.get(i).onSelectionChanged(text, start, end);
			}
		}

		@Override
		public void beforeTextChanged(CharSequence text, int start, int before, int after)
		{
			Editor.this.beforeTextChanged(text, start, before, after);
			for(int i = 0; i < textWatchers.size(); ++i){
				textWatchers.get(i).beforeTextChanged(text, start, before, after);
			}
		}

		@Override
		public void onTextChanged(CharSequence text, int start, int before, int after)
		{
			Editor.this.onTextChanged(text, start, before, after);
			for(int i = 0; i < textWatchers.size(); ++i){
				textWatchers.get(i).onTextChanged(text, start, before, after);
			}
		}

		@Override
		public void afterTextChanged(Editable text)
		{
			Editor.this.afterTextChanged(text);
			for(int i = 0; i < textWatchers.size(); ++i){
				textWatchers.get(i).afterTextChanged(text);
			}
		}
	}
	
	public void addTextWatcher(TextWatcher watcher){
		mChangeWatcher.textWatchers.add(watcher);
	}
	public void removeTextWatcher(TextWatcher watcher){
		mChangeWatcher.textWatchers.remove(watcher);
	}
	public void addSelectionWatcher(SelectionWatcher watcher){
		mChangeWatcher.selectionWatchers.add(watcher);
	}
	public void removeSelectionWatcher(SelectionWatcher watcher){
		mChangeWatcher.selectionWatchers.remove(watcher);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		//由于我可以滚动内部的图像，所以对于我自己的大小并没有什么要求
		//因此将我的大小直接设为父View规定的大小
		int width = MeasureSpec.getSize(widthMeasureSpec);
		int height = MeasureSpec.getSize(heightMeasureSpec);
		setMeasuredDimension(width, height);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		canvas.save();
		//需要将View的整个图像缩放指定倍数
		canvas.scale(scaleFactor, scaleFactor);
		
		//绘制文本和光标
		//long last = System.currentTimeMillis();
		mCursor.drawBackground(canvas, mPaint);
		mLayout.draw(canvas, mCursor.getCursorOffsetVertical());
		mCursor.drawForeground(canvas, mPaint);
		//long now = System.currentTimeMillis();
		//Toast.makeText(getContext(), String.valueOf(now-last), 0).show();
		canvas.restore();
	}

	@Override
	public void onDrawForeground(Canvas canvas)
	{
		super.onDrawForeground(canvas);
		//绘制光标把手
		mCursor.drawCursorHandle(canvas);
		mScrollBars.drawScrollBars(canvas);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (mGestureDetector == null){
			//与触摸有关的内容延迟在第一次触摸吋创建
			//因为GestureDetector会创建Handler，这将导致Editor无法在子线程中创建
			mGestureDetector = new GestureDetector(new GestureListener());
			mScaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener(0.5f, 2.0f));
			mChildTouchDelegate = new ChildTouchDelegate();
		}
		
		int action = event.getActionMasked();
		switch(action)
		{
			case MotionEvent.ACTION_DOWN:	
				touchState = SCROLL_SCALE;
				if (!mScroller.isFinished()) {
					//上次的滑行是否结束，如果没有那么强制结束
					mScroller.abortAnimation();
				}
				if (mChildTouchDelegate.onTouchEvent(event)){
					touchState = TOUCH_CHILD;
					getParent().requestDisallowInterceptTouchEvent(true);
				}
				//if click cursor handle
				//int offset = 0;
				//cursorX = mLayout.getOffsetHorizontal(offset);
				//cursorY = mLayout.getOffsetVertical(offset) + mLayout.getLineHeight()/2;
				
				break;
			case MotionEvent.ACTION_MOVE:
				switch(touchState)
				{
					case SELECT_STATE:
						//选择状态
						int offset = getOffsetForTouchEvent(event);
						if(offset>=cursorStart){
							//手指滑动到锚点后
						    setSelection(cursorStart,offset);
						}
						else if(offset<cursorStart){
							//手指滑动到锚点前
							setSelection(offset,cursorStart);
						}	
					break;
					//we move point to
					//cursorX += dx
					//cursorY += dy
					//int offset = getOffsetForTouchEvent(cursorX, cursorY);
					//setSelection(offset, offset);
				}
				break;
			case MotionEvent.ACTION_UP:
				break;
		}
		
		if(touchState == TOUCH_CHILD){
			mChildTouchDelegate.onTouchEvent(event);
		}
		else if(touchState == SCROLL_SCALE){
			mGestureDetector.onTouchEvent(event);
			mScaleGestureDetector.onTouchEvent(event);
		}
		invalidate();
		return true;
	}
	
	@Override
	public void computeScroll()
	{
		//视图被触摸后，就慢慢地滑行一段距离
		if (mScroller.computeScrollOffset()){
			//每次从mScroller中拿出本次应该滑行的距离，同时mScroller内部设置的总滑行值也会减少
			//当总滑行值为0，computeScrollOffset返回false，就不再滑行了
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            invalidate();
        }
	}

	@Override
	public void scrollTo(int x, int y)
	{
		int min = getMinScrollX();
		int max = getMaxScrollX();
		x = Math.min(max, Math.max(min, x));
		
		min = getMinScrollY();
		max = getMaxScrollY();
		y = Math.min(max, Math.max(min, y));
		super.scrollTo(x, y);
	}
	private void abortScrollTo(int x, int y)
	{
		if (!mScroller.isFinished()) {
			//上次的滑行是否结束，如果没有那么强制结束
			mScroller.abortAnimation();
		}
		scrollTo(x, y);
	}
	
	@Override
	public boolean canScrollHorizontally(int direction)
	{
		if (direction < 0) {
			// 向左滚动，检查当前水平滚动位置是否大于最小滚动值
			return getScrollX() > getMinScrollX();
		} else if (direction > 0) {
			// 向右滚动，检查当前水平滚动位置是否小于最大滚动值
			return getScrollX() < getMaxScrollX();
		}
		return false;
	}

	@Override
	public boolean canScrollVertically(int direction)
	{
		if (direction < 0) {
			// 向上滚动，检查当前垂直滚动位置是否大于最小滚动值
			return getScrollY() > getMinScrollY();
		} else if (direction > 0) {
			// 向下滚动，检查当前垂直滚动位置是否小于最大滚动值
			return getScrollY() < getMaxScrollY();
		}
		return false;
	}

	/* 计算视图可滚动内容的宽度(包含左侧和右侧) */
	protected int computeHorizontalScrollRange(){
		return getContentRight() - getContentLeft();
	}
	/* 计算当前的横向滚动量(相对于视图内容左边) */
	protected int computeHorizontalScrollOffset(){
		return getScrollX() - getContentLeft();
	}
	/* 计算横向滚动条对应在整个内容中的宽度 */
	protected int computeHorizontalScrollExtent()
	{
		int width = getWidth();
		int range = computeHorizontalScrollRange();
		//view的宽度在整个内容中的比例
		float bili = (float)width / range;
		float min = getMinScrollBarLen();
		if(bili * width < min){
			//如果该比例在宽度线上的滚动条长度小于滚动条最小长度，请重新计算
			//用min / width得到它在宽度线上的最小比例，并映射到整个内容上
			return (int)(min / width * range);
		}
		return width; 
	}
	
	/* 计算视图可滚动内容的高度 */
	protected int computeVerticalScrollRange(){
		return getContentBottom() - getContentTop();
	}
	/* 计算当前的纵向滚动量 */
	protected int computeVerticalScrollOffset(){
		return getScrollY() - getContentTop();
	}
	/* 计算纵向滚动条对应在整个内容中的高度 */
	protected int computeVerticalScrollExtent()
	{
		int height = getHeight();
		int range = computeVerticalScrollRange();
		float bili = (float)height / range;
		float min = getMinScrollBarLen();
		if(bili * height < min){
			//如果小于滑块最小长度，请重新计算
			return (int)(min / height * range);
		}
		return height;
	}
	/* 获取滚动条在宽度线/高度线上的最小长度 */
	private float getMinScrollBarLen(){
		return Math.min(getWidth(), getHeight()) * MIN_SCROLLBAR_LEN;
	}
	
	protected boolean canShowHorizontalScrollBar(){
		return computeHorizontalScrollRange() > getWidth();
	}
	protected boolean canShowVerticalScrollBar(){
		return computeVerticalScrollRange() > getHeight();
	}
	protected boolean needHorizontalScrollBarBold(){
		return computeHorizontalScrollRange() > getWidth() * 5;
	}
	protected boolean needVerticalScrollBarBold(){
		return computeVerticalScrollRange() > getHeight() * 5;
	}
	
	private int getMinScrollX(){
		return getContentLeft();
	}
	private int getMaxScrollX(){
		int left = getContentLeft();
		int right = getContentRight();
		return right - left < getWidth() ? left : right - getWidth();
	}
	private int getMinScrollY(){
		return getContentTop();
	}
	private int getMaxScrollY(){
		int top = getContentTop();
		int bottom = getContentBottom();
		return bottom - top < getHeight() ? top : bottom - getHeight();
	}
	
	private int getContentLeft(){
		return (int)(-mLayout.getLineNumberWidth() * scaleFactor);
	}
	private int getContentTop(){
		return 0;
	}
	private int getContentRight(){
		return (int)(mLayout.getWidth() * scaleFactor) + getWidth() / 2;
	}
	private int getContentBottom(){
		return (int)(mLayout.getHeight() * scaleFactor) + getHeight() / 2;
	}
	
	private float toSourcePoint(float dp){
		return dp / scaleFactor;
	}
	private float toDestPoint(float sp){
		return sp * scaleFactor;
	}
	public float getScaleFactor(){
		return scaleFactor;
	}
	
	private static final float SCROLLBAR_THICKNESS = 0.0125f;
	private static final float BOLD_SCROLLBAR_THICKNESS = 0.06f;
	private static final float MIN_SCROLLBAR_LEN = 0.175f;
	
	/* 当前触摸状态 */
	private static final int SCALE_STATE = 22, SELECT_STATE = 33;
	private static final int SCROLL_SCALE = 0;
	private static final int TOUCH_CHILD = 44;
	private static final int MOVE_CURSOR_HANDLE = 1;
	private static final int MOVE_SCROLLBAR = 2;
	private int touchState;
	
	/* 选择的起点 */
	private int cursorStart;
	private int cursorX, cursorY;
	
	/* 编辑器缩放倍数 */
	private float scaleFactor = 1f;

	/* 用于手指离开屏幕后做惯性滚动 */
    private Scroller mScroller = new Scroller(getContext());
	private VelocityTracker mVelocityTracker;

	private GestureDetector mGestureDetector;
	private ScaleGestureDetector mScaleGestureDetector;
	private ChildTouchDelegate mChildTouchDelegate;
	
	
	private class ChildTouchDelegate
	{
		private int mActivePointerId;
		private float mLastMotionX, mLastMotionY;
		private int touchChild; 
		
		public boolean onTouchEvent(MotionEvent event)
		{
			switch(event.getActionMasked())
			{
				case MotionEvent.ACTION_DOWN:
					mActivePointerId = event.getPointerId(0);
					mLastMotionX = event.getX();
					mLastMotionY = event.getY();
					if(mScrollBars.isTouchScrollBar((int)mLastMotionX, (int)mLastMotionY)){
						touchChild = MOVE_SCROLLBAR;
						return true;
					}
					if(mCursor.isTouchCursorHandle(mLastMotionX, mLastMotionY)){
						touchChild = MOVE_CURSOR_HANDLE;
						return true;
					}
					break;
				case MotionEvent.ACTION_MOVE:
					int index = event.findPointerIndex(mActivePointerId);
					float x = event.getX(index);
					float y = event.getY(index);
					float dx = x - mLastMotionX;
					float dy = y - mLastMotionY;
					switch(touchChild){
						case MOVE_SCROLLBAR:
							mScrollBars.executeTouchEvent((int)dx, (int)dy);
							break;
						case MOVE_CURSOR_HANDLE:
							mCursor.handleTouchEvent(dx, dy);
							break;
					}
					mLastMotionX = x;
					mLastMotionY = y;
					break;
				case MotionEvent.ACTION_UP:
					break;
			}
			return false;
		}
	}
	
	private class GestureListener extends GestureDetector.SimpleOnGestureListener 
	{
		public boolean onSingleTapUp(MotionEvent event)
		{
			//单击设置光标位置
			int offset = getOffsetForTouchEvent(event);
			mCursor.setSelection(offset, offset);
			openInputor(getContext(), Editor.this);
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
		{
			//we show bold scrollbar
			// 定义一个阈值，例如 1500 像素/秒
			final float THRESHOLD = 1500;
			if(Math.abs(velocityX) > THRESHOLD && needHorizontalScrollBarBold()){
				mScrollBars.hScrollBar.toBold();
			}
			if(Math.abs(velocityY) > THRESHOLD && needVerticalScrollBarBold()){
				mScrollBars.vScrollBar.toBold();
			}
			//设置mScroller的滑行值，并准备开始滑行
			mScroller.fling(getScrollX(), getScrollY(), -(int)velocityX, -(int)velocityY, 
				getMinScrollX(), getMaxScrollX(), getMinScrollY(), getMaxScrollY());
			return true;
		}

		public void onLongPress(MotionEvent event)
		{
			//长按开始选择，长按后的手指滑动不会触发onScroll
			//缩放可以中断选择，并且不要在缩放时触发长按
			if(touchState == SCALE_STATE){
				return;
			}
			touchState = SELECT_STATE;
			cursorStart = getOffsetForTouchEvent(event);
			getParent().requestDisallowInterceptTouchEvent(true);
		}
		
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
			//滑动手势，滚动视图
			scrollBy((int)distanceX, (int)distanceY);
			return true;
        }
    }
	
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener
	{
		private Matrix matrix;
		private float[] values;
		private float minScale, maxScale;
		
		ScaleListener(float minScale, float maxScale){
			matrix = new Matrix();
			values = new float[9];
			this.minScale = minScale;
			this.maxScale = maxScale;
		}
		
		public boolean onScale(ScaleGestureDetector detector) 
		{
			//缩放手势时不要拦截
			getParent().requestDisallowInterceptTouchEvent(true);
			
			//计算缩放倍数
			float scale = detector.getScaleFactor();
			float newScaleFactor = scaleFactor * scale;
			newScaleFactor = Math.min(maxScale, Math.max(minScale, newScaleFactor));
			float scaleChange = newScaleFactor / scaleFactor;
			scaleFactor = newScaleFactor;

			//获取缩放中心点
			float focusX = detector.getFocusX();
			float focusY = detector.getFocusY();
			
			//应用缩放并保持焦点位置
			matrix.setTranslate(-getScrollX(), -getScrollY());
			matrix.postScale(scaleChange, scaleChange, focusX, focusY);
			
			matrix.getValues(values);
			int sx = (int) -values[Matrix.MTRANS_X];
			int sy = (int) -values[Matrix.MTRANS_Y];
			scrollTo(sx, sy);
            return true;
		}
	}
	
	private int getOffsetForTouchEvent(MotionEvent event)
	{
		int index = event.getActionIndex();
		float dx = event.getX(index) + getScrollX();
		float dy = event.getY(index) + getScrollY();
		float sx = dx / scaleFactor;
		float sy = dy / scaleFactor;
		
		int line = mLayout.getLineForVertical((int)sy);
		int end = mLayout.getLineEnd(line);
		int offset = mLayout.getOffsetForHorizontal(line, sx);
		if (offset < end){
			float left = mLayout.getOffsetHorizontal(offset);
			float right = mLayout.getOffsetHorizontal(offset+1);
			if(sx >= (left + right) / 2){
				offset = offset + 1;
			}
		}
		return offset;
	}
}
