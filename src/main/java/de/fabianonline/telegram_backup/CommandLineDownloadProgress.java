package de.fabianonline.telegram_backup;

import de.fabianonline.telegram_backup.DownloadProgressInterface;

class CommandLineDownloadProgress implements DownloadProgressInterface {
	private int mediaCount = 0;
	private int i = 0;
	
	public void onMessageDownloadStart(int count) { System.out.println("Downloading " + count + " messages."); }
	public void onMessageDownloaded(int number) { System.out.print("" + number + "..."); }
	public void onMessageDownloadFinished() { System.out.println(" done."); }
	
	public void onMediaDownloadStart(int count) { i = 0; mediaCount = count; System.out.println("Checking " + count + " media."); }
	
	public void onMediaDownloadedVideo(boolean n) { show(n, 'V'); }
	public void onMediaDownloadedPhoto(boolean n) { show(n, 'P'); }
	public void onMediaDownloadedDocument(boolean n) { show(n, 'D'); }
	public void onMediaDownloadedSticker(boolean n) { show(n, 'S'); }
	public void onMediaDownloadedOther(boolean n) { show(n, ' '); }
	public void onMediaDownloadFinished() { showNewLine(); System.out.println("Done."); }
	
	private void show(boolean n, char letter) { System.out.print(n ? letter : '.'); i++; if (i % 50 == 0) showNewLine();}
	private void showNewLine() { System.out.println(" - " + i + "/" + mediaCount); }
}
	
