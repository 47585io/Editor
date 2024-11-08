package com.editor.text;

import java.util.*;
import com.editor.base.array.*;
import android.graphics.*;
import android.text.*;
import android.text.style.*;

/**
 * 简单的Layout，尽可能节省时间，所以不会使用nextSpanTranstion，并尽量少用getSpans
 * 只绘制BackgroundColorSpan和ForegroundColorSpan，为预算好宽高的文本附加样式，不会处理字体大小，边距 ，或大块背景图案
 */
public abstract class BaseLayout
{
	private static final char FT = '\t', SPACE = ' ', FREE = '_', USE = '-';
	private static final float LFS = 0.25f, LRS = 2.5f, CWS = 0.175f;
	
	private CharSequence mText;
	private TextPaint mPaint;
	private float mWidth;
	private float mLineSpacing;
	private int mLineColor;
	private int mTabSize;
	
	protected BaseLayout(CharSequence base, TextPaint paint, int tabSize, int lineColor, float lineSpacing)
	{
		mText = base;
		mPaint = paint;
		mWidth = 0;
		mTabSize = tabSize;
		mLineColor = lineColor;
		mLineSpacing = lineSpacing;
	}

	public final void setLineNumColor(int color){
		mLineColor = color;
	}
	public final void setLineSpacing(float lineSpacing){
		mLineSpacing = lineSpacing;
	}
	public final void setTabSize(int tabSize){
		mTabSize = tabSize;
	}
	public final int getLineNumColor(){
		return mLineColor;
	}
	public final float getLineSpacing(){
		return mLineSpacing;
	}
	public final int getTabSize(){
		return mTabSize;
	}
	
	public final void increaseWidthTo(float width){
		if(width > mWidth){
			mWidth = width;
		}
	}
	public void adjustLayout(){}

	/**
	 * 在绘制文本前200000行时还是正常的，然后之后再往后滚动就有点偏移，到800000行时就已经有肉眼可见的偏移
	 * 我仔细观察了一下，发现光标的位置正确，但是文本错了
	 * 计算的起始行和末尾行正确，但从测量的位置就可以看到末尾行文本提前截断，但光标仍保持在正确的行位置
	 * 当我把光标点在可见的第一行时，光标与文本偏移较小
	 * 当我把光标从第一行向下挪动时，光标与文本的偏移越来越大
	 * 所以我猜测在每次画完一行后画下一行时，递增的行位置有一点偏移
	 * 在很多次递增后偏移逐渐加大到可怕的程度
	 * 可是很奇怪，我的y += lineHeight;逻辑没问题
	 *
	 * 绘制位置的偏移其实是浮点数计算精度误差
	 * 由于浮点数在内存中的存储方式并不精确，当太小或太大的浮点数进行计算时误差会较大
	 * 而在y += lineHeight时，在前200000行时高度比较小所以误差不大，而在之后随着绘制高度越大误差也越大
	 * 光标的位置由于只进行一次计算所以误差不大，但是文本位置需要不断使用y += lineHeight，累计的误差会越来越大
	 * 解决办法相当简单，就是把所有需要参与计算的浮点数的类型改为整数，让计算结果变成整数，可以避免较大误差
	 * 不过要注意让每个地方的计算保持一致，否则两边计算的位置会对不上
	 *
	 * 另一个问题是，单行文本太长导致坐标累加偏移
	 * 由于用paint测量的文本宽度只能是浮点数，所以这里x计算时可以用double来尽量保证精度
	 */

