package com.editor.text;
import android.text.*;
import com.editor.base.array.*;

/**
 * 建立在分块文本容器上的Layout，
 * 通过记录每个文本块的行和宽度信息来节省测量时间，
 * 监听文本块的变化并调整该文本块的信息，分块可以尽量少地测量文本，
 * 优化了BaseLayout的部分方法，例如getLineWidth，getOffsetHorizontal，getOffsetForHorizontal
 */
public class BlockLayout extends BaseLayout implements BlockListener
{
	private int mLineCount;        //换行符的数量
	private int mBlockSize;        //文本块个数
	private TextBlock[] mBlocks;   //每个文本块的换行符的数量
	private int[] mBlockStartLine; //每个文本块的起始行数(累计于前面文本块)

	public BlockLayout(EditableList base, TextPaint paint, int tabSize, int lineColor, float lineSpacing)
	{
		super(base, paint, tabSize, lineColor, lineSpacing);
		mLineCount = 0;
		mBlockSize = 0;
		mBlocks = EmptyArray.emptyArray(TextBlock.class);
		mBlockStartLine = EmptyArray.INT;
		
		//测量所有文本块以初始化数据
		int count = base.getBlockSize();
		for(int i = 0; i < count; ++i){
			onBlockAdded(i);
			afterBlockTextInserted(i, 0, base.getBlock(i).length());
		}
		afterBlocksChanged(0, 0, base.length());
		//等待后续的测量
		base.setBlockListener(this);
	}
	
	/* 返回行数，行数是换行符数量加1 */
	public int getLineCount(){
		return mLineCount + 1;
	}

	/* 寻找指定行的起始位置 */
	public int getLineStart(int line)
	{
		if(line < 1) return 0;
		if(line > mLineCount) return getText().length();
		
		int id = getLineAtBlock(line);
		int startLine = mBlockStartLine[id];
		int startIndex = ((EditableList)getText()).getBlockStart(id);
		int toLine = line - startLine;
		int offset = mBlocks[id].getLineBlockStart(toLine);
		return startIndex + offset;
	}

	/* 寻找指定的offset所在的行 */
	public int getLineForOffset(int offset)
	{
		if(offset <= 0) return 0;
		if(offset >= getText().length()) return mLineCount;
		
		EditableList text = (EditableList) getText();
		int id = text.findBlockAfterIndex(offset);
		int startLine = mBlockStartLine[id];
		int startIndex = text.getBlockStart(id);
		int index = offset - startIndex;
		int kid = mBlocks[id].findLineBlockContainingIndex(index);
		return startLine + kid;
	}

	public void onBlockAdded(int i)
	{
		mBlocks = GrowingArrayUtils.insert(mBlocks, mBlockSize, i, new TextBlock());
		mBlockStartLine = GrowingArrayUtils.insert(mBlockStartLine, mBlockSize, i, 0);
		mBlockSize++;
	}

	public void onBlockRemoved(int i)
	{
		mBlocks = GrowingArrayUtils.remove(mBlocks, mBlockSize, i);
		mBlockStartLine = GrowingArrayUtils.remove(mBlockStartLine, mBlockSize, i);
		mBlockSize--;
	}

	public void beforeBlockTextDeleted(int i, int start, int end)
	{
		Editable block = ((EditableList)getText()).getBlock(i);
		int delete = mBlocks[i].beforeBlockTextDeleted(block, start, end);
		mLineCount -= delete;
	}

	public void afterBlockTextInserted(int i, int start, int end)
	{
		Editable block = ((EditableList)getText()).getBlock(i);
		int insert = mBlocks[i].afterBlockTextInserted(block, start, end);
		mLineCount += insert;
	}

