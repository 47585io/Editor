package com.editor.text;
import android.text.*;
import com.editor.base.array.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * 将大的数据分块/分区是一个很棒的思想，
 * 它使得对于大的数据的修改仅存在于小的区域中，而不必修改所有数据，
 * 只要限制每个块的大小，便可以使效率均衡。
 * 此类是分块文本容器的实现类，此类主要围绕两大内容:
 * 文本和span如何正确分配到文本块中并与其建立绑定，文本块顺序刷新和事件发送
 */
public class EditableList implements Editable
{
	private int mLength;    //总文本长度
	private int mBlockSize; //文本块的个数
	private int mSpanCount; //span的个数
	private int mSpanInsertCount; //span插入计数器

	//这里将文本打碎成文本块并按正序存储在mBlocks中，mBlockStarts记录了每个文本块的内容在总文本中的起始偏移量，它也是按正序排列，这用于快速查找下标所在的文本块
	//当插入或删除文本时，我们只要操作局部的少量文本块，其它文本块的内容保持不变(不用操作所有内容)
	//由于是用文本块存储的，某些span的范围可能会跨越多个文本块，因此我们使用mSpanInBlocks来存储span所在的文本块，并在修改文本时保持同步

	private Editable[] mBlocks;      //文本块列表
	private int[] mBlockStarts;      //每个文本块在总文本中的起始偏移量
	private IdentityHashMap<Editable,Integer> mIndexOfBlocks; //文本块处于mBlocks和mBlockStarts中的下标
	private IdentityHashMap<Object,SpanRange> mSpanInBlocks;  //span处于哪些文本块中

	private int mLowBlockIndexMark; //记录了mIndexOfBlocks应该从哪里开始刷新，避免无效刷新
	private int mLowBlockStartMark; //记录了mBlockStarts应该从哪里开始刷新
	private final int MaxCount;     //每个文本块的最大容量
	private final int ReserveCount; //在文本块装满并截取时，会额外预留ReserveCount长度的空间

	private int mTextWatcherDepth;
	private TextWatcher mTextWatcher;
	private BlockListener mBlockListener;
	private InputFilter[] mFilters = NO_FILTERS;

	private static final int MAX_COUNT = 1088;
	private static final int RESERVE_COUNT = 64;
	private static final InputFilter[] NO_FILTERS = new InputFilter[0];
	private static final IdentityHashMap[] sMapBuffer = new IdentityHashMap[6];
	
	
	public EditableList(){
		this("");
	}
	public EditableList(CharSequence text){
		this(text,0,text.length());
	}
	public EditableList(CharSequence text, int start, int end)
	{
		int srclen = end - start;
		if(srclen < 0) throw new StringIndexOutOfBoundsException();
		
		mLength = srclen;
		mBlockSize = 0;
		mSpanCount = 0;
		mSpanInsertCount = 0;
		
		mBlocks = EmptyArray.emptyArray(Editable.class);
		mBlockStarts = EmptyArray.INT;
		mIndexOfBlocks = new IdentityHashMap<>();
		mSpanInBlocks = new IdentityHashMap<>();
		
		MaxCount = MAX_COUNT;
		ReserveCount = RESERVE_COUNT;
		
		if(end > start){
			//第一次插入文本时，可以直接dispatchTextBlock
			//这可以节省时间和内存
			if(text instanceof Spanned){
				String str = TextUtils.substring(text, start, end);
				dispatchTextBlock(0, str, 0, str.length(), true);
				TextUtils.copySpansFrom((Spanned)text, start, end, Object.class, this, 0);
			}else{
				dispatchTextBlock(0, text, start, end, true);
			}
		}
		else{
			//仅仅只是在没有文本时创建空文本块，待之后插入
			//而插入文本时，不应创建多余的空文本块
			//删除文本时，应移除多余的空文本块
			addBlocks(0, 1, true);
		}
	}

	public void setTextWatcher(TextWatcher watcher){
		mTextWatcher = watcher;
	}
	void setBlockListener(BlockListener listener){
		mBlockListener = listener;
	}
	
	/* 在指定位置添加一个文本块 */
	private void addBlock(int i)
	{
		Editable block = new SpannableStringBuilder();
		mBlocks = GrowingArrayUtils.insert(mBlocks, mBlockSize, i, block);
		mBlockStarts = GrowingArrayUtils.insert(mBlockStarts,mBlockSize, i, 0);
		mIndexOfBlocks.put(block, i);
		mBlockSize++;
	}
	/* 移除指定位置的文本块 */
	private void removeBlock(int i)
	{
		Editable block = mBlocks[i];
		replaceSpans(i, 0, block.length(), "", 0, 0, true);
		mBlocks = GrowingArrayUtils.remove(mBlocks, mBlockSize, i);
		mBlockStarts = GrowingArrayUtils.remove(mBlockStarts, mBlockSize, i);
		mIndexOfBlocks.remove(block);
		mBlockSize--;
	}
	/* 调整指定文本块的内部结构，使得它占用更少的存储空间，并保证存储的内容不变且绑定正确 */
	private void adjustBlock(int i)
	{
		Editable oldBlock = mBlocks[i];
		replaceSpans(i, 0, oldBlock.length(), "", 0, 0, false);
		mIndexOfBlocks.remove(oldBlock);
		
		Editable newBlock = new SpannableStringBuilder();
		mBlocks[i] = newBlock;
		mIndexOfBlocks.put(newBlock, i);
		replaceSpans(i, 0, 0, oldBlock, 0, oldBlock.length(), false);
		newBlock.replace(0, 0, oldBlock, 0, oldBlock.length());
	}
	
	/**
	 * 从指定位置开始添加count个文本块，
	 * 若send为true，刷新mIndexOfBlocks和mBlockStarts，并发送文本块改变的事件
	 * 若send为false，则刷新mIndexOfBlocks和mBlockStarts是调用者的责任 
	 */
	private void addBlocks(final int i, final int count, boolean send)
	{
		for(int k=0;k<count;++k){
			addBlock(i+k);
		}
		invalidateIndexMark(i);
		invalidateStartMark(i);
		if(send){
			refreshInvariants();
			sendBlocksAdded(i,count);
		}
	}
	
