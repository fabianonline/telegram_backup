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

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.Database;
import de.fabianonline.telegram_backup.StickerConverter;
import de.fabianonline.telegram_backup.DownloadProgressInterface;
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory;
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager;

import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.api.Kotlogram;
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
import java.util.List;
import java.util.Random;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;

public class DownloadManager {
	UserManager user;
	TelegramClient client;
	Database db;
	DownloadProgressInterface prog = null;
	static TelegramClient download_client;
	
	public DownloadManager(UserManager u, TelegramClient c, DownloadProgressInterface p) {
		this.user = u;
		this.client = c;
		this.prog = p;
		this.db = new Database(u, c);
	}
	
	public void downloadMessages(Integer limit) throws RpcErrorException, IOException {
		boolean completed = true;
		do {
			completed = true;
			try {
				_downloadMessages(limit);
			} catch (RpcErrorException e) {
				if (e.getTag().startsWith("420: FLOOD_WAIT_")) {
					completed = false;
					Utils.obeyFloodWaitException(e);
				} else {
					throw e;
				}
			} catch (TimeoutException e) {
				completed = false;
				System.out.println("");
				System.out.println("Telegram took too long to respond to our request.");
				System.out.println("I'm going to wait a minute and then try again.");
				try { Thread.sleep(60*1000); } catch(InterruptedException e2) {}
				System.out.println("");
			}
		} while (!completed);
	}
	
	public void _downloadMessages(Integer limit) throws RpcErrorException, IOException, TimeoutException {
		Log.debug("This is _downloadMessages with limit %d", limit);
		Log.up();
		int dialog_limit = 100;
		Log.debug("Downloading the last %d dialogs", dialog_limit);
		Log.up();
		System.out.println("Downloading most recent dialogs... ");
		int max_message_id = 0;
		TLAbsDialogs dialogs = client.messagesGetDialogs(
			0,
			0,
			new TLInputPeerEmpty(),
			dialog_limit);
		Log.debug("Got %d dialogs", dialogs.getDialogs().size());
		Log.up();
		for (TLDialog d : dialogs.getDialogs()) {
			if (d.getTopMessage() > max_message_id) {
				Log.debug("Updating top message id: %d => %d", max_message_id, d.getTopMessage());
				max_message_id = d.getTopMessage();
			}
		}
		Log.down();
		System.out.println("Top message ID is " + max_message_id);
		int max_database_id = db.getTopMessageID();
		System.out.println("Top message ID in database is " + max_database_id);
		if (limit != null) {
			System.out.println("Limit is set to " + limit);
			max_database_id = Math.max(max_database_id, max_message_id-limit);
			System.out.println("New top message id 'in database' is " + max_database_id);
		}
		Log.down();
		
		if (max_database_id == max_message_id) {
			System.out.println("No new messages to download.");
		} else if (max_database_id > max_message_id) {
			throw new RuntimeException("max_database_id is bigger then max_message_id. This shouldn't happen. But the telegram api nonetheless does that sometimes. Just ignore this error, wait a few seconds and then try again.");
		} else {
			int start_id = max_database_id + 1;
			int end_id = max_message_id;
			
			List<Integer> ids = makeIdList(start_id, end_id);
			downloadMessages(ids);
		}
		
		Log.debug("Searching for missing messages in the db");
		Log.up();
		int count_missing = 0;
		System.out.println("Checking message database for completeness...");
		int db_count = db.getMessageCount();
		int db_max = db.getTopMessageID();
		Log.debug("db_count: %d", db_count);
		Log.debug("db_max: %d", db_max);
		
		if (db_count != db_max) {
			if (limit != null) {
				System.out.println("You are missing messages in your database. But since you're using '--limit-messages', I won't download these now.");
			} else {
				LinkedList<Integer> ids = db.getMissingIDs();
				count_missing = ids.size();
				System.out.println("Downloading " + ids.size() + " messages that are missing in your database.");
				
				downloadMessages(ids);
			}
		}
		
		Log.debug("Logging this run");
		db.logRun(Math.min(max_database_id + 1, max_message_id), max_message_id, count_missing);
		Log.down();
	}
	
	private void downloadMessages(List<Integer> ids) throws RpcErrorException, IOException {
		prog.onMessageDownloadStart(ids.size());
		
		Log.debug("Entering download loop");
		Log.up();
		while (ids.size()>0) {
			Log.debug("Loop");
			Log.up();
			TLIntVector vector = new TLIntVector();
			for (int i=0; i<100; i++) {
				if (ids.size()==0) break;
				vector.add(ids.remove(0));
			}
			Log.debug("vector.size(): %d", vector.size());
			Log.debug("ids.size(): %d", ids.size());
			
			TLAbsMessages response = client.messagesGetMessages(vector);
			prog.onMessageDownloaded(response.getMessages().size());
			db.saveMessages(response.getMessages(), Kotlogram.API_LAYER);
			db.saveChats(response.getChats());
			db.saveUsers(response.getUsers());
			Log.debug("Sleeping");
			try {
				Thread.sleep(Config.DELAY_AFTER_GET_MESSAGES);
			} catch (InterruptedException e) {}
			Log.down();
		}
		Log.down();
		Log.debug("Finished.");
		
		prog.onMessageDownloadFinished();
	}
	