	public void afterBlocksChanged(int start, int before, int after)
	{
		//刷新每个文本块的起始行数
		//文本变化后数组长度应该大于0，因为数组只会增长而不会缩小
		mBlockStartLine[0] = 0;
		for(int i = 1; i < mBlockSize; ++i){
			mBlockStartLine[i] = mBlockStartLine[i-1] + mBlocks[i-1].lineCount;
		}
		
		//测量文本宽度，只需测量修改范围涉及到的行即可，因为文本块的调整不会影响行的布局
		//删除文本时，只需测量单点所在的行，这可能是两行连接到一起
		//插入文本时，只需测量插入范围的行，如果插入了换行符会将插入点所在的行的文本拆为两半
		//只有与插入文本连接了的才需要测量，因为被拆分的行会更短，除此之外，测量中间所有自成一行的文本
		int startLine = getLineForOffset(start);
		int endLine = getLineForOffset(start + after);
		for(; startLine <= endLine; ++startLine){
			float width = getLineWidth(startLine);
			increaseWidthTo(width);
		}
	}

	/**
	 * 将文本按指定长度分块，它可以平衡读写效率
	 * 首先我们记录文本块中的每个换行符在文本块中的位置
	 * 这里在前面的基础上，将每个文本块所占的每行的范围分为一个单独的块
	 * 对于以下的文本，我们有(中括号括起来的是单个文本块)
	 *
	 *  [the first 
	 *   block end，][the second
	 *   block，][thread block][fourth block]
	 *
	 *  line 0: one block (len: 9, width: 180)
	 *  line 1: two block (len: 10, width: 200) and (len: 10, width: 200)
	 *  line 2: three block more...
	 *  
	 *  block 0: sub with 2 lines: (0 ~ 1)
	 *  block 1: sub with 2 lines: (1 ~ 2)
	 *  block 2: in singleline (2)
	 *  block 3: in singleline (2)
	 *
	 * 如果行的文本较短，那么单个文本块将包含多行以减少数组长度，节省修改效率
	 * 如果行的文本较长，那么多个文本块会将该行分块，以均衡读取效率
	 */
	private class TextBlock
	{
		private int lineCount;   //文本块中的换行符数量
		private int[] lineIndex; //文本块中的每个换行符在文本块中的下标
		private float[] lineBlockWidth; //文本块中的每一行的宽度，不包含换行符
		
		public TextBlock(){
			this("", 0, 0);
		}
		public TextBlock(CharSequence text, int start, int end){
			lineCount = 0;
			lineIndex = EmptyArray.INT;
			lineBlockWidth = EmptyArray.FLOAT;
			reflow(text, start, end);
		}
		
		/**
		 * lineBlockWidth的长度总是比lineIndex长度多1
		 * 因此我们将第一个lineBlock定义为从文本块起始位置开始到第0个换行符位置的范围
		 * 最后一个lineBlock总是文本块最后一个换行符位置到文本块末尾位置的范围
		 * 其它lineBlock的范围就是两换行符之间的范围，lineBlock不包含换行符
		 *
		 * 如果文本块第一个字符为换行符，则第一个lineBlock长度为0
		 * 如果文本块最后一个字符为换行符，则最后一个lineBlock长度为0
		 * 如果文本块中有连续的两个换行符，则两换行符之间的lineBlock长度为0
		 * 如果文本块的换行符为0，则只有一个lineBlock并且表示整个文本块范围
		 * lineBlockWidth则是所有这些lineBlock的宽度
		 */
		
		public int getLineCount(){
			return lineCount;
		}
		public int getLineBlockStart(int line)
		{
			if(lineCount == 0 || line == 0){
				return 0;
			}
			return lineIndex[line - 1] + 1;
		}
		public int getLineBlockEnd(int blockLength, int line)
		{
			if(lineCount == 0 || line == lineCount){
				return blockLength;
			}
			return lineIndex[line];
		}
		public float getLineBlockWidth(int line){
			return lineBlockWidth[line];
		}
		public int findLineBlockContainingIndex(int index)
		{
			int kid = ArrayUtils.findRangeContainingIndex(lineIndex, lineCount, index);
			if(lineCount > 0 && index > lineIndex[kid]){
				//如果文本块没有换行符，kid = 0
				//如果index在换行符之后，kid++(跳到下个行块)
				//如果index在换行符之前或命中换行符(仍在上一行)，不变
				kid++;
			}
			return kid;
		}
		
		/**
		 * 对于文本块本身的内容必须在文本块变化时立即测量，被修改的文本块也可能并不仅仅存在于插入文本范围内
		 * 因为文本块会截取后面的文本并分发而不知去向，如果该截取范围在插入范围后，受到影响的文本块可能还在更后头
		 */
		 