	/**
	 * 移除指定范围内的文本块，包含下标为i的文本块，但不包含下标为j的文本块
	 * 若send为false，则刷新mIndexOfBlocks和mBlockStarts是调用者的责任
	 */
	private void removeBlocks(final int i, final int j, boolean send)
	{
		for(int k=j-1;i<=k;--k){
			removeBlock(k);
		}
		invalidateIndexMark(i);
		invalidateStartMark(i);
		if(send){
			refreshInvariants();
			sendBlocksRemoved(i,j);
		}
		if(mBlockSize==0){
			//必须保证列表中至少有一个文本块，否则某些方法将会抛出异常
			addBlocks(0,1,true);
		}
	}
	
	/**
	 * 从指定id的文本块开始，分发tb中指定范围内的文本，返回分发到的最后一个文本块下标 
	 * 若send为false，则刷新mBlockStarts是调用者的责任
	 */
	private int dispatchTextBlock(final int id, CharSequence tb, int tbStart, int tbEnd, boolean send)
	{
		//计算并添加文本块，文本块会多预留一些空间
		final int onceCount = this.MaxCount-ReserveCount;
		final int len = tbEnd-tbStart;
		final int count = len%onceCount==0 ? len/onceCount : len/onceCount+1;

		int i = id;
		int j = id+count;
		//添加文本块后必须刷新mIndexOfBlocks，从id开始
		addBlocks(i,count,false);
		refreshInvariants();

		//计算并填充文本，插入文本仅需mIndexOfBlocks正确，因此可以连续插入
		for(i=id;i<j;++i)
		{
			if(tbEnd-tbStart <= onceCount){
				repalceWithSpans(i,0,0,tb,tbStart,tbEnd,false,false);
				break;
			}
			repalceWithSpans(i,0,0,tb,tbStart,tbStart+onceCount,false,false);
			tbStart += onceCount;
		}

		//在修正mBlockStarts后，一并发送事件
		if(send){
			refreshInvariants();
			sendBlocksAdded(id,count);
			sendAfterBlocksTextInserted(id, 0, j-1, mBlocks[j-1].length());
		}
		return j-1;
	}
	
	int getBlockSize(){
		return mBlockSize;
	}
	Editable getBlock(int id){
		return mBlocks[id];
	}
	int getBlockStart(int id){
		return mBlockStarts[id];
	}
	/* 获取每个文本块的标准容量 */
	int getBlockEnsureCount(){
		return MaxCount - ReserveCount;
	}
	
	/**
	 * 寻找index所在的文本块，返回该文本块的下标
	 * 如果index处于两文本块边界，那么返回前面的文本块，
	 * 无论如何，返回的文本块下标都不会超出文本块列表的范围(0 ~ mBlockSize)
	 */
	int findBlockBeforeIndex(int index)
	{
		int id = ArrayUtils.findRangeContainingIndex(mBlockStarts, mBlockSize, index);
		if(id > 0 && mBlockStarts[id] == index){
			return id - 1;
		}
		return id;
	}
	
	/**
	 * 寻找index所在的文本块，返回该文本块的下标
	 * 如果index处于两文本块边界，那么返回后面的文本块，
	 * 无论如何，返回的文本块下标都不会超出文本块列表的范围(0 ~ mBlockSize)
	 */
	int findBlockAfterIndex(int index){
		return ArrayUtils.findRangeContainingIndex(mBlockStarts, mBlockSize, index);
	}

	public Editable append(CharSequence p1){
		int len = length();
		return replace(len,len,p1,0,p1.length());
	}
	public Editable append(CharSequence p1, int p2, int p3){
		int len = length();
		return replace(len,len,p1,p2,p3);
	}
	public Editable append(char p1){
		int len = length();
		return replace(len,len,String.valueOf(p1),0,1);
	}
	public Editable insert(int p1, CharSequence p2, int p3, int p4){
		return replace(p1,p1,p2,p3,p4);
	}
	public Editable insert(int p1, CharSequence p2){
		return replace(p1,p1,p2,0,p2.length());
	}
	public Editable delete(int p1, int p2){
		return replace(p1,p2,"",0,0);
	}
	public Editable replace(int p1, int p2, CharSequence p3){
		return replace(p1,p2,p3,0,p3.length());
	}
	
	/** 
	 * 先删除start~end之间的文本和文本块，然后从start指定的文本块开始插入文本，
	 * 如果插入文本中的span已经存在于自身中，或者是无效的span，则不会加入该span
	 */
	public Editable replace(final int start, final int end, CharSequence tb, int tbStart, int tbEnd)
	{
		checkRange("replace", start, end);
		//过滤文本
        for (int i = 0; i < mFilters.length; ++i){
            CharSequence repl = mFilters[i].filter(tb, tbStart, tbEnd, this, start, end);
            if (repl!=null) {
                tb = repl;
                tbStart = 0;
                tbEnd = repl.length();
            }
        }

		final int before = end-start;
		final int after = tbEnd-tbStart;
		if(before == 0 && after == 0){
			//如果要删除和插入的文本长度为0，提前退出，以便文本监视器不会得到通知
			return this;
		}
		//文本变化前，调用文本监视器的方法，文本修改前触发二次更改无所谓
		sendBeforeTextChanged(start, before, after);

		if(before > 0){
			//找到start和end所指定的文本块，并将它们偏移到文本块的下标
			int i = findBlockAfterIndex(start);
			int j = findBlockBeforeIndex(end);
			int iStart = start - mBlockStarts[i];
			int jEnd = end - mBlockStarts[j];
			//删除范围内的文本和文本块
		    deleteForBlocks(i, iStart, j, jEnd);
		}
		if(after > 0){
			//在删除文本后重新查找插入的起始位置，因为删除文本可能导致原文本块移除
			int i = findBlockAfterIndex(start);
			int index = start - mBlockStarts[i];
			//然后将文本插入指定位置
			if(tb instanceof Spanned){
				String text = TextUtils.substring(tb, tbStart, tbEnd);
				insertForBlocks(i, index, text, 0, text.length());
				SpanUtils.copyUniqueSpans((Spanned)tb, tbStart, tbEnd, Object.class, this, start);
			}else{
				insertForBlocks(i, index, tb, tbStart, tbEnd);
			}
		}

		mLength += -before + after; 
		//应该先调用文本块监听器刷新，因为在sendTextChanged中可能触发下次更改
		sendAfterBlocksChanged(start, before, after);
		sendTextChanged(start, before, after);
		sendAfterTextChanged();
		return this;
	}
	
