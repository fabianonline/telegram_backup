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

public class AudioFileManager extends AbstractMediaFileManager {
	protected TLAudio audio;
	
	public AudioFileManager(TLMessage msg, UserManager user, TelegramClient client) {
		super(msg, user, client);
		TLAbsAudio a = ((TLMessageMediaAudio)msg.getMedia()).getAudio();
		if (a instanceof TLAudio) {
			this.audio = (TLAudio) a;
		} else if (a instanceof TLAudioEmpty) {
			this.isEmpty = true;
		} else {
			throwUnexpectedObjectError(a);
		}
	}
	
	public int getSize() { return audio.getSize(); }
	
	public String getExtension() {
		return extensionFromMimetype(audio.getMimeType());
	}
	
	public void download() throws RpcErrorException, IOException {
		DownloadManager.downloadFile(client, getTargetPathAndFilename(), getSize(), audio.getDcId(), audio.getId(), audio.getAccessHash());
	}
	
	public String getLetter() { return "a"; }
	public String getName() { return "audio"; }
	public String getDescription() { return "Audio"; }
}
