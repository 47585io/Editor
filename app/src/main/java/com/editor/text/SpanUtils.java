package com.editor.text;
import android.text.*;
import com.editor.base.array.*;
import java.lang.reflect.*;
import java.util.*;

public class SpanUtils
{
	/* 获取指定文本范围内的所有span，如果文本不为Spanned，则返回空数组 */
	public static<T> T[] getSpans(CharSequence text, int start, int end, Class<T> kind)
	{
		if(text instanceof Spanned){
			return ((Spanned)text).getSpans(start, end, kind);
		}
		return EmptyArray.emptyArray(kind);
	}
	
	/**
	 * 将源文本中(start...end)的span复制到目标文本的(destoff...destoff+end-start)
	 * 源文本在start之前开始的跨度，或在end之后结束但与此范围重叠的跨度将被修剪，就像它们从start开始或从end结束一样
	 * 若span已经存在于目标文本中，则不会重新设置(换句话说，该span在目标文本中的位置不变)
	 * 如果只想单纯地复制而不需要管理重复的span，您应该使用TextUtils.copySpansFrom()
	 */
	public static<T> void copyUniqueSpans(Spanned source, int start, int end, Class<T> kind, Spannable dest, int destoff)
	{
        if (kind == null) {
            kind = (Class<T>) Object.class;
        }
        Object[] spans = source.getSpans(start, end, kind);
        for (int i = 0; i < spans.length; i++) 
		{
			Object span = spans[i];
			if(dest.getSpanStart(span) < 0)
			{
				int st = source.getSpanStart(span);
				int en = source.getSpanEnd(span);
				int fl = source.getSpanFlags(span);
				if (st < start) st = start;
				if (en > end) en = end;
				dest.setSpan(span, st - start + destoff, en - start + destoff, fl);
			}
        }
    }
	
	/* 检查数组中的span是否已经存在于source中，返回一个包含所有重复的span的数组 */
	public static<T> T[] checkRepeatSpans(Spanned source, T[] spans, Class<T> kind)
	{
		List<T> resultList = new ArrayList<>();
		for(int i = 0; i < spans.length; i++){
			if(source.getSpanStart(spans[i]) >= 0){
				resultList.add(spans[i]);
			}
		}
		T[] result = (T[]) Array.newInstance(kind, resultList.size());
		return resultList.toArray(result);
	}

	/* 获取临时排序数组 */
	static int[] obtain(final int elementCount)
    {
        int[] result = null;
        synchronized (sCachedIntBuffer)
        {
            //如果找不到第一个可用的tmp数组，请尝试查找长度至少为elementCount的tmp数组
            int candidateIndex = -1;
            for (int i = sCachedIntBuffer.length - 1; i >= 0; --i)
            {
                if (sCachedIntBuffer[i] != null)
                {
                    if (sCachedIntBuffer[i].length >= elementCount) {
                        candidateIndex = i;
                        break;
                    } else if (candidateIndex == -1) {
                        candidateIndex = i;
                    }
                }
            }
            if (candidateIndex != -1) {
                result = sCachedIntBuffer[candidateIndex];
                sCachedIntBuffer[candidateIndex] = null;
            }
        }
		if (result == null || elementCount > result.length) {
            result = ArrayUtils.newUnpaddedIntArray(GrowingArrayUtils.growSize(elementCount));
        }
        return result;
    }

	/* 回收临时排序数组 */
    static void recycle(int[] buffer)
    {
		if(buffer.length > 10000){
			return;
		}
        synchronized (sCachedIntBuffer)
        {
            for (int i = sCachedIntBuffer.length-1; i >= 0; --i) 
            {
                if (sCachedIntBuffer[i] == null || buffer.length > sCachedIntBuffer[i].length) {
                    sCachedIntBuffer[i] = buffer;
                    break;
                }
            }
        }
    }
	
