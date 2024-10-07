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

public class Editor extends View implements TextWatcher
{
	private TextPaint mPaint;
	private EditableList mText;
	private BlockLayout mLayout;
	private myInputConnection mInput;

	private Cursor mCursor;
	private TextWatcher mTextWatcher;
	
	/* defAttrs */
	private int textColor;
	private float textSize;
	private int cursorColor;
	private int selectionColor;
	private int highlightLineColor;
	private int TabSize;
	private int lineNumberColor;
	private float lineSpacing;
	
	//cursorWidthSpacing?
	//Typeface?
	
	public Editor(Context cont){
		super(cont);
		init(cont, null);
		config();
	}
	protected void init(Context cont, AttributeSet attrs)
	{
		initAttrs(cont, attrs);
		mCursor = new Cursor();
		mPaint = new TextPaint();
		mInput = new myInputConnection();
		setText("", 0, 0);
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
		highlightLineColor = array.getColor(R.styleable.Editor_highlightLineColor, highlightLineColor);
		
		TabSize = array.getInteger(R.styleable.Editor_TabSize, TabSize);
		lineNumberColor = array.getColor(R.styleable.Editor_lineNumberColor, lineNumberColor);
		lineSpacing = array.getFloat(R.styleable.Editor_lineSpacing, lineSpacing);
	}
	protected void config()
	{
		configPaint(mPaint);
		setClickable(true);
		setLongClickable(true);
		//设置视图在任何情况下都可以获取焦点
		setFocusable(true);
		setFocusableInTouchMode(true);
		//设置在获取焦点时不用额外绘制高亮的矩形区域
		setDefaultFocusHighlightEnabled(false);
	}
	private void configPaint(TextPaint paint)
	{
		paint.setTextSize(textSize); 
		paint.setColor(textColor); 
		paint.setTypeface(Typeface.MONOSPACE);
	}
	
