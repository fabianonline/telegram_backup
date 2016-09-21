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
import de.fabianonline.telegram_backup.Config;
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

public abstract class AbstractMediaFileManager {
	protected UserManager user;
	protected TLMessage message;
	protected TelegramClient client;
	protected boolean isEmpty = false;
	
	public AbstractMediaFileManager(TLMessage msg, UserManager user, TelegramClient client) {this.user = user; this.message = msg; this.client = client;};
	public abstract int getSize();
	public abstract String getExtension();
	public boolean isEmpty() { return isEmpty; }
	public boolean isDownloaded() { return new File(getTargetPathAndFilename()).isFile(); }
	public boolean isDownloading() { return new File(getTargetPathAndFilename() + ".downloading").isFile(); }
	public abstract void download() throws RpcErrorException, IOException;
	public static void throwUnexpectedObjectError(Object o) {
		throw new RuntimeException("Unexpected " + o.getClass().getName());
	}
	public String getTargetPath() {
		String path = user.getFileBase() + Config.FILE_FILES_BASE + File.separatorChar;
		new File(path).mkdirs();
		return path;
	}
	public String getTargetFilename() { return "" + message.getId() + "." + getExtension(); }
	public String getTargetPathAndFilename() { return getTargetPath() + getTargetFilename(); }
	
	protected String extensionFromMimetype(String mime) {
		switch(mime) {
			case "text/plain": return "txt";
		}
		
		int i = mime.lastIndexOf('/');
		String ext = mime.substring(i+1).toLowerCase();
		
		if (ext=="unknown") return "dat";
		
		return ext;
	}
	
	public abstract String getLetter();
	public abstract String getName();
	public abstract String getDescription();
}
