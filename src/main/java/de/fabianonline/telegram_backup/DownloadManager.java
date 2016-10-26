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
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;

public class DownloadManager {
	UserManager user;
	TelegramClient client;
	Database db;
	DownloadProgressInterface prog = null;
	static TelegramClient download_client;
	static boolean last_download_succeeded = true;
	static final Logger logger = LoggerFactory.getLogger(DownloadManager.class);
	
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
				if (e.getCode()==420) { // FLOOD_WAIT
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
				try { TimeUnit.MINUTES.sleep(1); } catch(InterruptedException e2) {}
				System.out.println("");
			}
		} while (!completed);
	}
	
	public void _downloadMessages(Integer limit) throws RpcErrorException, IOException, TimeoutException {
		logger.info("This is _downloadMessages with limit {}", limit);
		int dialog_limit = 100;
		logger.info("Downloading the last {} dialogs", dialog_limit);
		System.out.println("Downloading most recent dialogs... ");
		int max_message_id = 0;
		TLAbsDialogs dialogs = client.messagesGetDialogs(
			0,
			0,
			new TLInputPeerEmpty(),
			dialog_limit);
		logger.debug("Got {} dialogs", dialogs.getDialogs().size());
		for (TLDialog d : dialogs.getDialogs()) {
			if (d.getTopMessage() > max_message_id && ! (d.getPeer() instanceof TLPeerChannel)) {
				logger.trace("Updating top message id: {} => {}. Dialog type: {}", max_message_id, d.getTopMessage(), d.getPeer().getClass().getName());
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
		if (max_message_id - max_database_id > 1000000) {
			System.out.println("Would have to load more than 1 million messages which is not supported by telegram. Capping the list.");
			logger.debug("max_message_id={}, max_database_id={}, difference={}", max_message_id, max_database_id, max_message_id - max_database_id);
			max_database_id = Math.max(0, max_message_id - 1000000);
			logger.debug("new max_database_id: {}", max_database_id);
		}

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
		
		logger.info("Searching for missing messages in the db");
		int count_missing = 0;
		System.out.println("Checking message database for completeness...");
		int db_count = db.getMessageCount();
		int db_max = db.getTopMessageID();
		logger.debug("db_count: {}", db_count);
		logger.debug("db_max: {}", db_max);
		
		if (db_count != db_max) {
			if (limit != null) {
				System.out.println("You are missing messages in your database. But since you're using '--limit-messages', I won't download these now.");
			} else {
				LinkedList<Integer> all_missing_ids = db.getMissingIDs();
				LinkedList<Integer> downloadable_missing_ids = new LinkedList<Integer>();
				for (Integer id : all_missing_ids) {
					if (id > max_message_id - 1000000) downloadable_missing_ids.add(id);
				}
				count_missing = all_missing_ids.size();
				System.out.println("" + all_missing_ids.size() + " messages are missing in your Database.");
				System.out.println("I can (and will) download " + downloadable_missing_ids.size() + " of them.");
				
				downloadMessages(downloadable_missing_ids);
			}
		}
		
		logger.info("Logging this run");
		db.logRun(Math.min(max_database_id + 1, max_message_id), max_message_id, count_missing);
	}
	
	private void downloadMessages(List<Integer> ids) throws RpcErrorException, IOException {
		prog.onMessageDownloadStart(ids.size());
		boolean has_seen_flood_wait_message = false;
		
		logger.debug("Entering download loop");
		while (ids.size()>0) {
			logger.trace("Loop");
			TLIntVector vector = new TLIntVector();
			for (int i=0; i<Config.GET_MESSAGES_BATCH_SIZE; i++) {
				if (ids.size()==0) break;
				vector.add(ids.remove(0));
			}
			logger.trace("vector.size(): {}", vector.size());
			logger.trace("ids.size(): {}", ids.size());
			
			TLAbsMessages response;
			int tries = 0;
			while(true) {
				logger.trace("Trying getMessages(), tries={}", tries);
				if (tries>=5) {
					CommandLineController.show_error("Couldn't getMessages after 5 tries. Quitting.");
				}
				tries++;
				try {
					response = client.messagesGetMessages(vector);
					break;
				} catch (RpcErrorException e) {
					if (e.getCode()==420) { // FLOOD_WAIT
						Utils.obeyFloodWaitException(e, has_seen_flood_wait_message);
						has_seen_flood_wait_message = true;
					} else {
						throw e;
					}
				}
			}
			logger.trace("response.getMessages().size(): {}", response.getMessages().size());
			if (response.getMessages().size() != vector.size()) {
				CommandLineController.show_error("Requested " + vector.size() + " messages, but got " + response.getMessages().size() + ". That is unexpected. Quitting.");
			}
			prog.onMessageDownloaded(response.getMessages().size());
			db.saveMessages(response.getMessages(), Kotlogram.API_LAYER);
			db.saveChats(response.getChats());
			db.saveUsers(response.getUsers());
			logger.trace("Sleeping");
			try {
				TimeUnit.MILLISECONDS.sleep(Config.DELAY_AFTER_GET_MESSAGES);
			} catch (InterruptedException e) {}
		}
		logger.debug("Finished.");
		
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
				completed = false;
				System.out.println("");
				System.out.println("Telegram took too long to respond to our request.");
				System.out.println("I'm going to wait a minute and then try again.");
				try { TimeUnit.MINUTES.sleep(1); } catch(InterruptedException e2) {}
				System.out.println("");
			}
		} while (!completed);
	}
	
	private void _downloadMedia() throws RpcErrorException, IOException, TimeoutException {
		logger.info("This is _downloadMedia");
		logger.info("Checking if there are messages in the DB with a too old API layer");
		LinkedList<Integer> ids = db.getIdsFromQuery("SELECT id FROM messages WHERE has_media=1 AND api_layer<" + Kotlogram.API_LAYER);
		if (ids.size()>0) {
			System.out.println("You have " + ids.size() + " messages in your db that need an update. Doing that now.");
			logger.debug("Found {} messages", ids.size());
			downloadMessages(ids);
		}
		
		LinkedList<TLMessage> messages = this.db.getMessagesWithMedia();
		logger.debug("Database returned {} messages with media", messages.size());
		prog.onMediaDownloadStart(messages.size());
		for (TLMessage msg : messages) {
			AbstractMediaFileManager m = FileManagerFactory.getFileManager(msg, user, client);
			logger.trace("message {}, {}, {}, {}, {}",
				msg.getId(),
				msg.getMedia().getClass().getSimpleName().replace("TLMessageMedia", "…"),
				m.getClass().getSimpleName(),
				m.isEmpty() ? "empty" : "non-empty",
				m.isDownloaded() ? "downloaded" : "not downloaded");
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
			logger.debug("Downloading file {}", target);
			logger.trace("Temporary filename: {}", temp_filename);
			
			int offset = 0;
			if (new File(temp_filename).isFile()) {
				logger.info("Temporary filename already exists; continuing this file");
				offset = (int)new File(temp_filename).length();
				if (offset >= size) {
					logger.warn("Temporary file size is >= the target size. Assuming corrupt file & deleting it");
					new File(temp_filename).delete();
					offset = 0;
				}
			}
			logger.trace("offset before the loop is {}", offset);
			fos = new FileOutputStream(temp_filename, true);
			TLFile response;
			do {
				int block_size = size;
				logger.trace("offset: {} block_size: {} size: {}", offset, block_size, size);
				TLRequestUploadGetFile req = new TLRequestUploadGetFile(loc, offset, block_size);
				if (dcID==null) {
					response = (TLFile) download_client.executeRpcQuery(req);
				} else {
					response = (TLFile) download_client.executeRpcQuery(req, dcID);
				}
				
				offset += response.getBytes().getData().length;
				logger.trace("response: {} total size: {}", response.getBytes().getData().length, offset);
				
				fos.write(response.getBytes().getData());
				fos.flush();
				try { TimeUnit.MILLISECONDS.sleep(Config.DELAY_AFTER_GET_FILE); } catch(InterruptedException e) {}
			} while(offset < size && response.getBytes().getData().length>0);
			fos.close();
			if (offset < size) {
				System.out.println("Requested file " + target + " with " + size + " bytes, but got only " + offset + " bytes.");
				new File(temp_filename).delete();
				System.exit(1);
			}
			logger.trace("Renaming {} to {}", temp_filename, target);
			int rename_tries = 0;
			IOException last_exception = null;
			while (rename_tries <= Config.RENAMING_MAX_TRIES) {
				rename_tries++;
				try {
					Files.move(new File(temp_filename).toPath(), new File(target).toPath(), StandardCopyOption.REPLACE_EXISTING);
					last_exception = null;
					break;
				} catch (IOException e) {
					logger.debug("Exception during move. rename_tries: {}. Exception: {}", rename_tries, e);
					last_exception = e;
					try { TimeUnit.MILLISECONDS.sleep(Config.RENAMING_DELAY); } catch (InterruptedException e2) {}
				}
			}
			if (last_exception != null) {
				throw last_exception;
			}
			last_download_succeeded = true;
			return true;
		} catch (java.io.IOException ex) {
			if (fos!=null) fos.close();
			System.out.println("IOException happened while downloading " + target);
			throw ex;
		} catch (RpcErrorException ex) {
			if (fos!=null) fos.close();
			if (ex.getCode()==500) {
				if (!last_download_succeeded) {
					System.out.println("Got an Internal Server Error from Telegram. Since the file downloaded before also happened to get this error, we will stop downloading now. Please try again later.");
					throw ex;
				}
				last_download_succeeded = false;
				System.out.println("Got an Internal Server Error from Telegram. Skipping this file for now. Next run of telegram_backup will continue to download this file.");
				logger.warn(ex.toString());
				return false;
			}
			System.out.println("RpcErrorException happened while downloading " + target);
			throw ex;
		}
	}
	
	public static boolean downloadExternalFile(String target, String url) throws IOException {
		FileUtils.copyURLToFile(new URL(url), new File(target), 5000, 5000);
		return true;
	}
}