		public int beforeBlockTextDeleted(Editable block, int start, int end)
		{
			final int oldLineCount = lineCount;
			int length = block.length() - end + start;
			char[] chars = RecylePool.obtainCharArray(length);
			
			//获取删除范围两侧的文本来测量
			TextUtils.getChars(block, 0, start, chars, 0);
			TextUtils.getChars(block, end, block.length(), chars, start);
			reflow(chars, length);
			RecylePool.recyleCharArray(chars);
			return oldLineCount - lineCount;
		}
		
		public int afterBlockTextInserted(Editable block, int start, int end)
		{
			final int oldLineCount = lineCount;
			reflow(block, 0, block.length());
			return lineCount - oldLineCount;
		}
		
		public void adjustBlock(Editable block){
			reflow(block, 0, block.length());
		}
		
		private void reflow(CharSequence text, int start, int end)
		{
			int length = end - start;
			char[] chars = RecylePool.obtainCharArray(length);
			TextUtils.getChars(text, start, end, chars, 0);
			reflow(chars, length);
			RecylePool.recyleCharArray(chars);
		}
		
		/* 重新测量整个文本块，并调整占用的空间，使其尽可能小 */
		private void reflow(char[] chars, int length)
		{
			//统计文本换行符数量
			lineCount = 0;
			for(int i = 0; i < length; ++i){
				if(chars[i] == '\n'){
					lineCount++;
				}
			}
			
			//根据换行符数量调整数组长度
			int capacity1 = calcCapacity(lineCount, lineIndex.length);
			int capacity2 = calcCapacity(lineCount+1, lineBlockWidth.length);
			if(capacity1 != lineIndex.length){
				lineIndex = ArrayUtils.newUnpaddedIntArray(capacity1);
			}
			if(capacity2 != lineBlockWidth.length){
				lineBlockWidth = ArrayUtils.newUnpaddedFloatArray(capacity2);
			}
			
			//记录文本中的换行符位置
			lineCount = 0;
			for(int i = 0; i < length; ++i){
				if(chars[i] == '\n'){
					lineIndex[lineCount] = i;
					lineCount++;
				}
			}
			
			//测量文本块每一行的宽度
			TextPaint paint = getPaint();
			for(int line = 0; line <= lineCount; ++line){
				int start = getLineBlockStart(line);
				int end = getLineBlockEnd(length, line);
				lineBlockWidth[line] = measureText(chars, start, end, paint);
			}
		}
		
		private int calcCapacity(int count, int length)
		{
			final int ensureCount = count + 10;
			if(length < count || length > ensureCount){
				return ensureCount;
			}
			return length;
		}
	}
	
	
	/* 用于顺序遍历指定行的所有块的接口 */
	private static abstract class LineBlockSeer
	{
		public abstract boolean nextBlock(Editable text, TextBlock block, int line)
		
		public void joinBlock(Editable text, TextBlock block, int line)
		{
			int blockStart = block.getLineBlockStart(line);
			int blockEnd = block.getLineBlockEnd(text.length(), line);
			float blockWidth = block.getLineBlockWidth(line);
			int count = blockEnd - blockStart;
			this.start += count;
			this.point += blockWidth;
		}
		
		public void joinBlock(int blockStart, int blockEnd, float blockWidth)
		{
			int count = blockEnd - blockStart;
			this.start += count;
			this.point += blockWidth;
		}
		
		public int start;
		public double point;
	}
	
	/* 顺序遍历指定行的所有文本块，这些文本块中的某行文本与该行文本重叠 */
	private void foreachLineBlocks(int line, LineBlockSeer seer)
	{
		//找到该行的第一块文本块i
		//找到该行的最后一块文本块j
		int i = getLineAtBlock(line);
		int j = getLineAtBlock(line + 1);
		EditableList eList = (EditableList) getText();
		
		//如果i == j，说明行块被包含在该文本块中，则只需找到单个文本块中的这个行块
		//如果i < j，说明行跨越多个文本块，则从第i块的最后一块开始，一直遍历到第j块的第一块，中间块全范围调用
		//多块文本块时，可能会有空的行块，但并不影响结果
		if(i == j){
			int at = line - mBlockStartLine[i];
			seer.nextBlock(eList.getBlock(i), mBlocks[i], at);
		}
		else if(i < j){
			boolean need = seer.nextBlock(eList.getBlock(i), mBlocks[i], mBlocks[i].lineCount);
			if(need){
				for(++i; i <= j; ++i){
					need = seer.nextBlock(eList.getBlock(i), mBlocks[i], 0);
					if(!need) break;
				}
			}
		}
	}
	
