package com.editor.view;
import android.content.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.animation.*;
import java.util.*;
import android.app.*;
import android.widget.TabHost.*;
import android.content.res.*;

/**
 * 
 */
public class ViewPager extends LinearLayout
{
	private static final float mTouchSlop = 20;
	//使用栈存储所有的手指，每有手指落下就加入栈顶，手指抬起就将它从栈中移除
	//滑动时始终跟踪栈顶的手指，当所有手指抬起，则终止本次滑动
	private float mLastMotionX, mLastMotionY;
	private int mActivePointerId;
	
	private int mCurrentIndex;
	private ArrayList<PageData> mPageData;
	private onTabPageListener mPageListener;
	
	public ViewPager(Context cont){
		super(cont);
		init(cont);
	}
	public ViewPager(Context cont, AttributeSet attrs){
		super(cont, attrs);
		init(cont);
	}
	private void init(Context cont){
		mCurrentIndex = -1;
		mPageData = new ArrayList<>();
	}
	
	public int getCurrentIndex() {
		return mCurrentIndex;
	}
	public PageData getDataAt(int i){
		return mPageData.get(i);
	}
	public void setOnTabPageListener(onTabPageListener listener){
		mPageListener = listener;
	}
	public int findViewByLabel(String label){
		for(int i = 0; i < mPageData.size(); ++i){
			PageData data = mPageData.get(i);
			if(data.label.equals(label)){
				return i;
			}
		}
		return -1;
	}
	
	public void addPage(PageData page)
	{
		mPageData.add(page);
		addView(page.content);
		changeTabPage(getChildCount()-1);
	}
	public void removePage(int i)
	{
		mPageData.remove(i);
		removeViewAt(i);
		int tabIndex = i;
		if(mCurrentIndex >= i){
			tabIndex = mCurrentIndex - 1;
		}
		changeTabPage(tabIndex);
	}
	
	private void changeTabPage(final int i)
	{
		requestLayout();
		ViewTreeObserver.OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener(){
			public void onGlobalLayout(){
				tabPage(i);
				getViewTreeObserver().removeGlobalOnLayoutListener(this);
			}
		};
		getViewTreeObserver().addOnGlobalLayoutListener(listener);
	}
	
	public void tabPage(int i)
	{
		int old = mCurrentIndex;
		mCurrentIndex = i;
		gotoChildPosition(i);
		if(mPageListener != null && old != i){
			mPageListener.onTabPage(i);
		}
	}
	
