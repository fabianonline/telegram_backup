package de.fabianonline.telegram_backup;

interface DownloadProgressInterface {
	public void onMessageDownloadStart(int count);
	public void onMessageDownloaded(int number);
	public void onMessageDownloadFinished();
	
	public void onMediaDownloadStart(int count);
	public void onMediaDownloadedVideo(boolean n);
	public void onMediaDownloadedPhoto(boolean n);
	public void onMediaDownloadedDocument(boolean n);
	public void onMediaDownloadedSticker(boolean n);
	public void onMediaDownloadedOther(boolean n);
	public void onMediaDownloadedAudio(boolean n);
	public void onMediaDownloadedEmpty(boolean n);
	public void onMediaDownloadFinished();
}