	/* 从指定文本块的指定位置插入一段文本，并重新分配文本块的文本，使每个文本块的大小不超出MaxCount */
	private void insertForBlocks(final int i, final int index, CharSequence tb, int tbStart, int tbEnd)
	{
		//先插入文本，让在此范围内的span进行扩展和修正
		//注意必须立即发送事件，因为插入的位置不是末尾时，不连续的范围将错误传递
		final Editable dstBlock = mBlocks[i];
		repalceWithSpans(i, index, index, tb, tbStart, tbEnd, true, false);

		//再检查文本块的内容是否超出MaxCount
		final int newLen = dstBlock.length();	
		if(newLen > this.MaxCount)
		{								   
			//将超出的文本截取出来，需要多预留一些空间以待之后使用
			final int keepLen = this.MaxCount - ReserveCount;
			final int overLen = newLen - keepLen;
			Spanned subText = (Spanned) dstBlock.subSequence(keepLen, newLen);
			repalceWithSpans(i, keepLen, newLen, "", 0, 0, true, false);

			//将超出的文本分发到之后的文本块中，并保证它们的长度不超出MaxCount
			if(i+1 < mBlockSize && mBlocks[i+1].length()+overLen <= this.MaxCount){
				//如果有下个文本块并且它可以容纳超出的文本，将超出的文本插入下个文本块开头
				//插入前需要获取重复的span，插入后再次修正范围
				Object[] spans = checkRepeatSpans(mBlocks[i+1], subText, 0, overLen);
				repalceWithSpans(i+1, 0, 0, subText, 0, overLen, true, false);
				fixRepeatSpansRange(mBlocks[i+1], subText, spans, 0);
			}
			else{
				//如果下个文本块无法容纳超出的文本，必须分发，分发时不需要修正不连续的span，也不需要修正重复span
				//就像是将修正好的文本单独拷贝到新的文本块中，由于没有重复的span，span整体位置保持不变
				dispatchTextBlock(i+1, subText, 0, overLen, true);
			}
			//太长的文本插入时，需要调整原文本块内部的空间
			//当文本块的长度为newLen时，那么它的内部文本数组长度最大为newLen * 2，
			//考虑到初始文本块长度为ensureCount，它的内部数组长度为ensureCount * 2，
			//那么文本块长度只要最坏不达到ensureCount * 2，内部数组就不会增长为ensureCount * 4
			//因此当一个文本块内部数组长度最坏增长至ensureCount * 4时，将它调整为ensureCount * 2
			if(newLen >= keepLen * 2){ //same as (newLen * 2 >= ensureCount * 4)
				adjustBlock(i);
			}
		}
	}

	/* 删除指定范围内的文本和文本块，空文本块被移除 */
	private void deleteForBlocks(final int i, final int start, final int j, final int end)
	{
		//连续的删除，仅发送一次事件
		sendBeforeBlocksTextDeleted(i, start, j, end);
		if(i == j)
		{
			//只要删除一个文本块中的内容
			if(start==0 && end==mBlocks[i].length()){
				//全部文本删除，文本块被移除
				removeBlocks(i,i+1,true);
			}else{	
			    //否则就删除范围内的文本(not send event)
				repalceWithSpans(i,start,end,"",0,0,false,true);
				refreshInvariants();
			}	
		}
		else
		{
			//要删除多个文本块的内容
			//删除文本或文本块时，mIndexOfBlocks和mBlockStarts均可不正确
			//因此可以连续删除，仅需在最后刷新数据
			int removedBlockStartIndex = i, removedBlockEndIndex = j+1;

			if(start > 0){
				//如果起始块不会移除，就删除范围内的文本，但移除文本块起始下标加1
			    repalceWithSpans(i,start,mBlocks[i].length(),"",0,0,false,true);
				removedBlockStartIndex++;
			}
			if(end < mBlocks[j].length()){
				//如果末尾块不会移除，就删除范围内的文本，但移除文本块末尾下标减1
			    repalceWithSpans(j,0,end,"",0,0,false,true);
				removedBlockEndIndex--;
			}

			//中间的文本块会直接全部删除。如果没有文本块删除，则不需要发送事件
			if(removedBlockEndIndex > removedBlockStartIndex){//mark
				removeBlocks(removedBlockStartIndex,removedBlockEndIndex,true);
			}else{
				refreshInvariants();
			}
		}
	}

	/* 替换指定文本块的文本及span与其的绑定，若send为false，则刷新mBlockStarts是调用者的责任 */
	private void repalceWithSpans(final int i, final int start, final int end, CharSequence tb, int tbStart, int tbEnd, boolean send, boolean spanIsRemoved)
	{
		final int before = end-start;
		final int after = tbEnd-tbStart;
		final Editable dstBlock = mBlocks[i];
		if(send && before > 0){
			sendBeforeBlocksTextDeleted(i, start, i, end);
		}

		//需要在插入文本前，获取端点处无法扩展的span或会挤到后面的span，这不包含插入文本中的新span
		Object[] spans = EmptyArray.OBJECT;
		if(after > 0 && (start == 0 || start == dstBlock.length())){
			//手动插入文本时，处于文本块内的span会自己扩展和移动，但处于两端的不会，因此修正它们之前之后的衔接
			//截取的文本内包含的span，在下个文本块中可能重复，导致无法设置，因此修正它们之前的衔接
			//新添加的空文本块，仅用于拷贝截取的文本，不会获取任何span，也不用修正
			spans = dstBlock.getSpans(start,start,Object.class);
		}

		//需要在删除文本前，替换span的绑定。删除文本不需要修正span
		replaceSpans(i,start,end,tb,tbStart,tbEnd,spanIsRemoved);
		dstBlock.replace(start,end,tb,tbStart,tbEnd);
		invalidateStartMark(i);
		//需要在插入后，修正端点处的span
		if(after > 0){
			fixDiscontinuousSpans(dstBlock,spans);
		}

		if(send){
			//需要在发送事件前刷新数据
			refreshInvariants();
			if(after > 0){
				sendAfterBlocksTextInserted(i, start, i, start+after);
			}
		}
	}

