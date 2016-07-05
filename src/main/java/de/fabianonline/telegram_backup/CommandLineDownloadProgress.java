/* Telegram_Backup
 * Copyright (C) 2016 Fabian Schlenz
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package de.fabianonline.telegram_backup;

import de.fabianonline.telegram_backup.DownloadProgressInterface;

class CommandLineDownloadProgress implements DownloadProgressInterface {
	private int mediaCount = 0;
	private int i = 0;
	
	public void onMessageDownloadStart(int count) { i=0; System.out.println("Downloading " + count + " messages."); }
	public void onMessageDownloaded(int number) { i+=number; System.out.print("..." + i); }
	public void onMessageDownloadFinished() { System.out.println(" done."); }
	
	public void onMediaDownloadStart(int count) {
		i = 0;
		mediaCount = count;
		System.out.println("Checking and downloading media.");
		System.out.println("Legend:");
		System.out.println("'V' - Video         'P' - Photo         'D' - Document");
		System.out.println("'S' - Sticker       'A' - Audio         'G' - Geolocation");
		System.out.println("'.' - Previously downloaded file        'e' - Empty file");
		System.out.println("' ' - Ignored media type (location or website, for example)");
		System.out.println("" + count + " Files to check / download");    
	}
	
	public void onMediaDownloadedVideo(boolean n) { show(n, 'V'); }
	public void onMediaDownloadedPhoto(boolean n) { show(n, 'P'); }
	public void onMediaDownloadedDocument(boolean n) { show(n, 'D'); }
	public void onMediaDownloadedSticker(boolean n) { show(n, 'S'); }
	public void onMediaDownloadedOther(boolean n) { show(n, ' '); }
	public void onMediaDownloadedAudio(boolean n) { show(n, 'A'); }
	public void onMediaDownloadedGeo(boolean n) { show(n, 'G'); }
	public void onMediaDownloadedEmpty(boolean n) { show(true, 'e'); }
	public void onMediaDownloadFinished() { showNewLine(); System.out.println("Done."); }
	
	private void show(boolean n, char letter) { System.out.print(n ? letter : '.'); i++; if (i % 100 == 0) showNewLine();}
	private void showNewLine() { System.out.println(" - " + i + "/" + mediaCount); }
}
	
