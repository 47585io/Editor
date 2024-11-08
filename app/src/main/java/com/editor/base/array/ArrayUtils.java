package com.editor.base.array;

import java.lang.reflect.*;
import java.util.*;

public final class ArrayUtils
{
	private static final int ALIGNMENT_AMOUNT = 8;
	
	/* 创建一个新的指定元素个数的数组，可能比size更大，但这会利用空闲的空间 */
	public static<T> T[] newUnpaddedArray(Class<T> kind, int size){
		return (T[])Array.newInstance(kind,getAlignmentMinimumSize(kind,size));
	}
	
	public static boolean[] newUnpaddedBooleanArray(int size){
		return new boolean[getAlignmentMinimumSize(boolean.class,size)];
	}
	
	public static byte[] newUnpaddedByteArray(int size){
		return new byte[getAlignmentMinimumSize(byte.class,size)];
	}
	
	public static char[] newUnpaddedCharArray(int size){
		return new char[getAlignmentMinimumSize(char.class,size)];
	}
	
	public static int[] newUnpaddedIntArray(int size){
		return new int[getAlignmentMinimumSize(int.class,size)];
	}
	
	public static long[] newUnpaddedLongArray(int size){
		return new long[getAlignmentMinimumSize(long.class,size)];
	}

	public static float[] newUnpaddedFloatArray(int size){
		return new float[getAlignmentMinimumSize(float.class,size)];
	}
	
	public static double[] newUnpaddedDoubleArray(int size){
		return new double[getAlignmentMinimumSize(double.class,size)];
	}
	
	//计算指定元素类型且指定元素个数的数组的内存对齐后的最小元素个数
	private static int getAlignmentMinimumSize(Class kind, int size)
	{
		if(kind==boolean.class || kind==byte.class){
			size = getAlignmentMinimumSize(1,size);
		}
		else if(kind==char.class){
			size = getAlignmentMinimumSize(2,size);
		}
		else if(kind==int.class || kind==float.class){
			size = getAlignmentMinimumSize(4,size);
		}
		else if(kind==long.class || kind==double.class){
			size = getAlignmentMinimumSize(8,size);
		}
		else{
			size = getAlignmentMinimumSize(4,size);
		}
		return size;
	}
	
	//数组要占用的内存长度没有对齐，就将其对齐到下个位置，计算对齐后的元素个数
	private static int getAlignmentMinimumSize(int kind, int size)
	{
		int len = size * kind;
		int over = len % ALIGNMENT_AMOUNT;
		if(over != 0){
			len += ALIGNMENT_AMOUNT - over;
			size = len / kind;
		}
		return size;
	}
	
	/* 在数组中向后寻找指定元素，找到了返回它的下标，从index开始 */
	public static <T> int indexOf(T[] array, T value, int index)
	{
        if (array == null || index<0) return -1;
        for (; index < array.length; index++) {
            if (Objects.equals(array[index], value)) return index;
        }
        return -1;
    }
	/* 在数组中向前寻找指定元素，找到了返回它的下标，从index开始 */
	public static <T> int lastIndexOf(T[] array, T value, int index)
	{
        if (array == null || index>=array.length) return -1;
        for (;index >= 0; index--) {
            if (Objects.equals(array[index], value)) return index;
        }
        return -1;
    }
	
	public static int indexOf(char[] array, char value, int index, int end)
	{
        if (array == null || index < 0 || end >= array.length) return -1;
        for (; index <= end; index++) {
            if (array[index] == value) return index;
        }
        return -1;
    }
	public static int lastIndexOf(char[] array, char value, int index, int begin)
	{
        if (array == null || index >= array.length || begin < 0) return -1;
        for (;index >= begin; index--) {
            if (array[index] == value) return index;
        }
        return -1;
    }
	
	public static int indexOf(int[] array, int value, int index)
	{
        if (array == null || index<0) return -1;
        for (; index < array.length; index++) {
            if (array[index] == value) return index;
        }
        return -1;
    }
	public static int lastIndexOf(int[] array, int value, int index)
	{
        if (array == null || index>=array.length) return -1;
        for (;index >= 0; index--) {
            if (array[index] == value) return index;
        }
        return -1;
    }
	
	/**
	 * 在有序的区间列表中寻找指定的index，返回包含该index的区间(在列表中的位置，这个位置是区间起始位置的数在数组中的下标)
	 * 区间列表是按正序排列的一组数，每个数和紧挨的数构成一个区间，每个数是该区间的起始位置，也是上个区间的末尾位置
	 * 返回包含该index的区间(rangeStart <= index < rangeEnd)(在列表中的位置)
	 * 如果寻找的index不被包含在区间列表中并且index小于区间列表第一个数，则返回0
	 * 如果寻找的index不被包含在区间列表中并且index大于区间列表最后一个数，则返回count - 1
	 * 如果区间列表中有多个相同的数并且都与index相等，返回最后一个数(在列表中的位置)
	 * 如果区间列表长度为0，则返回0
	 *
	 * @param rangeList 区间数组（按升序排列）
	 * @param count 数组长度
	 * @param index 要查找的索引
	 * @return 区间在列表中的位置
	 */
	public static int findRangeContainingIndex(int[] rangeList, int count, int index)
	{
		int high = count, low = -1, guess;
        while (high - low > 1) {
            guess = (high + low) >> 1;
            if (rangeList[guess] > index)
                high = guess;
            else
                low = guess;
		}
        if (low < 0) return 0;
        return low;
	}
}
