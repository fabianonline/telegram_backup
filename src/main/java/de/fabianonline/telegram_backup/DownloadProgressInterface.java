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
	public void onMediaDownloadedGeo(boolean n);
	public void onMediaDownloadedEmpty(boolean n);
	public void onMediaDownloadFinished();
}
