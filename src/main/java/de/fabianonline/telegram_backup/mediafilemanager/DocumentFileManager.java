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

public class DocumentFileManager extends AbstractMediaFileManager {
	protected TLDocument doc;
	private String extension = null;
	
	public DocumentFileManager(TLMessage msg, UserManager user, TelegramClient client) {
		super(msg, user, client);
		TLAbsDocument d = ((TLMessageMediaDocument)msg.getMedia()).getDocument();
		if (d instanceof TLDocument) {
			this.doc = (TLDocument)d;
		} else if (d instanceof TLDocumentEmpty) {
			this.isEmpty = true;
		} else {
			throwUnexpectedObjectError(d);
		}
	}
	
	public boolean isSticker() {
		TLDocumentAttributeSticker sticker = null;
		for(TLAbsDocumentAttribute attr : doc.getAttributes()) {
			if (attr instanceof TLDocumentAttributeSticker) {
				sticker = (TLDocumentAttributeSticker)attr;
			}
		}
		return sticker!=null;
	}
	
	public int getSize() { return doc.getSize(); }
	
	public String getExtension() {
		if (extension != null) return extension;
		String ext = null;
		String original_filename = null;
		for(TLAbsDocumentAttribute attr : doc.getAttributes()) {
			if (attr instanceof TLDocumentAttributeFilename) {
				original_filename = ((TLDocumentAttributeFilename)attr).getFileName();
			}
		}
		if (original_filename != null) {
			int i = original_filename.lastIndexOf('.');
			if (i>0) ext = original_filename.substring(i+1);
			
		}
		if (ext==null) {
			ext = extensionFromMimetype(doc.getMimeType());
		}
		this.extension = ext;
		return ext;
	}
	
	public void download() throws RpcErrorException, IOException {
		DownloadManager.downloadFile(client, getTargetPathAndFilename(), getSize(), doc.getDcId(), doc.getId(), doc.getAccessHash());
	}
	
	public String getLetter() { return "d"; }
	public String getName() { return "document"; }
	public String getDescription() { return "Document"; }
}