	/**
	 * 如果文本的行太多并且滚动到底部吋会导致绘制位置坐标太大，需要划动一段较长的距离文本位置才会变化，划动时感觉文本一刻一刻的
	 * 这个问题我以前在写图片处理软件时也遇到过，当时记得是图像缩放倍数太大时滑动时一刻一刻的
	 * 其实也是因为数字太大或太小然后浮点数精度问题，这个问题几乎没办法解决
	 *
	 * 想象浮点数最小的那一位上的数(0.00000001)，超出浮点数精度表示范围的数不会生效(比如0.00000001 + 0.00000000001 = 0.00000001)
	 * 那么如果图片缩放太大了，你在屏幕上滑动的距离变换到图像绘制偏移距离时太小了，所以并不生效
	 * 只有当你在屏幕上滑动的距离变换到图像绘制偏移距离至少达到下个0.00000001的位置时，图像的绘制位置才受影响
	 * 而且由于图像缩放了，所以图像上0.00000001的距离在屏幕上是一段较长的距离
	 * 所以看起来需要划动一段较长的距离图像位置才会变化，划动时感觉图像一刻一刻的
	 *
	 * 太大的数在浮点表示中可能导致精度丢失，尤其是当涉及到运算时
	 * 由于浮点数的格式限制，只有有限的有效位数，对于很大的数，计算时可能无法保持所有位数的精确度，导致舍入误差
	 *
	 * 其实你可能觉得奇怪，我只是滚动图像，每行的绘制位置又不变，就好像把可见区域的选择框向下拖动一下，怎么文本会一刻一刻，
	 * 其实滚动图像本身也是设置平移来实现的，图像的实际绘制位置会进行变换后画在指定位置，而所谓变换就是浮点数的计算
	 * 所以这不又回来了吗: 数字太大或太小然后计算导致浮点数精度问题，导致绘制位置有偏差
	 *
	 * 这个问题有解决办法但是过于复杂了，因为它需要重写很多内容
	 * 首先在View那边不要对canvas进行平移变换(也不要scroll)，你自己用整数记录每次的scroll
	 * 然后在Layout这边绘制时不要canvas.getClipBounds(rect)，你自己传一个参数Rect，这是你在Editor那边用scroll算的
	 * 然后每次绘制时让Layout自己去偏移绘制位置，原来从startLine*lineHeight开始绘制，现在从startLine*lineHeight - See.top开始
	 * x绘制位置也要减See.left，由于全程是你自己用整数计算的，所以几乎没有偏移
	 * 至于缩放我还没想好怎么解决
	 */
	
	/* 绘制画布可视区域的文本 */
	public void draw(Canvas canvas, int cursorOffsetVertical)
	{
		//获取可视区域
		Rect see = new Rect();
		if(!canvas.getClipBounds(see)){
			return;
		}
		
		//计算可视区域的行
		int startLine = getLineForVertical(see.top);
		int endLine = getLineForVertical(see.bottom);
		//使用当前画笔的属性初始化数据
		int lineHeight = getLineHeight();
		int tyOffset = getTextOffset();
		
		//在0的左侧绘制行数，绘制的是行号而不是行的下标
		if(see.left < -getUnitCharcterWidth() * LRS){
			float leftPadding = -getLineNumberWidth() +getUnitCharcterWidth()*LFS;
			int highlightLine = cursorOffsetVertical / lineHeight;
			drawLineNumber(startLine+1, endLine+1, highlightLine+1, leftPadding, startLine*lineHeight, lineHeight, tyOffset, canvas, mPaint);
		}

		//一行行绘制文本
		for(int line = startLine; line <= endLine; ++line){
			//获取每一行的可见起始位置和末尾位置
			int start = getOffsetForHorizontal(line, see.left);
			float x = getOffsetHorizontal(start);
			int end = measureOffsetClosed(mText, start, getLineEnd(line), x, see.right, mPaint);
			int y = line * lineHeight;
			//绘制该行的可见范围的文本
			if(end > start){
				drawSingleLineText(mText, start, end, x, y, lineHeight, tyOffset, canvas, mPaint);
			}
		}
	}
	
	/* 在指定的位置开始绘制一列范围内的行数，高亮的行用文本颜色绘制 */
	private void drawLineNumber(int startLine, int endLine, int highlightLine, float x, int y, int lineHeight, int tyOffset, Canvas canvas, TextPaint paint)
	{
		int textColor = paint.getColor();
		paint.setColor(mLineColor);
		//从起始行开始，绘制到末尾行
		for(;startLine<=endLine;++startLine)
		{
			String line = String.valueOf(startLine);
			if(startLine == highlightLine){
				paint.setColor(textColor);
				canvas.drawText(line,x,y+tyOffset,paint);
				paint.setColor(mLineColor);
			}else{
				canvas.drawText(line,x,y+tyOffset,paint);
			}
			y += lineHeight;
		}
		paint.setColor(textColor);
	}
	
