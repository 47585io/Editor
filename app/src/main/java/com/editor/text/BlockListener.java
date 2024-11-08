package com.editor.text;

interface BlockListener
{
	public void onBlockAdded(int i)

	public void onBlockRemoved(int i)

	public void beforeBlockTextDeleted(int i, int start, int end)

	public void afterBlockTextInserted(int i, int start, int end)

	public void afterBlocksChanged(int start, int before, int after)
}