	/* 移除指定文本块指定范围内的span与其的绑定(如果可以)，将插入文本范围内的span与文本块建立新的绑定 */
	private void replaceSpans(final int i, final int start, final int end, CharSequence tb, int tbStart, int tbEnd, boolean spanIsRemoved)
	{
		//先移除指定文本块start~end范围内的span与block的绑定
		//纯删除时，mIndexOfBlocks和mBlockStarts均可不正确
		final Editable dstBlock = mBlocks[i];
		if(end > start)
		{
			final boolean blockIsRemoved = start == 0 && end == dstBlock.length();
			Object[] spans = dstBlock.getSpans(start, end, Object.class);
			for(int k = 0; k < spans.length; ++k)
			{
				Object span = spans[k];
				//如果span可以被移除或文本块会被移除，则可以与文本块解除绑定
				if(blockIsRemoved || canRemoveSpan(span, start, end, dstBlock))
				{
					SpanRange spanRange = mSpanInBlocks.get(span);
					//如果span只在这一个文本块中，可以直接移除span，否则解除span与文本块的绑定
					//另一个情况是，本次不是真实的删除操作，因此暂时保留空的spanRange，以保留spanOrder
					if(spanIsRemoved && spanRange.headBlock() == spanRange.tailBlock()){
						mSpanInBlocks.remove(span);
						mSpanCount--;
					}else{
						spanRange.remove(i, dstBlock);
					}
				}	
			}
		}

		//如果要替换的文本是Spanned，范围内的span需要与block建立新的绑定
		//插入时，mIndexOfBlocks必须是正确的
		if(tbEnd > tbStart && tb instanceof Spanned)
		{
			Spanned sp = (Spanned) tb;
			Object[] spans = sp.getSpans(tbStart, tbEnd, Object.class);
			for(int k = 0; k < spans.length; ++k)
			{
				Object span = spans[k];
				SpanRange spanRange = mSpanInBlocks.get(span);
				if(spanRange == null){
					//一个全新的span，需要映射到一个新的范围
					spanRange = new SpanRange(mSpanInsertCount++);
					mSpanInBlocks.put(span, spanRange);
					mSpanCount++;
				}
				//将block加入spanRange中，并不加入重复的block
				spanRange.add(i, dstBlock);
			}
		}
	}

	/* 在文本或文本块改变后，刷新所有的数据 */
	private void refreshInvariants()
	{
		if(mLowBlockStartMark == 0){
			mBlockStarts[mLowBlockStartMark++] = 0;
		}
		for(int i=mLowBlockStartMark; i<mBlockSize; ++i){
			mBlockStarts[i] = mBlockStarts[i-1]+mBlocks[i-1].length();
		}
		for(int i=mLowBlockIndexMark; i<mBlockSize; ++i){
			mIndexOfBlocks.put(mBlocks[i], i);
		}
		mLowBlockStartMark = Integer.MAX_VALUE;
		mLowBlockIndexMark = Integer.MAX_VALUE;
	}
	/* 在添加移除文本块时，刷新mLowBlockIndexMark */
	private void invalidateIndexMark(int i){
		mLowBlockIndexMark = i<=mLowBlockIndexMark ? i:mLowBlockIndexMark;
	}
	/* 在添加移除文本块时或修改文本块的文本时，刷新mLowBlockStartMark */
	private void invalidateStartMark(int i){
		mLowBlockStartMark = i<=mLowBlockStartMark ? i:mLowBlockStartMark;
	}

	/* 在文本块修改后，修正不连续的span在该文本块中的范围，使它在多个文本块中的范围是连续的，span样本通常是取自该文本块的两端 */
	private void fixDiscontinuousSpans(Editable dstBlock, Object[] spans)
	{
		final int len = dstBlock.length();
		for(int k = 0; k < spans.length; ++k)
		{
			Object span = spans[k];
			SpanRange spanRange = mSpanInBlocks.get(span);
			//文本刚刚在dstBlock中插入，因此span也只可能在此文本块中的范围错误
			//如果span绑定了多个文本块，才需要修正，因为在单独文本块中的范围会自己改变
			if(spanRange.headBlock() != spanRange.tailBlock())
			{
				int start = dstBlock.getSpanStart(span);
				int end = dstBlock.getSpanEnd(span);
				int flags = dstBlock.getSpanFlags(span);
				if(spanRange.headBlock() == dstBlock){
					//span应衔接在起始块末尾
					if(end < len){
						dstBlock.setSpan(span,start,len,flags);
					}
				}
				else if(spanRange.tailBlock() == dstBlock){
					//span应衔接在末尾块起始
					if(start > 0){
						dstBlock.setSpan(span,0,end,flags);
					}
				}
				else if(start > 0 || end < len){
					//span应附着在整个中间块
					dstBlock.setSpan(span,0,len,flags);
				}
			}
		}
	}

	/* 检查在dst与src中重复的span，spans取自dst中的指定范围 */
	private static Object[] checkRepeatSpans(Spanned src, Spanned dst, int start, int end){
		Object[] spans = dst.getSpans(start, end, Object.class);
		return SpanUtils.checkRepeatSpans(src, spans, Object.class);
	}

