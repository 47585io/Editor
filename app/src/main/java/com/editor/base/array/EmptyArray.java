package com.editor.base.array;
import java.lang.reflect.*;

public final class EmptyArray
{
	public static final Object[] OBJECT = new Object[0];

	public static final boolean[] BOOLEAN = new boolean[0];
	
	public static final byte[] BYTE = new byte[0];
	
	public static final char[] CHAR = new char[0];
	
	public static final int[] INT = new int[0];
	
	public static final float[] FLOAT = new float[0];
	
	public static final long[] LONG = new long[0];

	public static final double[] DOUBLE = new double[0];
	
	
	private static final int CACHE_SIZE = 73;
	
	private static Object[] sCache = new Object[CACHE_SIZE];
	
	public static <T> T[] emptyArray(Class<T> kind)
	{
        if (kind == Object.class) {
            return (T[]) EmptyArray.OBJECT;
        }
		//去除最高位后，为其在sCache中选择一个位置(相同的对象进行相同计算，将获得相同的值)
        int bucket = (kind.hashCode() & 0x7FFFFFFF) % CACHE_SIZE;
        Object cache = sCache[bucket];
        if (cache == null || cache.getClass().getComponentType() != kind) {
			//第一次获取，或者键计算冲突，则新创建一个
            cache = Array.newInstance(kind, 0);
            sCache[bucket] = cache;
        }
        return (T[]) cache;
    }
}
