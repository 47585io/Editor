package com.editor.view;
import android.widget.*;
import android.content.*;
import android.view.*;
import android.util.*;
import android.content.res.*;
import android.graphics.drawable.*;
import android.graphics.*;
import android.animation.*;
import com.editor.*;

/**
 * SplitterLayout的空间被一根轴分为上下或左右两部分，
 * 轴的两侧可以各放置一个View，拖动轴可以调整两部分的大小
 */
public class SplitterLayout extends LinearLayout
{
	private float separator = 0.6f;
	private float minSeparator = 0.6f, maxSeparator = 1f;
	private boolean touchSeparatorEnabled;
	private boolean autoMoveSeparator, autoChangeOrientation;
	
	private boolean isTouchOnSeparator, isInterceptGesture;
	private float mLastMotionX, mLastMotionY;
	private int mActivePointerId;
	
	public SplitterLayout(Context cont){
		super(cont);
	}
	public SplitterLayout(Context cont, AttributeSet attrs){
		super(cont, attrs);
		init(attrs);
	}
	private void init(AttributeSet attrs)
	{
		if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SplitterLayout);

            separator = a.getFloat(R.styleable.SplitterLayout_separator, separator);
            minSeparator = a.getFloat(R.styleable.SplitterLayout_minSeparator, minSeparator);
            maxSeparator = a.getFloat(R.styleable.SplitterLayout_maxSeparator, maxSeparator);
            touchSeparatorEnabled = a.getBoolean(R.styleable.SplitterLayout_touchSeparatorEnabled, touchSeparatorEnabled);
            autoMoveSeparator = a.getBoolean(R.styleable.SplitterLayout_autoMoveSeparator, autoMoveSeparator);
            autoChangeOrientation = a.getBoolean(R.styleable.SplitterLayout_autoChangeOrientation, autoChangeOrientation);