	/**
	 * 获取第line个换行符所在的文本块，也就是最后一个mStartLines为line - 1的块
	 * 文本块的起始行数实际上是之前的文本块的行数(例如第二块文本块的起始行数就是第一块的行数)
	 * 这意味着，要寻找指定的换行所在的文本块，需要先找到它等于某个文本块的起始行数的文本块，此换行应该在它之前的文本块
	 * 为了避免单行文本太长跨越了多个文本块，导致很多文本块的startLine相同，所以这里应该找到最后面的文本块，也就是换行符的那个
	 */
	private int getLineAtBlock(int line){
		return ArrayUtils.findRangeContainingIndex(mBlockStartLine, mBlockSize, line - 1);
	}

	@Override
	public float getLineWidth(int line)
	{
		if(line < 0) line = 0;
		else if(line > mLineCount) line = mLineCount;
		
		LineBlockSeer seer = new LineBlockSeer(){
			public boolean nextBlock(Editable text, TextBlock block, int line){
				joinBlock(text, block, line);
				return true;
			}
		};
		foreachLineBlocks(line, seer);
		return (float)seer.point;
	}

	@Override
	public float getOffsetHorizontal(int offset)
	{
		if(offset < 0) offset = 0;
		else if(offset > getText().length()) offset = getText().length();
		
		int lineNum = getLineForOffset(offset);
		final int lineOff = offset - getLineStart(lineNum);
		LineBlockSeer seer = new LineBlockSeer(){
			public boolean nextBlock(Editable text, TextBlock block, int line)
			{
				int blockStart = block.getLineBlockStart(line);
				int blockEnd = block.getLineBlockEnd(text.length(), line);
				float blockWidth = block.getLineBlockWidth(line);
				int blockLength = blockEnd - blockStart;
				
				if (this.start + blockLength > lineOff){
					int overLength = lineOff - this.start;
					if (overLength <= blockLength / 2){
						this.point += measureText(text, blockStart, blockStart+overLength, getPaint());
					}else{
						this.point = this.point + blockWidth - measureText(text, blockStart+overLength, blockEnd, getPaint());
					}
					return false;
				}
				joinBlock(blockStart, blockEnd, blockWidth);
				return true;
			}
		};
		foreachLineBlocks(lineNum, seer);
		return (float)seer.point;
	}

	@Override
	public int getOffsetForHorizontal(int line, final float horiz)
	{
		if(line < 0) line = 0;
		else if(line > mLineCount) line = mLineCount;
		
		LineBlockSeer seer = new LineBlockSeer(){
			public boolean nextBlock(Editable text, TextBlock block, int line)
			{
				int blockStart = block.getLineBlockStart(line);
				int blockEnd = block.getLineBlockEnd(text.length(), line);
				float blockWidth = block.getLineBlockWidth(line);
				
				if(this.point + blockWidth > horiz){
					int offset = measureOffsetOpened(text, blockStart, blockEnd, this.point, horiz, getPaint());
					this.start += offset - blockStart;
					return false;
				}
				joinBlock(blockStart, blockEnd, blockWidth);
				return true;
			}
		};
		foreachLineBlocks(line, seer);
		return getLineStart(line) + seer.start;
	}
	
	/* 当进行与文本无关的修改，如修改paint.textSize，则可以调用该方法刷新Layout */
	public void adjustLayout()
	{
		super.adjustLayout();
		EditableList eList = (EditableList) getText();
		for(int i = 0; i < mBlockSize; ++i){
			mBlocks[i].adjustBlock(eList.getBlock(i));
		}
		afterBlocksChanged(0, 0, eList.length());
	}
}