	/* 将重复的span从copy中截取到block的开头后，修正那些范围不正确的span，可以指定在copy中截取的起始位置 */
	private static void fixRepeatSpansRange(Editable block, Spanned copy, Object[] spans, int start)
	{
		for(int i=0; i<spans.length; ++i)
		{
			Object span = spans[i];
			int ost = block.getSpanStart(span);
			int nst = copy.getSpanStart(span);
			nst = nst<start ? 0 : nst-start;
			if(ost != nst){
				//此时被插入的文本必然在文本块开头，因此跨越多个文本块的span必然衔接在开头或末尾，已在fixDiscontinuousSpans时修正的不用再次修正
				//另外的，完全被截取至单独的下个文本块且重复的span没有被fixDiscontinuousSpans修正，应将它衔接在上次的位置之前，spanEnd已在插入时修正
				block.setSpan(span,nst,block.getSpanEnd(span),block.getSpanFlags(span));
			}
		}
	}
	
	/**
	 * 检查该span是否是无效的span
	 * 只能设置标志为SPAN_EXCLUSIVE_EXCLUSIVE的span，因为其它标志的span边界会扩展而出现无法预料的行为
	 * 另一个原因是replace只能先删再插，所以无法保留删除范围内的span，而其它标志的span可能会保留在文本块而出现异常
	 * 也不能设置TextWatcher和SpanWatcher，因为它们在文本块被修改时会发送错误的事件，并且影响性能
	 * NoCopySpan在某些情况下不能从一个文本块中拷贝到另一个文本块中，因此为了避免异常也不能设置
	 */
	private static boolean isInvalidSpan(Object span, int start, int end, int flags){
		return start == end || (flags & SPAN_EXCLUSIVE_EXCLUSIVE) != SPAN_EXCLUSIVE_EXCLUSIVE
		       || span instanceof TextWatcher || span instanceof SpanWatcher || span instanceof NoCopySpan;
	}
	/* 检查该span是否可以从指定的文本块中移除 */
	private static boolean canRemoveSpan(Object span, int delstart, int delend, Editable block){
		int spanStart = block.getSpanStart(span);
		int spanEnd = block.getSpanEnd(span);
		return spanStart >= delstart && spanEnd <= delend;
	}
	
	public void clear(){
		replace(0, length(), "", 0, 0);
		mSpanInsertCount = 0;
	}
	public void clearSpans()
	{
		if(mSpanCount == 0){
			return;
		}
		//移除所有文本块的所有span，并移除所有绑定
		for(int i=0; i<mBlockSize; ++i){
			mBlocks[i].clearSpans();
		}
		mSpanInBlocks.clear();
		mSpanCount = 0;
		mSpanInsertCount = 0;
	}
	
	public void setSpan(final Object span, int start, int end, final int flags)
	{
		checkRange("setSpan",start,end);
		if(isInvalidSpan(span, start, end, flags)){
			//从该类创建无效跨度时，自动忽略无效跨度
			return;
		}

		SpanRange tempRange = mSpanInBlocks.get(span);
		if(tempRange != null){
			//如果已有该span，仅清空与其绑定的文本块，保留它的插入顺序
			tempRange.removeSpan(span);
		}
		else{
			//一个全新的span，需要映射到一个新的列表，并添加插入顺序
			tempRange = new SpanRange(mSpanInsertCount++);
			mSpanInBlocks.put(span,tempRange);
			mSpanCount++;
		}
		final SpanRange spanRange = tempRange;

		//将范围内的所有文本块都设置span，并建立绑定
		RangeTotal total = new RangeTotal()
		{
			@Override
			public void rangeTotal(int id, int start, int end)
			{
				Editable block = mBlocks[id];
				block.setSpan(span, start, end, flags);
				spanRange.add(block);
			}
		};
		runRangeTotal(start, end, total);
	}

	public void removeSpan(Object span)
	{
		//移除span与blocks的绑定，并将span从这些文本块中移除
	    SpanRange spanRange = mSpanInBlocks.remove(span);
		if(spanRange != null){
			spanRange.removeSpan(span);
			mSpanCount--;
		}
	}
	
	public int getSpanStart(Object span)
	{
		//获取span所绑定的第一个文本块，然后获取文本块的起始位置，并附加span在此文本块的起始位置
		SpanRange spanRange = mSpanInBlocks.get(span);
		if(spanRange == null){
			return -1;
		}
		Editable block = spanRange.headBlock();
		int id = mIndexOfBlocks.get(block);
		int start = block.getSpanStart(span);
		return mBlockStarts[id] + start;
	}
	
	public int getSpanEnd(Object span)
	{
		//获取span所绑定的最后一个文本块，然后获取文本块的起始位置，并附加span在此文本块的末尾位置
		SpanRange spanRange = mSpanInBlocks.get(span);
		if(spanRange == null){
			return -1;
		}
		Editable block = spanRange.tailBlock();
		int id = mIndexOfBlocks.get(block);
		int end = block.getSpanEnd(span);
		return mBlockStarts[id] + end;
	}
	
	public int getSpanFlags(Object span)
	{
		SpanRange spanRange = mSpanInBlocks.get(span);
		if(spanRange == null){
			return 0;
		}
		return spanRange.headBlock().getSpanFlags(span);
	}
	
	public int getSpanCount(){
		return mSpanCount;
	}
	