	/* 从(x, y)开始，以单行绘制text中指定范围的文本和span */
	private void drawSingleLineText(CharSequence text, int start, int end, double x, int y, int lineHeight, int tyOffset, Canvas canvas, TextPaint textPaint)
	{
		int length = end - start;
		char[] chars = RecylePool.obtainCharArray(length);
		char[] table = RecylePool.obtainCharArray(length);
		float[] widths = RecylePool.obtainFloatArray(length);
		
		TextUtils.getChars(text, start, end, chars, 0);
		getTextWidths(chars, 0, length, textPaint, widths);
		Arrays.fill(table, 0, length, FREE);
		
		if(text instanceof Spanned)
		{
			Spanned sp = (Spanned) text;
			CharacterStyle[] spans = sp.getSpans(start, end, CharacterStyle.class);
			int spanCount = spans.length;
			
			//先绘制背景的span
			for(int k = 0; k < spanCount; ++k)
			{
				Object span = spans[k];
				if(span instanceof BackgroundColorSpan)
				{
					//将span偏移到数组范围
					int spanStart = sp.getSpanStart(span) - start;
					int spanEnd = sp.getSpanEnd(span) - start;
					//超出可见范围的内容不绘制
					if(spanStart < 0){
						spanStart = 0;
					}
					if(spanEnd > length){
						spanEnd = length;
					}
					if(spanEnd > spanStart){
						int saveColor = textPaint.getColor();
						textPaint.setColor(((BackgroundColorSpan)span).getBackgroundColor());
						double xStart = x + togetherWidth(widths, 0, spanStart);
						double width = togetherWidth(widths, spanStart, spanEnd);
						canvas.drawRect((float)xStart, y, (float)(xStart+width), y+lineHeight, textPaint);
						textPaint.setColor(saveColor);
					}
				}
			}

			//反向绘制文本的span，并用范围填充表，每个字符只能被绘制一次
			for(int k = spanCount-1; k >= 0; --k)
			{
				Object span = spans[k];
				if(span instanceof ForegroundColorSpan)
				{
					int spanStart = sp.getSpanStart(span) - start;
					int spanEnd = sp.getSpanEnd(span) - start;
					if(spanStart < 0){
						spanStart = 0;
					}
					if(spanEnd > length){
						spanEnd = length;
					}
					if(spanEnd > spanStart){
						int saveColor = textPaint.getColor();
						textPaint.setColor(((ForegroundColorSpan)span).getForegroundColor());
						double xStart = x + togetherWidth(widths, 0, spanStart);
						drawTextWithCharcterStop(chars, table, widths, spanStart, spanEnd, xStart, y+tyOffset, canvas, textPaint);
						textPaint.setColor(saveColor);
					}
				}
			}
		}
		
		//检查本行剩余的未绘制文本，并绘制出来
		drawTextWithCharcterStop(chars, table, widths, 0, length, x, y+tyOffset, canvas, textPaint);
		
		RecylePool.recyleCharArray(chars);
		RecylePool.recyleCharArray(table);
		RecylePool.recyleFloatArray(widths);
	}
	
	/* 绘制单行文本，并不绘制已绘制的位置或不可绘制的字符，并用范围填充表 */
	private static void drawTextWithCharcterStop(char[] chars, char[] table, float[] widths, int start, int end, double x, float y, Canvas canvas, TextPaint paint)
	{
		while(start < end)
		{
			//寻找下个可绘制范围
			int csStart = findNextDrawStart(chars, table, widths, start, end);
			int csEnd = findNextDrawEnd(chars, table, widths, csStart, end);
			if (csStart >= end){
				break;
			}
			//绘制范围内的文本并填充表
			x += togetherWidth(widths, start, csStart);
			canvas.drawText(chars, csStart, csEnd-csStart, (float)x, y, paint);
			x += togetherWidth(widths, csStart, csEnd);
			Arrays.fill(table, csStart, csEnd, USE);
			start = csEnd;
		}
	}
	
	private static int findNextDrawStart(char[] chars, char[] table, float[] widths, int start, int end)
	{
		for(; start < end; ++start){
			if(needDrawAt(chars, table, widths, start)){
				break;
			}
		}
		return start;
	}
	private static int findNextDrawEnd(char[] chars, char[] table, float[] widths, int start, int end)
	{
		for(; start < end; ++start){
			if(!needDrawAt(chars, table, widths, start)){
				break;
			}
		}
		return start;
	}
	private static boolean needDrawAt(char[] chars, char[] table, float[] widths, int i){
		return table[i] == FREE && chars[i] != FT && chars[i] != SPACE; //某些BMP字符可能会占用连续的几个字符，并且其中的某个字符宽度为0
	}
	