            a.recycle();
        }
	}
	
	public void setBothView(View first, View second){
		removeAllViews();
		addView(first);
		addView(second);
	}
	public void setSeparator(float newSeparator){
		float oldSeparator = this.separator;
		newSeparator = Math.max(newSeparator, minSeparator);
		newSeparator = Math.min(newSeparator, maxSeparator);
		if(oldSeparator != newSeparator){
			this.separator = newSeparator;
			requestLayout();
		}
	}
	public void setSeparatorRange(float min, float max){
		minSeparator = min;
		maxSeparator = max;
	}
	public float getSeparator(){
		return separator;
	}
	
	public void moveSeparatorTo(float to){
		ValueAnimator moveSeparatorAnimator = ValueAnimator.ofFloat(separator, to);
		moveSeparatorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
				public void onAnimationUpdate(ValueAnimator p1){
					float value = p1.getAnimatedValue();
					setSeparator(value);
				}
			});
		moveSeparatorAnimator.start();
	}
	public void setTouchSeparatorEnabled(boolean enabled){
		touchSeparatorEnabled = enabled;
	}
	public void setAutoMoveSeparator(boolean auto){
		autoMoveSeparator = auto;
	}
	public void setAutoChangeOrientation(boolean auto){
		autoChangeOrientation = auto;
		if(autoChangeOrientation){
			setOrientationByConfiguration(getContext().getResources().getConfiguration());
		}
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
		setMeasuredDimension(width, height);
		//分配给它们两侧的大小
		if(getOrientation() == HORIZONTAL){
			measureChildrenWithHorizontal(width, height);
		}else{
			measureChildrenWithVertical(width, height);
		}
	}
	
	private void measureChildrenWithHorizontal(int width, int height)
	{
		if(getChildCount() > 0)
		{
			View leftChild = getChildAt(0);
			int separatorPosition = (int) (width * separator);
			int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(separatorPosition, MeasureSpec.EXACTLY);
			int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
			leftChild.measure(childWidthMeasureSpec, childHeightMeasureSpec);
			
			if(getChildCount() > 1){
				View rightChild = getChildAt(1);
				childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width - separatorPosition, MeasureSpec.EXACTLY);
				rightChild.measure(childWidthMeasureSpec, childHeightMeasureSpec);
			}
		}
	}
	
	private void measureChildrenWithVertical(int width, int height)
	{
		if(getChildCount() > 0)
		{
			View topChild = getChildAt(0);
			int separatorPosition = (int) (height * separator);
			int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
			int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(separatorPosition, MeasureSpec.EXACTLY);
			topChild.measure(childWidthMeasureSpec, childHeightMeasureSpec);
			
			if(getChildCount() > 1){
				View bottomChild = getChildAt(1);
				childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height - separatorPosition, MeasureSpec.EXACTLY);
				bottomChild.measure(childWidthMeasureSpec, childHeightMeasureSpec);
			}
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event)
	{
		final int touchSlop = 10;
		switch(event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
				mLastMotionX = event.getX();
				mLastMotionY = event.getY();
				mActivePointerId = event.getPointerId(0);
				isInterceptGesture = true;
				isTouchOnSeparator = isTouchOnSeparator(event) || false;
				break;
			case MotionEvent.ACTION_MOVE:
				// 计算滑动的方向
				int activePointerIndex = event.findPointerIndex(mActivePointerId);
				float x = event.getX(activePointerIndex);
				float y = event.getY(activePointerIndex);
				float speedX = Math.abs(x - mLastMotionX);
				float speedY = Math.abs(y - mLastMotionY);
				if ((getOrientation() == HORIZONTAL && speedX > touchSlop && speedX > speedY) || 
					(getOrientation() == VERTICAL && speedY > touchSlop && speedY > speedX)) {
					//横向布局时，手指左右移速较快，并且左右移速大于上下移速，拦截事件
					//纵向布局时，手指上下移速较快，并且上下移速大于左右移速，拦截事件
					//必须在开始时点击到了分隔轴，并且手指没有滚动子View
					return isTouchOnSeparator && isInterceptGesture; 
				}
				mLastMotionX = x;
				mLastMotionY = y;
				break;
		}
		//如果手指点击到分隔轴，就拦截事件给自己
		return super.onInterceptTouchEvent(event);
	}

	@Override
    public boolean onTouchEvent(MotionEvent event)
	{
		//如果拦截到了事件，移动分隔轴
		//如果没有子View消耗事件，并且手指点击到分隔轴，移动分隔轴
		int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN && isTouchOnSeparator(event)) {
			return true;
        } 
		else if (action == MotionEvent.ACTION_MOVE) {
            float newTouchPosition = getTouchPosition(event);
            float newSeparator = newTouchPosition / (getOrientation() == VERTICAL ? getHeight() : getWidth());
            setSeparator(newSeparator);
            return true;
        }
		else if(action == MotionEvent.ACTION_UP && event.getPointerCount() == 1 && autoMoveSeparator){
			float mid = (minSeparator + maxSeparator) / 2;
			float to = separator < mid ? minSeparator : maxSeparator;
			moveSeparatorTo(to);
		}
        return super.onTouchEvent(event);
    }

	private boolean isTouchOnSeparator(MotionEvent event) 
	{
		if(!touchSeparatorEnabled){
			return false;
		}
        int x = (int) event.getX();
        int y = (int) event.getY();
        int width = getWidth();
        int height = getHeight();
		float separatorInsets = 100;

        if (getOrientation() == VERTICAL) {
            float separatorPosition = height * separator;
            return Math.abs(y - separatorPosition) < separatorInsets; 
        } else {
            float separatorPosition = width * separator;
            return Math.abs(x - separatorPosition) < separatorInsets; 
        }
    }
    private float getTouchPosition(MotionEvent event) {
        return getOrientation() == VERTICAL ? event.getY() : event.getX();
    }

	@Override
	protected void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		if(autoChangeOrientation){
			setOrientationByConfiguration(newConfig);
		}
	}
	
	public void setOrientationByConfiguration(Configuration newConfig){
		if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE){
			setOrientation(LinearLayout.HORIZONTAL);
		}
		else if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
			setOrientation(LinearLayout.VERTICAL);
		}
	}
}
