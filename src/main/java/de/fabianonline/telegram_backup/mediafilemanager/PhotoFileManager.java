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

package de.fabianonline.telegram_backup.mediafilemanager;

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.Database;
import de.fabianonline.telegram_backup.StickerConverter;
import de.fabianonline.telegram_backup.DownloadProgressInterface;
import de.fabianonline.telegram_backup.DownloadManager;

import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.core.TLIntVector;
import com.github.badoualy.telegram.tl.core.TLObject;
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages;
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs;
import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.api.upload.TLFile;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
import com.github.badoualy.telegram.tl.api.request.TLRequestUploadGetFile;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.net.URL;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;

public class PhotoFileManager extends AbstractMediaFileManager {
	private TLPhoto photo;
	private TLPhotoSize size = null;
	public PhotoFileManager(TLMessage msg, UserManager user, TelegramClient client) {
		super(msg, user, client);
		TLAbsPhoto p = ((TLMessageMediaPhoto)msg.getMedia()).getPhoto();
		if (p instanceof TLPhoto) {
			this.photo = (TLPhoto)p;
			
			TLPhotoSize biggest = null;
			for (TLAbsPhotoSize s : photo.getSizes()) if (s instanceof TLPhotoSize) {
				TLPhotoSize size = (TLPhotoSize) s;
				if (biggest == null || (size.getW()>biggest.getW() && size.getH()>biggest.getH())) {
					biggest = size;
				}
			}
			if (biggest==null) {
				throw new RuntimeException("Could not find a size for a photo.");
			}
			this.size = biggest;
		} else if (p instanceof TLPhotoEmpty) {
			this.isEmpty = true;
		} else {
			throwUnexpectedObjectError(p);
		}
	}
	
	public int getSize() {
		if (size!=null) return size.getSize();
		return 0;
	}
	
	public String getExtension() { return "jpg"; }
	
	public void download() throws RpcErrorException, IOException {
		if (isEmpty) return;
		TLFileLocation loc = (TLFileLocation) size.getLocation();
		DownloadManager.downloadFile(client, getTargetPathAndFilename(), getSize(), loc.getDcId(), loc.getVolumeId(), loc.getLocalId(), loc.getSecret());
	}
	
	public String getLetter() { return "p"; }
	public String getName() { return "photo"; }
	public String getDescription() { return "Photo"; }
}