	public <T extends Object> T[] getSpans(int start, int end, Class<T> kind){
		return getSpans(start,end,kind,true);
	}
	/* 获取与指定范围重叠的指定类型的span，sort表示是否按优先级和插入顺序排序 */
	private <T extends Object> T[] getSpans(int start, int end, final Class<T> kind, boolean sort)
	{
		//start和end的位置可以超出文本的范围，因为寻找到的文本块不会超出文本块列表的范围
		//但是传递给文本块的搜索范围将可能超出它自己的文本范围，但这并不影响搜索与该范围重叠的span
		if(kind == null){
			return (T[])EmptyArray.OBJECT;
		}
		if(mSpanCount == 0){
			return EmptyArray.emptyArray(kind);
		}

		int i, j;
		if(start == end){
			//当获取范围为单点并且此点存在于文本块边界，要包含两端的span
			i = findBlockBeforeIndex(start);
			j = findBlockAfterIndex(end);
		}else{
			//当获取范围是一个范围，处于边界时则不用包含两端的span
			i = findBlockAfterIndex(start);
			j = findBlockBeforeIndex(end);
		}
		start -= mBlockStarts[i];
		end -= mBlockStarts[j];

		if(i == j){
			//如果获取的span仅存于单个文本块中，不必再创建map
			T[] spans = mBlocks[i].getSpans(start, end, kind);
			if(sort){
				//虽然无法保证span优先级，但是我们可以重新排序
				sortSpans(spans);
			}
			return spans;
		}

		//收集范围内所有文本块的span，并不包含重复的span
		final IdentityHashMap<T, Object> spanMap = obtainMap();
		RangeTotal total = new RangeTotal()
		{
			@Override
			public void rangeTotal(int id, int start, int end)
			{
				T[] spans = mBlocks[id].getSpans(start, end, kind);
				for(int i = 0; i < spans.length; ++i){
					spanMap.put(spans[i], null);
				}
			}
		};
		runRangeTotal(i, start, j, end, total);	

		int count = spanMap.size();
		if(count == 0){
			//没有找到span，提前回收map并返回
			recyleMap(spanMap);
			return EmptyArray.emptyArray(kind);
		}

		//创建一个指定长度的数组类型的对象并转换，然后将span转移到其中
		T[] spans = (T[]) Array.newInstance(kind, count);
		spanMap.keySet().toArray(spans);
		recyleMap(spanMap);
		if(sort){
			//虽然无法保证span优先级，但是我们可以重新排序
			sortSpans(spans);
		}
		return spans;
	}
	
	/* 将spans按优先级和插入顺序排序 */
	private <T> void sortSpans(T[] spans)
	{
		final int length = spans.length;
		final int[] prioSortBuffer = SpanUtils.obtain(length);
		final int[] orderSortBuffer = SpanUtils.obtain(length);
		for(int i = 0; i < length; ++i){
			SpanRange spanRange = mSpanInBlocks.get(spans[i]);
			prioSortBuffer[i] = spanRange.headBlock().getSpanFlags(spans[i]) & SPAN_PRIORITY;
			orderSortBuffer[i] = spanRange.spanOrder;
		}
		SpanUtils.sort(spans, prioSortBuffer, orderSortBuffer);
		SpanUtils.recycle(prioSortBuffer);
		SpanUtils.recycle(orderSortBuffer);
	}

	/* 获取临时span容器 */
	private static IdentityHashMap obtainMap()
	{
		synchronized(sMapBuffer)
		{
			for(int i = 0; i < sMapBuffer.length; ++i){
				if(sMapBuffer[i] != null){
					IdentityHashMap buffer = sMapBuffer[i];
					sMapBuffer[i] = null;
					return buffer;
				}
			}
		}
		return new IdentityHashMap();
	}
	/* 回收临时span容器 */
	private static void recyleMap(IdentityHashMap buffer)
	{
		buffer.clear();
		synchronized(sMapBuffer)
		{
			for(int i = 0; i < sMapBuffer.length; ++i){
				if(sMapBuffer[i] == null){
					sMapBuffer[i] = buffer;
				}
			}
		}
	}

	/**
	 * 返回在start之后但小于或等于limit的下一个偏移量，此偏移量是距离start最近的一个span的端点
	 * 如果在start ~ limit之间没有一个span的端点，则返回limit
	 */
	public int nextSpanTransition(final int start, final int limit, Class kind)
	{
		if(mSpanCount == 0){
			return limit;
		}
		if(kind == null){
			kind = Object.class;
		}

		//先走到start指定的文本块
		int i = findBlockAfterIndex(start);
		Editable block = mBlocks[i];
		int length = block.length();
		int blockStart = mBlockStarts[i];

		if (start - blockStart < length){
			//文本块还有剩余文本，将文本块剩余的范围找完
			int next = block.nextSpanTransition(start-blockStart, limit-blockStart, kind);
			if (next < length || (next == length && hasSpanPointAt(this, blockStart+length))){
				return next+blockStart;
			}
		}

		for(++i; i < mBlockSize; ++i)
		{
			block = mBlocks[i];
			length = block.length();
		    blockStart = mBlockStarts[i];
			int next = block.nextSpanTransition(0, limit - blockStart, kind);
			if (next < length || (next == length && hasSpanPointAt(this, blockStart+length))){
				//在文本块中找到了span则返回它的位置，没有找到span会返回limit-blockStart
				//不管找没找到，只有next小于length，才能说明在自身中找到了span，或者没找到但已经到达limit，此时返回
				//反之，如果next大于length，则说明一定没找到，并且limit还在自己之后，还需要在之后的文本块中找
				//但无法判断当limit==length时，不知该span是否在之后有衔接，因此额外判断
				return next+blockStart;
			}
		}
		return limit;
	}
	
	/* 是否有span的端点正好处于文本中的指定位置 */
	private static boolean hasSpanPointAt(Spanned text, int offset)
	{
		Object[] spans = text.getSpans(offset, offset, Object.class);
		for(int i = 0; i < spans.length; ++i){
			if(text.getSpanStart(spans[i]) == offset || text.getSpanEnd(spans[i]) == offset){
				return true;
			}
		}
		return false;
	}
	