	/** 
	 * 迭代堆排序实现。它将首先按照优先级，然后按照插入顺序对跨度进行排序
	 * 优先级较高的范围将在优先级较低的范围之前。
	 * 如果优先级相同，跨度将按照插入顺序排序。
	 * 具有较低插入顺序的*范围将在具有较高插入顺序的范围之前。*
	 * @param array跨度要排序的数组。
	 * @param priority的优先级
	 * @param insertionOrder对象类型的插入顺序。
	 * @param <T> 该方法拷贝于Android源码
	 */
    static final <T> void sort(T[] array, int[] priority, int[] insertionOrder) 
    {
		final int size = array.length;
        //从最后一个节点的父节点开始，向前将所有节点排序，构建一个大顶堆
        for (int i = size / 2 - 1; i >= 0; i--) {
            siftDown(i, array, size, priority, insertionOrder);
        }
        //从最后一个节点开始，向前一个个交换位置来排序
        for (int i = size - 1; i > 0; i--) 
        {
            //每次将节点i与根节点交换位置，使得最大的值移至末尾，末尾的值移至开头
            final T tmpSpan =  array[0];
            array[0] = array[i];
            array[i] = tmpSpan;
            final int tmpPriority =  priority[0];
            priority[0] = priority[i];
            priority[i] = tmpPriority;
            final int tmpOrder =  insertionOrder[0];
            insertionOrder[0] = insertionOrder[i];
            insertionOrder[i] = tmpOrder;

            //交换完成后，需要维护堆的顺序，仅需维护根节点的路径，并且堆的节点个数少1
            siftDown(0, array, i, priority, insertionOrder);
        }
    }

    /** 
	 * 堆的维护函数
	 * @param index 要维护的元素的索引。
	 * @param array 要排序的数组。
	 * @param size  当前堆大小。
	 * @param priority 数组元素的优先级。
	 * @param insertionOrder 数组元素的插入顺序。
	 */
    private static final <T> void siftDown(int index, T[] array, int size, int[] priority, int[] insertionOrder) 
    {
        //从index的左子节点开始
        int left = 2 * index + 1;
        while (left < size)
        {
            if (left < size - 1 && compareSpans(left, left + 1, priority, insertionOrder) < 0) {
                //如果左子节点小于右子节点，右子节点是最大的(left++)，否则左子节点最大(left)
                left++;
            }
            if (compareSpans(index, left, priority, insertionOrder) >= 0) {
                //将index节点与其左右子节点中最大的节点比较，若此节点比它的子节点都大，则路径下的顺序已经正确了，不用比较了
                break;
            }

            //将index指向的节点与其左右子节点中最大的节点交换位置
            final T tmpSpan =  array[index];
            array[index] = array[left];
            array[left] = tmpSpan;
            final int tmpPriority =  priority[index];
            priority[index] = priority[left];
            priority[left] = tmpPriority;
            final int tmpOrder =  insertionOrder[index];
            insertionOrder[index] = insertionOrder[left];
            insertionOrder[left] = tmpOrder;

            //向下走到最大的子节点，并在之后与其左右子节点比较
            index = left;
            left = 2 * index + 1;
        }
    }

    /** 
	 * 比较数组中的两个span元素。比较首先基于区间的优先级标志，然后是区间的插入顺序
	 * @param left 要比较的元素的左索引。
	 * @param right 要比较的其他元素的右索引。
	 * @param priority span优先级
	 * @param insertionOrder span插入顺序
	 * @return 0代表两元素相等，-1代表左元素小于右元素，1代表左元素大于右元素
	 */
    private static final int compareSpans(int left, int right, int[] priority, int[] insertionOrder)
    {
        int priority1 = priority[left];
        int priority2 = priority[right];
        if (priority1 == priority2) {
			int order1 = insertionOrder[left];
			int order2 = insertionOrder[right];
            return order1<order2 ? -1 : (order1==order2 ? 0 : 1);
        }
        //因为高优先级必须在低优先级之前，所以要比较的参数与插入顺序检查相反
        return priority2<priority1 ? -1 : 1;
    }
	
	private static final int[][] sCachedIntBuffer = new int[6][0];
}
