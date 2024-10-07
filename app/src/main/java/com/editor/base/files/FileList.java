package com.editor.base.files;
import java.io.*;
import java.util.*;

/**
 * 每一次的增删文件都会往文件系统中增加一个文件，而文件系统中的文件是乱序的，那每一次增删文件都要重获取所有文件？
 * 当然不需要，因为我们使用sortFilelist来顺序存储当前目录所有文件对象，当增删一个文件，就同时对sortFilelist进行修改
 * 这样，无论如何sortFilelist中的文件都保持顺序同步，而获取一个文件，只是获取sortFilelist中的文件对象，不需要重新获取全部文件
 * 当然，特殊情况是需要访问上级或下级目录，此时才重新获取并排序全部文件
 */
public class FileList implements Comparator<File>
{
	private File nowDir;
	private ArrayList<File> sortFileList;
	private FileFilter mFileFilter;
	private FileChangeListener mFileListener;

	public static final int PARENT_INDEX = -1;
	private static final String PATH_SPILT = "/";
	private static final String END_PATH = "/storage/emulated/0";

	public FileList(){
		nowDir = new File(END_PATH);
		sortFileList = new ArrayList<>();
		//refreshData();
	}

	public void setFileFilter(FileFilter filter){
		mFileFilter = filter;
	}
	public void setFileChangeListener(FileChangeListener li){
		mFileListener = li;
	}
	public File getCurrentDirectory(){
		return nowDir;
	}
	public ArrayList<File> getCurrentFiles(){
		return sortFileList;
	}

	/* 选中指定下标的文件，如果是一个目录会进入目录 */
	public File selectedFile(int index)
	{
		if (index == PARENT_INDEX){
			//如果index为PARENT_INDEX，则获取并进入父目录
			return jumpToDirectory(nowDir.getParentFile());
		}
		return jumpToDirectory(sortFileList.get(index));
	}
	
	public File jumpToDirectory(File file)
	{
		if(file.isDirectory() && file.canRead()){
			//如果是一个目录，则进入目录
			nowDir = file;
			refreshData();
		}
		return file;
	}

	public void createFile(String name)
	{
		File file = new File(nowDir.getPath() + PATH_SPILT + name);
		try{
			file.createNewFile();
			onCreateFile(file);
		}catch (IOException e){}
	}
	
	public void createDirectory(String name)
	{
		File file = new File(nowDir.getPath() + PATH_SPILT + name);
		file.mkdir();
		onCreateFile(file);
	}
	
	private void onCreateFile(File file)
	{
		int index = findInsertedIndex(file);
		sortFileList.add(index, file);
		if(mFileListener != null){
			mFileListener.onFileCreated(index, file);
		}
	}

	public void deleteFile(int index)
	{
		File file = sortFileList.remove(index);
		file.delete();
		if(mFileListener != null){
			mFileListener.onFileDeleted(index, file);
		}
	}

	public void renameFile(int index, String name)
	{
		File file = new File(nowDir.getPath() + PATH_SPILT + name);
		sortFileList.get(index).renameTo(file);
		if(mFileListener!=null){
			mFileListener.onFileRenamed(index, file);
		}
	}

	public void refreshData()
	{
		File[] files = mFileFilter != null ? nowDir.listFiles(mFileFilter) : nowDir.listFiles();
		Arrays.sort(files, this);
		sortFileList.clear();
		sortFileList.addAll(Arrays.asList(files));
		if(mFileListener != null){
			mFileListener.onFilesRefreshed(files);
		}
	}

	private int findInsertedIndex(File insert)
	{
		int size = sortFileList.size();
		for(int i = 0; i < size; ++i){
			File source = sortFileList.get(i);
			//后面的文件比它大了，它应插入这里
			if(compare(source, insert) >= 0){
				return i;
			}
		}
		//它是最大的，插入列表的末尾
		return size;
	}

	@Override
	public int compare(File f1, File f2)
	{
		//目录放在文件之前
		if(f1.isDirectory() && f2.isFile()){
			return -1;
		}
		if(f1.isFile() && f2.isDirectory()){
			return 1;
		}
		//都是文件或都是目录，逐字符比较它们名字的ASCII码
		return f1.getName().compareTo(f2.getName());
	}

	public static interface FileChangeListener
	{
		public void onFilesRefreshed(File[] files)

		public void onFileCreated(int index, File file)

		public void onFileDeleted(int index, File file)

		public void onFileRenamed(int index, File file)
	}
}
