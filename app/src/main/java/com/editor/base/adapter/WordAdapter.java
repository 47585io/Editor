package com.editor.base.adapter;

import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.editor.*;
import java.util.*;
import android.util.*;

public class WordAdapter extends BaseAdapter
{
	private int resid;
	private Context mContext;
	private ArrayList<wordIcon> mIconItems;

	public WordAdapter(Context context, int resid){
		this.resid = resid;
		mContext = context;
		mIconItems = new ArrayList<>();
	}
	
	public void addItem(wordIcon icon){
		mIconItems.add(icon);
	}
	public void addItem(int index, wordIcon icon){
		mIconItems.add(index, icon);
	}
	
	public void removeItem(int index){
		mIconItems.remove(index);
	}
	public void removeItem(wordIcon icon){
		mIconItems.remove(icon);
	}
	
	public void setItem(int index, wordIcon icon){
		mIconItems.set(index, icon);
	}
	
	public void clear(){
		mIconItems.clear();
	}
	
	@Override
	public int getCount(){
		return mIconItems.size();
	}

	@Override
	public wordIcon getItem(int p1){
		return mIconItems.get(p1);
	}

	@Override
	public long getItemId(int p1){
		return mIconItems.get(p1).hashCode();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		final ViewHolder viewHolder;
        if (convertView == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            convertView = layoutInflater.inflate(resid, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.tvIcon = convertView.findViewById(R.id.list_item_icon);
            viewHolder.tvName = convertView.findViewById(R.id.list_item_text);
            convertView.setTag(viewHolder);
        }else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        wordIcon item = getItem(position);
		viewHolder.tvIcon.setImageResource(item.icon);
        viewHolder.tvName.setText(item.text);
        return convertView;
	}

	private class ViewHolder
	{
		private ImageView tvIcon;
		private TextView tvName;
	}
}