	public int length(){
		return mLength;
	}
	public char charAt(int index)
	{
		int len = mLength;
        if (index < 0) {
            throw new IndexOutOfBoundsException("charAt: " + index + " < 0");
        } else if (index >= len) {
            throw new IndexOutOfBoundsException("charAt: " + index + " >= length " + len);
        }
		//先走到start指定的文本块
		int i = findBlockAfterIndex(index);
		int start = mBlockStarts[i];
		return mBlocks[i].charAt(index - start);
	}
	public void getChars(int start, int end, final char[] arr, final int index)
	{
		checkRange("getChars", start, end);
		//收集范围内所有文本块的字符，存储到arr中
		RangeTotal total = new RangeTotal()
		{
			private int getCharsCount = 0;
			
			@Override
			public void rangeTotal(int id, int start, int end)
			{
				mBlocks[id].getChars(start, end, arr, index+getCharsCount);
				getCharsCount += end-start;
				//累计已经获取的字符数，便于计算下块的字符在arr的起始获取位置
			}
		};
		runRangeTotal(start, end, total);
	}
	
	public CharSequence subSequence(int start, int end){
		return new EditableList(this, start, end);
	}
	public String toString()
	{
		int len = mLength;
        char[] buf = new char[len];
        getChars(0, len, buf, 0);
        return new String(buf);
	}

	public void setFilters(InputFilter[] filters)
	{
		if(filters == null){
			throw new IllegalArgumentException();
		}
		mFilters = filters;
	}
	public InputFilter[] getFilters(){
		return mFilters;
	}
	
	/* 检查范围，并抛出异常 */
    private void checkRange(final String operation, int start, int end)
	{
        if (end < start) {
            throw new IndexOutOfBoundsException(operation + " " +
                                                region(start, end) + " has end before start");
        }
		if (start < 0 || end < 0){
			throw new IndexOutOfBoundsException(operation + " " +
                                                region(start, end) + " start before 0 ");
		}
        int len = mLength;
        if (start > len || end > len) {
            throw new IndexOutOfBoundsException(operation + " " +
                                                region(start, end) + " ends beyond length " + len);
        }
    }
	private static String region(int start, int end) {
        return "(" + start + " ... " + end + ")";
    }

	/* 发送文本块事件 */
	private void sendBlocksAdded(int i, int count)
	{
		if(mBlockListener!=null){
			for(int k=0; k<count; ++k){
				mBlockListener.onBlockAdded(i+k);
			}
		}
	}
	private void sendBlocksRemoved(int i, int j)
	{
		if(mBlockListener!=null){
			for(int k=j-1; i<=k; --k){
				mBlockListener.onBlockRemoved(k);
			}
		}
	}
	private void sendBeforeBlocksTextDeleted(int i, int iStart, int j, int jEnd)
	{
		if(mBlockListener!=null)
		{
			RangeTotal total = new RangeTotal()
			{
				@Override
				public void rangeTotal(int id, int start, int end){
					mBlockListener.beforeBlockTextDeleted(id, start, end);
				}
			};
			runRangeTotal(i, iStart, j, jEnd, total);
		}
	}
	private void sendAfterBlocksTextInserted(int i, int iStart, int j, int jEnd)
	{
		if(mBlockListener!=null)
		{
			RangeTotal total = new RangeTotal()
			{
				@Override
				public void rangeTotal(int id, int start, int end){
					mBlockListener.afterBlockTextInserted(id, start, end);
				}
			};
			runRangeTotal(i, iStart, j, jEnd, total);
		}
	}
	private void sendAfterBlocksChanged(int start, int before, int after){
		if(mBlockListener!=null){
			mBlockListener.afterBlocksChanged(start, before, after);
		}
	}

	/* 发送文本事件 */
	private void sendBeforeTextChanged(int start, int before, int after)
	{
        mTextWatcherDepth++;
        if(mTextWatcher!=null){
			mTextWatcher.beforeTextChanged(this,start,before,after);
		}
        mTextWatcherDepth--;
    }
    private void sendTextChanged(int start, int before, int after) 
	{
        mTextWatcherDepth++;
		if(mTextWatcher!=null){
			mTextWatcher.onTextChanged(this, start, before, after);
		}     
        mTextWatcherDepth--;
    }
    private void sendAfterTextChanged()
	{
        mTextWatcherDepth++;
		if(mTextWatcher!=null){
            mTextWatcher.afterTextChanged(this);
		}
        mTextWatcherDepth--;
    }
	public int getTextWatcherDepth() {
        return mTextWatcherDepth;
    }
	
	
	/* 对每个文本块要分配的任务 */
	private static interface RangeTotal
	{
		public void rangeTotal(int id, int start, int end)
	}
	
	/* 将start~end的范围拆分为文本块的范围，并逐块调用 */
	private void runRangeTotal(int start, int end, RangeTotal total)
	{
		if(start == end){
			//当范围为一点并且此点存在于文本块边界，i和j将会反了
			return;
		}
		
		//找到start和end所指定的文本块，并将它们偏移到文本块的下标
		int i = findBlockAfterIndex(start);
		int j = findBlockBeforeIndex(end);
		start -= mBlockStarts[i];
		end -= mBlockStarts[j];

		//从起始块开始，调至末尾块
		runRangeTotal(i, start, j, end, total);
	}
	/* 从(i, iStart)开始，调至(j, jEnd), 分别调用它们之间的每一个文本块 */
	private void runRangeTotal(int i, int iStart, int j, int jEnd, RangeTotal total)
	{
		//从起始块开始，调至末尾块
		if(i == j){
			total.rangeTotal(i, iStart, jEnd);
		}
		else if(i < j){
			total.rangeTotal(i, iStart, mBlocks[i].length());
			for(++i; i < j; ++i){
				total.rangeTotal(i, 0, mBlocks[i].length());
			}
			total.rangeTotal(j, 0, jEnd);
		}
	}
	
