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
import java.util.Random;
import java.net.URL;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;

public class DownloadManager {
	UserManager user;
	TelegramClient client;
	Database db;
	DownloadProgressInterface prog = null;
	
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
		System.out.println("Downloading most recent dialog... ");
		int max_message_id = 0;
		TLAbsDialogs dialogs = client.messagesGetDialogs(
			0,
			0,
			new TLInputPeerEmpty(),
			100);
		for (TLAbsDialog d : dialogs.getDialogs()) {
			if (d.getTopMessage() > max_message_id) {
				max_message_id = d.getTopMessage();
			}
		}
		System.out.println("Top message ID is " + max_message_id);
		int max_database_id = db.getTopMessageID();
		System.out.println("Top message ID in database is " + max_database_id);
		if (limit != null) {
			System.out.println("Limit is set to " + limit);
			max_database_id = Math.max(max_database_id, max_message_id-limit);
			System.out.println("New top message id 'in database' is " + max_database_id);
		}
		
		if (max_database_id == max_message_id) {
			System.out.println("No new messages to download.");
		} else if (max_database_id > max_message_id) {
			throw new RuntimeException("max_database_id is bigger then max_message_id. This shouldn't happen. But the telegram api nonetheless does that sometimes. Just ignore this error, wait a few seconds and then try again.");
		} else {
			int start_id = max_database_id + 1;
			int current_start_id = start_id;
			int end_id = max_message_id;
			
			prog.onMessageDownloadStart(end_id - current_start_id + 1);
			
			while (current_start_id <= end_id) {
				int my_end_id = Math.min(current_start_id+99, end_id);
				ArrayList<Integer> a = makeIdList(current_start_id, my_end_id);
				TLIntVector ids = new TLIntVector();
				ids.addAll(a);
				my_end_id = ids.get(ids.size()-1);
				current_start_id = my_end_id + 1;
				TLAbsMessages response = client.messagesGetMessages(ids);
				prog.onMessageDownloaded(response.getMessages().size());
				db.saveMessages(response.getMessages());
				db.saveChats(response.getChats());
				db.saveUsers(response.getUsers());
				try {
					Thread.sleep(Config.DELAY_AFTER_GET_MESSAGES);
				} catch (InterruptedException e) {}
			}
			
			prog.onMessageDownloadFinished();
		}
		
		int count_missing = 0;
		System.out.println("Checking message database for completeness...");
		if (db.getMessageCount() != db.getTopMessageID()) {
			if (limit != null) {
				System.out.println("You are missing messages in your database. But since you're using '--limit-messages', I won't download these now.");
			} else {
				LinkedList<Integer> ids = db.getMissingIDs();
				count_missing = ids.size();
				System.out.println("Downloading " + ids.size() + " messages that are missing in your database.");
				prog.onMessageDownloadStart(ids.size());
				while (ids.size()>0) {
					TLIntVector vector = new TLIntVector();
					for (int i=0; i<100; i++) {
						if (ids.size()==0) break;
						vector.add(ids.remove());
					}
					TLAbsMessages response = client.messagesGetMessages(vector);
					prog.onMessageDownloaded(response.getMessages().size());
					db.saveMessages(response.getMessages());
					db.saveChats(response.getChats());
					db.saveUsers(response.getUsers());
					try { Thread.sleep(Config.DELAY_AFTER_GET_MESSAGES); } catch (InterruptedException e) {}
				}
				prog.onMessageDownloadFinished();
			}
		}
		db.logRun(Math.min(max_database_id + 1, max_message_id), max_message_id, count_missing);
	}
	
	public void downloadMedia() throws RpcErrorException, IOException {
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
		LinkedList<TLMessage> messages = this.db.getMessagesWithMedia();
		prog.onMediaDownloadStart(messages.size());
		for (TLMessage msg : messages) {
			AbstractMediaFileManager m = FileManagerFactory.getFileManager(msg, user, client);
			if (m.isEmpty()) {
				prog.onMediaDownloadedEmpty();
			} else if (m.isDownloaded()) {
				prog.onMediaAlreadyPresent(m);
			} else {
				m.download();
				prog.onMediaDownloaded(m);
			}
		}
		prog.onMediaDownloadFinished();
	}
	
	private ArrayList<Integer> makeIdList(int start, int end) {
		if (start > end) throw new RuntimeException("start and end reversed");
		ArrayList<Integer> a = new ArrayList<Integer>(end - start + 1);
		for (int i=0; i<=end-start; i++) a.add(start+i);
		return a; 
	}
	
	public static void downloadFile(TelegramClient client, String targetFilename, int size, long volumeId, int localId, long secret) throws RpcErrorException, IOException {
		TLInputFileLocation loc = new TLInputFileLocation(volumeId, localId, secret);
		downloadFileFromDc(client, targetFilename, loc, null, size);
	}
	
	public static void downloadFile(TelegramClient client, String targetFilename, int size, int dcId, long id, long accessHash) throws RpcErrorException, IOException {
		TLInputDocumentFileLocation loc = new TLInputDocumentFileLocation(id, accessHash);
		downloadFileFromDc(client, targetFilename, loc, dcId, size);
	}
	
	private static boolean downloadFileFromDc(TelegramClient client, String target, TLAbsInputFileLocation loc, Integer dcID, int size) throws RpcErrorException, IOException {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(target);
			int offset = 0;
			TLFile response;
			do {
				int block_size = Config.FILE_DOWNLOAD_BLOCK_SIZES[new Random().nextInt(Config.FILE_DOWNLOAD_BLOCK_SIZES.length)];
				TLRequestUploadGetFile req = new TLRequestUploadGetFile(loc, offset, block_size);
				if (dcID==null) {
					response = (TLFile) client.executeRpcQuery(req);
				} else {
					response = (TLFile) client.executeRpcQuery(req, dcID);
				}
				offset += response.getBytes().getData().length;
				fos.write(response.getBytes().getData());
				try { Thread.sleep(Config.DELAY_AFTER_GET_FILE); } catch(InterruptedException e) {}
			} while(offset < size && response.getBytes().getData().length>0);
			fos.close();
			if (offset < size) {
				System.out.println("Requested file " + target + " with " + size + " bytes, but got only " + offset + " bytes.");
				new File(target).delete();
				System.exit(1);
			}
			return true;
		} catch (java.io.IOException ex) {
			if (fos!=null) fos.close();
			new File(target).delete();
			System.out.println("IOException happened while downloading " + target);
			throw ex;
		} catch (RpcErrorException ex) {
			if (fos!=null) fos.close();
			new File(target).delete();
			System.out.println("RpcErrorException happened while downloading " + target);
			throw ex;
		}
	}
	
	public static boolean downloadExternalFile(String target, String url) throws IOException {
		FileUtils.copyURLToFile(new URL(url), new File(target), 5000, 5000);
		return true;
	}
}