	/* 滚动画布到指定孑View位置 */
	protected void gotoChildPosition(int index)
	{
		if(index < 0 || index >= getChildCount()){
			return;
		}
		View view = getChildAt(index);
		ValueAnimator animator;
		if(getOrientation() == HORIZONTAL){
			int fromx = getScrollX();
			int tox = view.getLeft();
			animator = ValueAnimator.ofInt(fromx, tox);
			animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
					public void onAnimationUpdate(ValueAnimator p1){
						int value = p1.getAnimatedValue();
						scrollTo(value, getScrollY());
					}
				});
		}
		else{
			int fromy = getScrollY();
			int toy = view.getTop();
			animator = ValueAnimator.ofInt(fromy, toy);
			animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
					public void onAnimationUpdate(ValueAnimator p1){
						int value = p1.getAnimatedValue();
						scrollTo(getScrollX(), value);
					}
				});
		}
		animator.setDuration(200);
		animator.start();
	}
	
	/* 通过坐标获取子元素 */
	protected int getChildFromPosition(int x, int y)
	{
		for(int i = 0; i < getChildCount(); ++i)
		{
			View view = getChildAt(i);
		    if (x >= view.getLeft() && x <= view.getRight() && 
			    y >= view.getTop() && y <= view.getBottom()){
				return i;
			}
		}
		return -1;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
		setMeasuredDimension(width, height);
		
		//为了支持页面切换，PageHandler中的每个子元素必须和PageHandler一样大
		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
		for(int i = 0; i < getChildCount(); ++i){
			View child = getChildAt(i);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		super.onLayout(changed, l, t, r, b);
		if(mCurrentIndex > -1){
			int sx = getScrollX();
			int sy = getScrollY();
			int childX = getChildAt(mCurrentIndex).getLeft();
			int childY = getChildAt(mCurrentIndex).getTop();
			if(sx != childX || sy != childY){
				scrollTo(childX, childY);
			}
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event)
	{
		switch (event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
				// 记录下当前的触摸点
				mLastMotionX = event.getX();
				mLastMotionY = event.getY();
				mActivePointerId = event.getPointerId(0);
				break;
			case MotionEvent.ACTION_MOVE:
				// 计算滑动的方向
				int activePointerIndex = event.findPointerIndex(mActivePointerId);
				float x = event.getX(activePointerIndex);
				float y = event.getY(activePointerIndex);
				float deltaX = x - mLastMotionX;
				float deltaY = y - mLastMotionY;
				float speedX = Math.abs(deltaX);
				float speedY = Math.abs(deltaY);
				if ((getOrientation() == HORIZONTAL && speedX > mTouchSlop && speedX > speedY * 4 && canScrollHorizontally(-(int)deltaX)) || 
					(getOrientation() == VERTICAL && speedY > mTouchSlop && speedY > speedX * 4 && canScrollVertically(-(int)deltaY))) {
					//横向布局时，手指左右移速较快，并且左右移速大于上下移速，并且自己可以左右滚动，拦截事件
					//纵向布局时，手指上下移速较快，并且上下移速大于左右移速，并且自己可以上下滚动，拦截事件
					return true;
				}
				mLastMotionX = x;
				mLastMotionY = y;
				break;
		}
		return super.onInterceptTouchEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		switch (event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
				// 初始化触摸事件处理
				mLastMotionX = event.getX();
				mLastMotionY = event.getY();
				mActivePointerId = event.getPointerId(0);
				break;
			case MotionEvent.ACTION_MOVE:
				// 计算滑动偏移量
				int activePointerIndex = event.findPointerIndex(mActivePointerId);
				float x = event.getX(activePointerIndex);
				float y = event.getY(activePointerIndex);
				float deltaX = mLastMotionX - x;
				float deltaY = mLastMotionY - y;
				
				if (getOrientation() == HORIZONTAL) {
					scrollBy((int)(deltaX),  0);
				} else {
					scrollBy(0, (int)(deltaY));
				}
			
				mLastMotionX = x;
				mLastMotionY = y;
				break;
			case MotionEvent.ACTION_UP:
				if(event.getPointerCount() == 1){
					//计算滚动到哪个页面
					if(getChildCount() == 0){
						scrollTo(0, 0);
						break;
					}
					
					int left = getScrollX();
					int top = getScrollY();
					int right = left + getWidth();
					int bottom = top + getHeight();
					
					int start = getChildFromPosition(left, top);
					int end = getChildFromPosition(right, bottom);
					start = start < 0 ? 0 : start;
					end = end < 0 ? getChildCount() - 1 : end;
					
					if(start == end){
						gotoChildPosition(start);
					}
					else{
						int toIndex = 0;
						if(getOrientation() == HORIZONTAL){
							int middle = (left + right) >> 1;
							toIndex = getChildAt(start).getRight() >= middle ? start : end;
						}else{
							int middle = (top + bottom) >> 1;
							toIndex = getChildAt(start).getBottom() >= middle ? start : end;
						}
						tabPage(toIndex);
					}
				}
				break;
		}
		return true;
	}

	@Override
	public boolean canScrollHorizontally(int direction)
	{
		if(mCurrentIndex > -1){
			return !getChildAt(mCurrentIndex).canScrollHorizontally(direction);
		}
		return super.canScrollHorizontally(direction);
	}

	@Override
	public boolean canScrollVertically(int direction)
	{
		if(mCurrentIndex > -1){
			return !getChildAt(mCurrentIndex).canScrollVertically(direction);
		}
		return super.canScrollVertically(direction);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		changeTabPage(mCurrentIndex);
	}
	
	public static interface onTabPageListener
	{
		public void onTabPage(int i)
	}
	
	public static class PageData
	{
		private View content;
		private String label;
		private Object data;
		
		public PageData(View content, String label, Object data){
			this.content = content;
			this.label = label;
			this.data = data;
		}
		
		public View getContent(){
			return content;
		}
		public String getLabel(){
			return label;
		}
		public Object getData(){
			return data;
		}
	}
}
