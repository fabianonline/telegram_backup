package de.fabianonline.telegram_backup;

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.Database;

import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.core.TLIntVector;
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages;
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs;
import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.api.upload.TLFile;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;

class DownloadManager {
	UserManager user;
	TelegramClient client;
	Database db;
	
	public DownloadManager(UserManager u, TelegramClient c) {
		this.user = u;
		this.client = c;
		this.db = new Database(u);
	}
	
	public void downloadMessages() throws RpcErrorException, IOException {
		System.out.print("Downloading dialogs... ");
		TLAbsDialogs dialogs = client.messagesGetDialogs(
			0,
			0,
			new TLInputPeerEmpty(),
			100);
		System.out.println("Got " + dialogs.getDialogs().size() + " dialogs.");
		int max_message_id = -1;
		for(TLAbsDialog dialog : dialogs.getDialogs()) {
			max_message_id = Math.max(max_message_id, dialog.getTopMessage());
		}
		System.out.println("Top message ID is " + max_message_id);
		int max_database_id = db.getTopMessageID();
		System.out.println("Top message ID in database is " + max_database_id);
		
		int start_id = max_database_id + 1;
		int current_start_id = start_id;
		int end_id = max_message_id;
		if (start_id > end_id) {
			System.out.println("No new messages to download.");
			return;
		}
		
		while (current_start_id <= end_id) {
			int my_end_id = Math.min(current_start_id+99, end_id);
			ArrayList<Integer> a = makeIdList(current_start_id, my_end_id);
			TLIntVector ids = new TLIntVector();
			ids.addAll(a);
			my_end_id = ids.get(ids.size()-1);
			System.out.println("Fetching messages from " + ids.get(0) + " to " + my_end_id + "...");
			current_start_id = my_end_id + 1;
			
			TLAbsMessages response = client.messagesGetMessages(ids);
			db.save(response.getMessages());
			try {
				Thread.sleep(750);
			} catch (InterruptedException e) {}
		}
	}
	
	public void downloadMedia() throws RpcErrorException, IOException {
		LinkedList<TLMessage> messages = this.db.getMessagesWithMedia();
		for (TLMessage msg : messages) {
			TLAbsMessageMedia media = msg.getMedia();
			
			if (media instanceof TLMessageMediaPhoto) {
				TLMessageMediaPhoto p = (TLMessageMediaPhoto) media;
				if (p.getPhoto() instanceof TLPhoto) {
					TLPhoto photo = (TLPhoto) p.getPhoto();
					TLPhotoSize size = null;
					for (TLAbsPhotoSize s : photo.getSizes()) {
						if (s instanceof TLPhotoSize) {
							TLPhotoSize s2 = (TLPhotoSize) s;
							if (size == null || (s2.getW()>size.getW() && s2.getH()>size.getH())) {
								size = s2;
							}
						}
					}
					if (size==null) {
						throw new RuntimeException("Could not find a size for a photo.");
					}
					if (size.getLocation() instanceof TLFileLocation) {
						this.downloadPhoto(msg.getId(), (TLFileLocation)size.getLocation());
						return;
					}
				} else {
					throw new RuntimeException("Got an unexpected " + p.getPhoto().getClass().getName());
				}
			}
		}
	}
	
	private ArrayList<Integer> makeIdList(int start, int end) {
		if (start > end) throw new RuntimeException("start and end reversed");
		ArrayList<Integer> a = new ArrayList<Integer>(end - start + 1);
		for (int i=0; i<=end-start; i++) a.add(start+i);
		return a; 
	}
	
	private void downloadPhoto(int msgId, TLFileLocation src) throws RpcErrorException, IOException {
		TLInputFileLocation loc = new TLInputFileLocation();
		loc.setVolumeId(src.getVolumeId());
		loc.setLocalId(src.getLocalId());
		loc.setSecret(src.getSecret());
		
		this.downloadFile(this.makeFilename(msgId, "jpg"), loc);
	}
	
	private String makeFilename(int id, String ext) {
		String path = Config.FILE_BASE + 
			File.separatorChar + 
			Config.FILE_FILES_BASE +
			File.separatorChar;
		new File(path).mkdirs();
		if (ext!=null) return path + id + "." + ext;
		return path + id + ".dat";
	}
		
	
	private void downloadFile(String target, TLInputFileLocation loc) throws RpcErrorException, IOException {
		FileOutputStream fos = new FileOutputStream(target);
		int offset = 0;
		TLFile response;
		do {
			response = this.client.uploadGetFile(loc, offset, Config.FILE_DOWNLOAD_BLOCK_SIZE);
			offset += Config.FILE_DOWNLOAD_BLOCK_SIZE;
			fos.write(response.getBytes().getData());
		} while(response.getBytes().getLength() == Config.FILE_DOWNLOAD_BLOCK_SIZE);
		fos.close();
	}
}