	/* 累计数组中的指定范围内的字符的宽度 */
	private static double togetherWidth(float[] widths, int start, int end)
	{
		double width = 0;
		for(;start<end;++start){
			width += widths[start];
		}
		return width;
	}

	public final CharSequence getText(){
		return mText;
	}
    public final TextPaint getPaint(){
		return mPaint;
	}
	public final float getWidth(){
		return mWidth;
	}
	public final int getHeight(){
		return getLineCount()*getLineHeight();
	}
	public final float getLineNumberWidth(){
		return mPaint.measureText(String.valueOf(getLineCount())) + getUnitCharcterWidth() * (LFS + LRS);
	}
	
	private final float getUnitCharcterWidth(){
		return mPaint.measureText(" ");
	}
	private final int getTextOffset()
	{
		Paint.FontMetrics font = mPaint.getFontMetrics();
		float height = font.bottom - font.top;
		float spacingAdd = mLineSpacing - 1;
		float textOffset = height * spacingAdd / 2;
		return (int)(textOffset - font.ascent);
	}
	
	/** 
	 * 行数以下标的形式记录，第一行的下标为0，第二行的下标为1...
	 * 在之后的方法中，传递以及返回的行，都是行的下标
	 */

	/* 获取行数，行数是最后一行的下标加1 */
	public abstract int getLineCount()

	/**
	 * 获取指定行的宽度，不包含换行符的宽度 
	 * 如果line < 0，则获取第0行的宽度
	 * 如果line >= lineCount，则获取最后一行的宽度
	 */
	public float getLineWidth(int line){
		int start = getLineStart(line); 
		int end = getLineEnd(line);
		return measureText(mText,start,end,mPaint);
	}
	/* 获取行的高度，返回的高度附加了lineSpacing */
	public final int getLineHeight(){
		Paint.FontMetrics font = mPaint.getFontMetrics();
		float height = font.bottom - font.top;
		return (int)(height * mLineSpacing);
	}

	/**
	 * 获取指定行的顶部纵坐标，
	 * 如果line < 0，则返回第0行的顶部(0)，
	 * 如果line > lineCount，则返回最后一行的底部(getHeight)
	 */
	public final int getLineTop(int line){
		int lineCount = getLineCount();
		line = line < 0 ? 0 : (line > lineCount ? lineCount : line);
		return line * getLineHeight();
	}
	
	/* 获取指定行的底部纵坐标，等同于getLineTop(line + 1) */
	public final int getLineBottom(int line){
		return getLineTop(line + 1);
	}
	
	/**
	 * 获取指定的纵坐标所在的行
	 * 如果请求的位置在 0 以上，则获取 0；
	 * 如果请求的位置在文本底部以下，则获取最后一行(lineCount-1)
	 */
	public final int getLineForVertical(int vertical) {
        int lineCount = getLineCount();
		int line = vertical / getLineHeight();
		line = line < 0 ? 0 : (line >= lineCount ? lineCount-1 : line);
		return line;
	}

	/**
	 * 获取指定行在文本中的末尾下标，该下标是这行的换行符下标 
	 * 如果line < 0，则返回第0行的末尾位置
	 * 如果line >= lineCount，则返回最后一行的末尾位置(mText.length())
	 */
	public final int getLineEnd(int line){
		if (line >= getLineCount() - 1){
			return mText.length();
		} 
		return getLineStart(line + 1) - 1;
	}
	
	/**
	 * 获取指定行在文本中的起始下标(该行第0个字符的下标)，
	 * 如果line < 0，则返回 0
	 * 如果line >= lineCount，则返回mText.length()
	 */
	public abstract int getLineStart(int line)

	/**
	 * 获取文本中指定下标所在的行，
	 * 如果offset < 0，则返回 0
	 * 如果offset > mText.length()，则返回最后一行(lineCount-1)
	 */
	public abstract int getLineForOffset(int offset)