	public void setText(CharSequence text){
		setText(text, 0, text.length());
	}
	public void setText(CharSequence text,int start,int end)
	{
		mText = new EditableList(text,start,end);
		mText.setTextWatcher(this);
		if(mLayout != null){
			int lineColor = mLayout.getLineColor();
			float lineSpacing = mLayout.getLineSpacing();
			mLayout = new BlockLayout(mText, mPaint, lineColor, lineSpacing);
		}else{
			mLayout = new BlockLayout(mText, mPaint, lineNumberColor, lineSpacing);
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
	
	/* 文本变化时调用文本监视器的方法，并更改光标位置 */
	public void beforeTextChanged(CharSequence text, int start, int lenghtBefore, int lengthAfter)
	{
		if(mTextWatcher!=null){
			mTextWatcher.beforeTextChanged(text,start,lenghtBefore,lengthAfter);
		}
	}
	public void onTextChanged(CharSequence text, int start, int lenghtBefore, int lengthAfter)
	{
		int index = start+lengthAfter;
		mCursor.setSelection(index,index);	
		if(mTextWatcher!=null){
			mTextWatcher.onTextChanged(text,start,lenghtBefore,lengthAfter);
		}
	}
	public void afterTextChanged(Editable text)
	{
		//文本变化后，刷新界面
		invalidate();
		if(mTextWatcher!=null){
			mTextWatcher.afterTextChanged(text);
		}	
	}
	public void setTextWatcher(TextWatcher watcher){
		mTextWatcher = watcher;
	}
	
	private final class Cursor
	{
		public int selectionStart, selectionEnd;
		public float selectionStartX, selectionStartY;
		public float selectionEndX, selectionEndY;
		public Path cursorPath;
		public Rect lineBounds;
		
		private Cursor(){
			cursorPath = new Path();
			lineBounds = new Rect();
		}
		
		public void setSelection(int start, int end)
		{
			if(start != selectionStart || end != selectionEnd)
			{
				int ost = selectionStart;
				int oen = selectionEnd;
				if(start != selectionStart){
					selectionStart = start;
					selectionStartX = mLayout.getOffsetHorizontal(start);
					selectionStartY = mLayout.getOffsetVertical(start);
				}
				if(end != selectionEnd){
					selectionEnd = end;
					selectionEndX = mLayout.getOffsetHorizontal(end);
					selectionEndY = mLayout.getOffsetVertical(end);
				}
				
				//当光标位置变化，制作新的Path和Rect
				cursorPath.rewind();
				if(selectionStart == selectionEnd){
					mLayout.getCursorPath(selectionStart, cursorPath);
				}else{
					mLayout.getSelectionPath(selectionStart, selectionEnd, cursorPath);
				}
				mLayout.getLineBounds(mLayout.getLineForVertical((int)getCursorOffsetVertical()), lineBounds);
				//lineBounds.right = Integer.MAX_VALUE;
				Editor.this.onSelectionChanged(start, end, ost, oen, mText);
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
		
		public void drawBackground(Canvas canvas, Paint paint)
		{
			int saveColor = paint.getColor();
			if(selectionStart == selectionEnd){
				paint.setColor(highlightLineColor);
				canvas.drawRect(lineBounds, paint);
			}
			paint.setColor(saveColor);
		}
		
		public void drawForeground(Canvas canvas, Paint paint)
		{
			int saveColor = paint.getColor();
			if(selectionStart == selectionEnd)
				paint.setColor(cursorColor);
			else
				paint.setColor(selectionColor);
			canvas.drawPath(cursorPath, paint);
			paint.setColor(saveColor);
		}
		
		public int getCursorOffsetVertical()
		{
			float off = mLayout.getLineHeight() / 2;
			if(selectionStart == selectionEnd){
				return (int)(selectionStartY + off);
			}
			return (int)(selectionEndY + off);
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
	
	public void onSelectionChanged(int start, int end, int oldStart, int oldEnd, CharSequence editor)
	{
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
	public boolean onTouchEvent(MotionEvent event)
	{
		if (mVelocityTracker == null) {
			//每次都在事件开始时创建一个计速器
			mVelocityTracker = VelocityTracker.obtain();
		}
		//每次将事件传递过去计算手指速度
        mVelocityTracker.addMovement(event);
		
		switch(event.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:	
				if (!mScroller.isFinished()) {
					//上次的滑行是否结束，如果没有那么强制结束
					mScroller.abortAnimation();
				}
				break;
			case MotionEvent.ACTION_UP:
				int sx = getScrollX();
				int sy = getScrollY();
				//计算速度并获取应该在x和y轴上滑行的距离
				mVelocityTracker.computeCurrentVelocity(1000);
				int dx = (int)-mVelocityTracker.getXVelocity();
				int dy = (int)-mVelocityTracker.getYVelocity();
				//设置mScroller的滑行值，并准备开始滑行
				mScroller.fling(sx, sy, dx, dy, 
				    getMinScrollX(), getMaxScrollX(), getMinScrollY(), getMaxScrollY());
					
			case MotionEvent.ACTION_CANCEL:
				if (mVelocityTracker != null) {
					//在ACTION_CANCEL或ACTION_UP时，回收本次创建的mVelocityTracker
				    mVelocityTracker.recycle();
				    mVelocityTracker = null;
				}
		}
		
		gestureDetector.onTouchEvent(event);
		scaleDetector.onTouchEvent(event);
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
	
	private int getMinScrollX(){
		return (int)(-mLayout.getLineNumberWidth() * scaleFactor);
	}
	private int getMaxScrollX(){
		int left = (int)(-mLayout.getLineNumberWidth() * scaleFactor);
		int right = (int)((mLayout.getWidth() + 500) * scaleFactor);
		return right - left < getWidth() ? left : right - getWidth();
	}
	private int getMinScrollY(){
		return 0;
	}
	private int getMaxScrollY(){
		int top = 0;
		int bottom = (int)((mLayout.getHeight() + 1000) * scaleFactor);
		return bottom - top < getHeight() ? top : bottom - getHeight();
	}
	public float getScaleFactor(){
		return scaleFactor;
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
	
	
	/* 编辑器缩放倍数 */
	private float scaleFactor = 1f;

	/* 用于手指离开屏幕后做惯性滚动 */
    private Scroller mScroller = new Scroller(getContext());
	private VelocityTracker mVelocityTracker;

	private GestureDetector gestureDetector = new GestureDetector(new GestureListener());;
	private ScaleGestureDetector scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener(0.5f, 2.0f));
	
	
	private class GestureListener extends GestureDetector.SimpleOnGestureListener 
	{
		@Override
		public boolean onSingleTapUp(MotionEvent event)
		{
			int index = event.getActionIndex();
			float dx = event.getX(index) + getScrollX();
			float dy = event.getY(index) + getScrollY();
			float sx = dx / scaleFactor;
			float sy = dy / scaleFactor;
			int offset = mLayout.getOffsetForPosition(sx, sy);
			mCursor.setSelection(offset, offset);
			openInputor(getContext(), Editor.this);
			return true;
		}

		@Override
		public boolean onDoubleTap(MotionEvent event)
		{
			//选词
			return super.onDoubleTap(event);
		}

		@Override
		public void onLongPress(MotionEvent event)
		{
			
			super.onLongPress(event);
		}
		
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
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
}