	/* 
	  至此有两种不同的错误:
	  1、以单链表存储所有文本块，在插入时直接通过插入文本块下标相对起始文本块下标的偏移作为count，向后遍历count个节点作为插入位置，
	    错误: 当插入文本后[at, not, not, insert, not, at...]，此时链表第一块实际上是第一个at，那么向后走3步其实不对，它跳到最后一个at后了，
		即使先删再插，那么算出的count是负数，虽然插在开头就可以了，但是先删再插很麻烦
	  2、以范围block存储，当插入文本后[at, not, insert, not, at]，此时startBlock是第一个at，endBlock为最后一个，
	    那么startBlock不变，但是插完后删除第一个at时，默认跳到下个not，startBlock错了
		
	  改进建议:
	  1、以单链表存储所有文本块，在插入时逐一比较来确定位置，或者在插入时直接通过插入文本块下标相对起始文本块下标的偏移作为count，但是需要先删再插
	  2、以范围block存储，先删再插，指定spanIsRemoved，防止span完全移除导致order错误
	  
	  效率评价:
	  1、以单链表存储所有文本块内存占用超高，查找效率超低，无论怎么优化也没用，不建议使用
	  2、以范围block存储，内存占用小，查找效率高
	  
	  再次确认: 以中括号来表示范围内span附着于文本块的情况，后跟括号表示spanRange内的起始和末尾文本块
	  当以范围block存储时，纯删除时没事，插入时只扩展一个文本块也没事，
	  完全移除: [at, not](0, 0) → [not, not](-1, -1) → [not, at](1, 1)
	  向前衔接: [at, not](0, 0) → [at, at](0 / before 0, 1);
	  向后衔接: [at, at](0, 1) → [not / at, at](0, 1) / (1, 1);
	  
	  比较麻烦的是dispatch，我们看看:
	  完全移除: [at, not, not](0, 0) → [not, not, not](-1, -1) → [not, not, at, at](插入自第一个at时，2, 2，之后再后连续插入，变为2，3)
	  向前衔接: [at, at, not, not](0, 1) → [at, at, at, not](首先无法从块中移除，因此相当于从原范围直接向后扩展)
	  向后衔接: [at, at, ](0, 1) → [at, insert, at](如果不会移除，则不改变)
	          [at, at] → [not, at](1, 1) → [not, not, at, not, at](从此时重新确定起始位置) → [not, not, at, at, at](从中间插入)
	  如果完全包含修改范围，则不改变[at, at, at, at]
	*/
	
	/* 移除span时，先replace再removeBlock反而错了，应该同时做 */
	
	/**
	 * spanRange.add方法检查添加文本块是否在范围内，如果不在就扩展范围到那个文本块，
	 * 比较自由，无论一边replaceSpans一边addBlock，还是先addBlocks再全部replaceSpans都可以
	 *
	 * spanRange.remove方法就比较麻烦了，换句话说，我不知道删除起始和末尾文本块时应该跳到哪，
	 * 因此我只能尝试跳到下一块和上一块，步长只有1
	 *
	 * 为什么在insertForBlocks中必须先删再插？因为删除的步长只有1，必须删除了才能跳到正确位置，之后插入时扩展也是正常的，
	 * 如果先插再删，那么起始范围会停留在这里，最后删除时根本不知道要跳到哪里
	 * 
	 * 直接删除单块文本没什么，但是如果要连续删除多块文本并且它们即将移除，必须一边replaceSpans一边removeBlock，
	 * 只有这样span才能跳到删除文本块范围的两边(说白了就是单独一块块删除)
	 */
	
	/**
	 * 存储span附着于文本块列表中的文本块范围和插入顺序，存储文本块而不是下标，因为下标会变化
	 * 由于大部分情况下都是增加和删除文本块，并且几乎只会获取首尾文本块，因此这里使用范围block
	 * 范围block以(startBlock，endBlock)来表示span附着于文本块列表中的(起始文本块，末尾文本块)
	 */
	private final class SpanRange
	{
		private Editable startBlock;
		private Editable endBlock;
		private final int spanOrder;

		public SpanRange(int order){
			spanOrder = order;
			startBlock = endBlock = null;
		}
		
		/* 直接在最后添加一个新文本块 */
		public void add(Editable dstBlock)
		{
			if(startBlock == null){
				startBlock = endBlock = dstBlock;
			}else{
				endBlock = dstBlock;
			}
		}
		
		/* span被加入index所指定的文本块时，添加span与文本块的绑定 */
		public void add(int index, Editable dstBlock)
		{
			//如果加入的文本块的位置在旧范围之外，需要扩展范围并包含该文本块
			//在start之前的文本块会被startBlock跳转包含，而不改变endBlock
			//在end之后的文本块会被endBlock包含，而不改变startBlock
			if(startBlock == null){
				startBlock = endBlock = dstBlock;
				return;
			}
			int start = mIndexOfBlocks.get(startBlock);
			int end = mIndexOfBlocks.get(endBlock);
			if(index < start){
				startBlock = dstBlock;
			}
			else if(index > end){
				endBlock = dstBlock;
			}
		}
		
		/* span从指定的文本块中移除时，移除span与文本块的绑定 */
		public void remove(int index, Editable dstBlock)
		{
			//如果删除位置在文本块范围两边，需要缩小该范围
			//startBlock被删除时会向后走一步，包含删除位置后的文本块
			//endBlock被删除时会向前走一步，包含删除位置前的文本块
			if(startBlock == endBlock){
				if(startBlock == dstBlock)
					startBlock = endBlock = null;
			}
			else if(startBlock == dstBlock){
				startBlock = mBlocks[index + 1];
			}
			else if(endBlock == dstBlock){
				endBlock = mBlocks[index - 1];
			}
		}
		
		/* 从所有记录的文本块中移除span，并清空所有记录的文本块 */
		public void removeSpan(Object span)
		{
			int start = mIndexOfBlocks.get(startBlock);
			int end = mIndexOfBlocks.get(endBlock);
			for(;start <= end; ++start){
				mBlocks[start].removeSpan(span);
			}
			startBlock = endBlock = null;
		}
		
		/* 获取span附着于文本块列表的第一个文本块，
		 * 如果span没有附着于文本块，则返回null
		 */
		public Editable headBlock(){
			return startBlock;
		}
		/* 获取span附着于文本块列表的最后一个文本块，
		 * 如果span没有附着于文本块，则返回null
		 */
		public Editable tailBlock(){
			return endBlock;
		}
	}
}