	/**
	 * 获取文本中指定offset的横坐标，
	 * 如果offset < 0，则返回0
	 * 如果offset > mText.length()，则返回最后一行的最后一个字符之后的位置
	 */
	public float getOffsetHorizontal(int offset){
		if(offset < 0) offset = 0;
		else if(offset > mText.length()) offset = mText.length();
		int start = getOffsetToLeftOf(offset);
		return measureText(mText,start,offset,mPaint);
	}
	/**
	 * 获取文本中指定offset的纵坐标，
	 * 如果offset < 0，则返回0，
	 * 如果offset > mText.length()，则返回最后一行的顶部位置
	 */
	public final int getOffsetVertical(int offset){
		return getLineForOffset(offset) * getLineHeight();
	}
	
	/**
	 * 获取指定行且指定横坐标处的offset，不会包含该行的换行符
	 * 如果line < 0，则首先锁定第0行，如果line >= lineCount，则首先锁定最后一行
	 * 如果horiz < 0，则获取该行的第0个字符的下标，如果horiz > getLineWidth(line)，则获取该行的最后一个字符的下标(换行符)
	 */
	public int getOffsetForHorizontal(int line, float horiz){
		int start = getLineStart(line);
		int end = getLineEnd(line);
		return measureOffsetOpened(mText,start,end,0,horiz,mPaint);
	}
	/* 获取指定坐标处的offset，不会包含该行的换行符 */
	public final int getOffsetForPosition(float x, float y){
		int line = getLineForVertical((int)y);
		int count = getOffsetForHorizontal(line, x);
		return count;
	}

	/* 获取offset所在行的起始下标 */
	public final int getOffsetToLeftOf(int offset){
		int line = getLineForOffset(offset);
		return getLineStart(line);
	}
	/* 获取offset所在行的末尾的换行符下标 */
	public final int getOffsetToRightOf(int offset){
		int line = getLineForOffset(offset);
		return getLineEnd(line);
	}
	
	/* 获取指定行的矩形区域 */
	public void getLineBounds(int line, Rect bounds)
	{
		int lineHeight = getLineHeight();
		bounds.left = 0;
		bounds.top = line*lineHeight;
		bounds.right = (int)(bounds.left+mWidth);
		bounds.bottom = bounds.top+lineHeight;
	}
	/* 获取光标的路径 */
	public void getCursorPath(int point, Path dest)
	{
		float width = CWS * getUnitCharcterWidth();
		int lineHeight = getLineHeight();
		float x = getOffsetHorizontal(point);
		int y = getOffsetVertical(point);
		dest.moveTo(x-width/2, y);
		dest.lineTo(x+width/2, y);
		dest.lineTo(x+width/2, y+lineHeight);
		dest.lineTo(x-width/2, y+lineHeight);
		dest.close();
	}
	/* 获取选择区域的路径 */
	public void getSelectionPath(int start, int end, Path dest)
	{
		int startLine = getLineForOffset(start);
		int endLine = getLineForOffset(end);
		for(int line = startLine; line <= endLine; ++line){
			int lineStart = getLineStart(line);
			int lineEnd = getLineEnd(line);
			int csStart = Math.max(lineStart, start);
			int csEnd = Math.min(lineEnd, end);
			addSelectionRect(line, csStart, csEnd, dest);
		}
	}
	private void addSelectionRect(int line, int start, int end, Path dest)
	{
		float xStart = getOffsetHorizontal(start);
		float xEnd = getOffsetHorizontal(end);
		int yStart = getLineTop(line);
		int yEnd = getLineBottom(line);
		if(start == end && end < mText.length()){
			xEnd = xStart + getUnitCharcterWidth();
		}
		dest.addRect(xStart, yStart, xEnd, yEnd, Path.Direction.CW);
	}

	/* 测量文本时一并附加特殊字符的宽度，使用double累计宽度以在测量长文本时确保精度 */
	public final float measureText(CharSequence text, int start, int end, TextPaint paint)
	{
		int count = end - start;
		float[] widths = RecylePool.obtainFloatArray(count);
		getTextWidths(text, start, end, paint, widths);
		float width = (float) togetherWidth(widths, 0, count);
		RecylePool.recyleFloatArray(widths);
		return width;
	}
	
	public final float measureText(char[] chars, int start, int end, TextPaint paint)
	{
		int count = end - start;
		float[] widths = RecylePool.obtainFloatArray(count);
		getTextWidths(chars, start, end, paint, widths);
		float width = (float) togetherWidth(widths, 0, count);
		RecylePool.recyleFloatArray(widths);
		return width;
	}
	
