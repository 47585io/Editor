package com.editor.view;
import android.widget.*;
import android.content.*;
import android.util.*;

public class ReSpinner extends Spinner
{
	public boolean isDropDownMenuShown = false; //标志下拉列表是否正在显示
    private onItemSelectionListener slistener = null;

	public ReSpinner(Context context) {
		super(context);
	}
	public ReSpinner(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	public ReSpinner(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	public void setonItemSelectionListener(onItemSelectionListener listener){
		slistener = listener;
	}

	public boolean isDropDownMenuShown(){
		return isDropDownMenuShown;
	}
	public void setDropDownMenuShown(boolean isDropDownMenuShown){
		this.isDropDownMenuShown = isDropDownMenuShown;
	}
	@Override
	public boolean performClick() {
		this.isDropDownMenuShown = true;
		return super.performClick();
	}

	@Override
	public void setSelection(int position) {
		boolean sameSelected = position == getSelectedItemPosition();
		super.setSelection(position);
		if (sameSelected) {
			//getOnItemSelectedListener().onItemSelected(this, getSelectedView(), position, getSelectedItemId());
			if(slistener != null)
				slistener.onItemRepeatSelected(position);
		}
	}
	
	@Override
	public void setSelection(int position, boolean animate) {
		boolean sameSelected = position == getSelectedItemPosition();
		super.setSelection(position, animate);
		if (sameSelected) {
			// 如果选择项是Spinner当前已选择的项,则 OnItemSelectedListener并不会触发,因此这里手动触发回调
			//getOnItemSelectedListener().onItemSelected(this, getSelectedView(), position, getSelectedItemId());
			if(slistener != null)
				slistener.onItemRepeatSelected(position);
		}
	}

	public static abstract interface onItemSelectionListener extends OnItemSelectedListener
	{
		public abstract void onItemRepeatSelected(int postion)
	}
}