	public void downloadMedia() throws RpcErrorException, IOException {
		download_client = client.getDownloaderClient();
		boolean completed = true;
		do {
			completed = true;
			try {
				_downloadMedia();
			} catch (RpcErrorException e) {
				if (e.getTag().startsWith("420: FLOOD_WAIT_")) {
					completed = false;
					Utils.obeyFloodWaitException(e);
				} else {
					throw e;
				}
			} catch (TimeoutException e) {
				Log.down();
				completed = false;
				System.out.println("");
				System.out.println("Telegram took too long to respond to our request.");
				System.out.println("I'm going to wait a minute and then try again.");
				try { Thread.sleep(60*1000); } catch(InterruptedException e2) {}
				System.out.println("");
			}
		} while (!completed);
	}
	
	private void _downloadMedia() throws RpcErrorException, IOException, TimeoutException {
		Log.debug("This is _downloadMedia");
		Log.debug("Checking if there are messages in the DB with a too old API layer");
		LinkedList<Integer> ids = db.getIdsFromQuery("SELECT id FROM messages WHERE has_media=1 AND api_layer<" + Kotlogram.API_LAYER);
		if (ids.size()>0) {
			System.out.println("You have " + ids.size() + " messages in your db that need an update. Doing that now.");
			Log.debug("Found %d messages", ids.size());
			downloadMessages(ids);
		}
		
		LinkedList<TLMessage> messages = this.db.getMessagesWithMedia();
		Log.debug("Database returned %d messages with media", messages.size());
		prog.onMediaDownloadStart(messages.size());
		Log.up();
		for (TLMessage msg : messages) {
			AbstractMediaFileManager m = FileManagerFactory.getFileManager(msg, user, client);
			Log.debug("Message ID: %d  Media type: %-10.10s  FileManager type: %-10.10s  isEmpty: %-5s  isDownloaded: %-5s",
				msg.getId(),
				msg.getMedia().getClass().getSimpleName().replace("TLMessageMedia", "…"),
				m.getClass().getSimpleName().replace("FileManager", "…"),
				m.isEmpty(),
				m.isDownloaded());
			if (m.isEmpty()) {
				prog.onMediaDownloadedEmpty();
			} else if (m.isDownloaded()) {
				prog.onMediaAlreadyPresent(m);
			} else {
				Log.up();
				m.download();
				Log.down();
				prog.onMediaDownloaded(m);
			}
		}
		Log.down();
		prog.onMediaDownloadFinished();
	}
	
	private List<Integer> makeIdList(int start, int end) {
		LinkedList<Integer> a = new LinkedList<Integer>();
		for (int i=start; i<=end; i++) a.add(i);
		return a; 
	}
	
	public static void downloadFile(TelegramClient client, String targetFilename, int size, int dcId, long volumeId, int localId, long secret) throws RpcErrorException, IOException {
		TLInputFileLocation loc = new TLInputFileLocation(volumeId, localId, secret);
		downloadFileFromDc(client, targetFilename, loc, dcId, size);
	}
	
	public static void downloadFile(TelegramClient client, String targetFilename, int size, int dcId, long id, long accessHash) throws RpcErrorException, IOException {
		TLInputDocumentFileLocation loc = new TLInputDocumentFileLocation(id, accessHash);
		downloadFileFromDc(client, targetFilename, loc, dcId, size);
	}
	
	private static boolean downloadFileFromDc(TelegramClient client, String target, TLAbsInputFileLocation loc, Integer dcID, int size) throws RpcErrorException, IOException {
		FileOutputStream fos = null;
		try {
			String temp_filename = target + ".downloading";
			Log.debug("Temporary filename %s", temp_filename);
			
			int offset = 0;
			if (new File(temp_filename).isFile()) {
				Log.debug("Temporary filename already exists; continuing this file");
				offset = (int)new File(temp_filename).length();
				if (offset >= size) {
					Log.debug("Temporary file size is >= the target size. Assuming corrupt file & deleting it");
					new File(temp_filename).delete();
					offset = 0;
				}
			}
			Log.debug("offset before the loop is %d", offset);
			fos = new FileOutputStream(temp_filename, true);
			TLFile response;
			do {
				int block_size = size;
				Log.debug("offset:   %8d block_size: %7d size: %8d", offset, block_size, size);
				TLRequestUploadGetFile req = new TLRequestUploadGetFile(loc, offset, block_size);
				if (dcID==null) {
					response = (TLFile) download_client.executeRpcQuery(req);
				} else {
					response = (TLFile) download_client.executeRpcQuery(req, dcID);
				}
				
				offset += response.getBytes().getData().length;
				Log.debug("response: %8d               total size: %8d", response.getBytes().getData().length, offset);
				
				fos.write(response.getBytes().getData());
				fos.flush();
				try { Thread.sleep(Config.DELAY_AFTER_GET_FILE); } catch(InterruptedException e) {}
			} while(offset < size && response.getBytes().getData().length>0);
			fos.close();
			if (offset < size) {
				System.out.println("Requested file " + target + " with " + size + " bytes, but got only " + offset + " bytes.");
				new File(temp_filename).delete();
				System.exit(1);
			}
			Log.debug("Renaming %s to %s", temp_filename, target);
			Files.move(new File(temp_filename).toPath(), new File(target).toPath(), StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (java.io.IOException ex) {
			if (fos!=null) fos.close();
			System.out.println("IOException happened while downloading " + target);
			throw ex;
		} catch (RpcErrorException ex) {
			if (fos!=null) fos.close();
			System.out.println("RpcErrorException happened while downloading " + target);
			throw ex;
		}
	}
	
	public static boolean downloadExternalFile(String target, String url) throws IOException {
		FileUtils.copyURLToFile(new URL(url), new File(target), 5000, 5000);
		return true;
	}
}