	/* 测量单行文本中，指定位置的下标，如果该位置处于字符内，返回字符左侧的下标 */
	public final int measureOffsetOpened(CharSequence text, int start, int end, double fromx, double tox, TextPaint paint)
	{
		//不必直接获取所有内容，可以每次测量100长度，不断向后走
		final int once = 100;
		float[] widths = RecylePool.obtainFloatArray(once);
		while(start < end)
		{
			boolean find = false;
			int count = Math.min(end-start, once);
			getTextWidths(text, start, start+count, paint, widths);
			for(int i = 0; i < count; ++i){
				if(fromx + widths[i] > tox){
					find = true;
					break;
				}
				start++;
				fromx += widths[i];
			}
			if(find){
				break;
			}
		}
		RecylePool.recyleFloatArray(widths);
		return start;
	}
	
	/* 测量单行文本中，指定位置的下标，如果该位置处于字符内，返回字符右侧的下标 */
	public final int measureOffsetClosed(CharSequence text, int start, int end, double fromx, double tox, TextPaint paint)
	{
		//不必直接获取所有内容，可以每次测量100长度，不断向后走
		final int once = 100;
		float[] widths = RecylePool.obtainFloatArray(once);
		while(start < end)
		{
			boolean find = false;
			int count = Math.min(end-start, once);
			getTextWidths(text, start, start+count, paint, widths);
			for(int i = 0; i < count; ++i){
				if(fromx >= tox){
					find = true;
					break;
				}
				start++;
				fromx += widths[i];
			}
			if(find){
				break;
			}
		}
		RecylePool.recyleFloatArray(widths);
		return start;
	}
	
	public final void getTextWidths(CharSequence text, int start, int end, TextPaint paint, float[] widths)
	{
		int length = end - start;
		char[] chars = RecylePool.obtainCharArray(length);
		TextUtils.getChars(text,start,end,chars,0);
		getTextWidths(chars,0,length,paint,widths);
		RecylePool.recyleCharArray(chars);
	}
	
	/* 获取文本中所有字符的宽度，特殊字符额外处理 */
	public final void getTextWidths(char[] chars, int start, int end, TextPaint paint, float[] widths)
	{
		int count = end - start;
		float tabWidth = mTabSize * paint.measureText(" ");
		paint.getTextWidths(chars,start,count,widths);
		//将Tab的宽度替换为指定大小
		for(int i=0; i<count; ++i){
			if(chars[i+start] == FT){
				widths[i] = tabWidth;
			}
		}
	}
	
	/* 回收池 */
	protected static final class RecylePool
	{
		private static final int MAX_SIZE = 10000;
		private static final char[][] sCharArrays = new char[3][0];
		private static final float[][] sFloatArrays = new float[3][0];
		
		public static char[] obtainCharArray(int size)
		{
			synchronized(sCharArrays)
			{
				for(int i=0;i<sCharArrays.length;++i)
				{
					char[] array = sCharArrays[i];
					if (array!=null && array.length>=size) {
						sCharArrays[i] = null;
						return array;
					}
				}
			}
			return ArrayUtils.newUnpaddedCharArray(GrowingArrayUtils.growSize(size));
		}
		public static void recyleCharArray(char[] array)
		{
			if(array.length > MAX_SIZE){
				return;
			}
			synchronized (sCharArrays)
			{
				for (int i=0;i<sCharArrays.length;i++) 
				{
					if (sCharArrays[i] == null || array.length > sCharArrays[i].length) {
						sCharArrays[i] = array;
						break;
					}
				}
			}
		}
		public static float[] obtainFloatArray(int size)
		{
			synchronized(sFloatArrays)
			{
				for(int i=0;i<sFloatArrays.length;++i)
				{
					float[] array = sFloatArrays[i];
					if (array!=null && array.length>=size) {
						sFloatArrays[i] = null;
						return array;
					}
				}
			}
			return ArrayUtils.newUnpaddedFloatArray(GrowingArrayUtils.growSize(size));
		}
		public static void recyleFloatArray(float[] array)
		{
			if(array.length > MAX_SIZE){
				return;
			}
			synchronized (sFloatArrays)
			{
				for (int i=0;i<sFloatArrays.length;i++) 
				{
					if (sFloatArrays[i] == null || array.length > sFloatArrays[i].length) {
						sFloatArrays[i] = array;
						break;
					}
				}
			}
		}
	}
}
