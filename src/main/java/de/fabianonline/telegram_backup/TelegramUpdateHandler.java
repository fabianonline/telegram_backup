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

import com.github.badoualy.telegram.api.UpdateCallback;
import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.api.Kotlogram;
import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.core.TLVector;

import de.fabianonline.telegram_backup.Database;
import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager;
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory;

class TelegramUpdateHandler implements UpdateCallback {
	private UserManager user = null;
	private Database db = null;
	public boolean debug = false;
	
	public void activate() { this.user = UserManager.getInstance(); this.db = Database.getInstance();}
	
	public void onUpdates(TelegramClient c, TLUpdates u) {
		if (db==null) return;
		if (debug) System.out.println("onUpdates - " + u.getUpdates().size() + " Updates, " + u.getUsers().size() + " Users, " + u.getChats().size() + " Chats");
		for(TLAbsUpdate update : u.getUpdates()) {
			processUpdate(update, c);
			if (debug) System.out.println("  " + update.getClass().getName());
		}
		db.saveUsers(u.getUsers());
		db.saveChats(u.getChats());
	}
	
	public void onUpdatesCombined(TelegramClient c, TLUpdatesCombined u) {
		if (db==null) return;
		if (debug) System.out.println("onUpdatesCombined");
		for(TLAbsUpdate update : u.getUpdates()) {
			processUpdate(update, c);
		}
		db.saveUsers(u.getUsers());
		db.saveChats(u.getChats());
	}
		
	public void onUpdateShort(TelegramClient c, TLUpdateShort u) {
		if (db==null) return;
		if (debug) System.out.println("onUpdateShort");
		processUpdate(u.getUpdate(), c);
		if (debug) System.out.println("  " + u.getUpdate().getClass().getName());
	}
	
	public void onShortChatMessage(TelegramClient c, TLUpdateShortChatMessage m) {
		if (db==null) return;
		if (debug) System.out.println("onShortChatMessage - " + m.getMessage());
		TLMessage msg = new TLMessage(
			m.getOut(),
			m.getMentioned(),
			m.getMediaUnread(),
			m.getSilent(),
			false,
			m.getId(),
			m.getFromId(),
			new TLPeerChat(m.getChatId()),
			m.getFwdFrom(),
			m.getViaBotId(),
			m.getReplyToMsgId(),
			m.getDate(),
			m.getMessage(),
			null,
			null,
			m.getEntities(),
			null,
			null);
		TLVector<TLAbsMessage> vector = new TLVector<TLAbsMessage>(TLAbsMessage.class);
		vector.add(msg);
		db.saveMessages(vector, Kotlogram.API_LAYER);
		System.out.print('.');
	}
	
	public void onShortMessage(TelegramClient c, TLUpdateShortMessage m) {
		if (db==null) return;
		if (debug) System.out.println("onShortMessage - " + m.getOut() + " - " + m.getUserId() + " - " + m.getMessage());
		int from_id, to_id;
		if (m.getOut()==true) {
			from_id = user.getUser().getId();
			to_id = m.getUserId();
		} else {
			to_id = user.getUser().getId();
			from_id = m.getUserId();
		}
		TLMessage msg = new TLMessage(
			m.getOut(),
			m.getMentioned(),
			m.getMediaUnread(),
			m.getSilent(),
			false,
			m.getId(),
			from_id,
			new TLPeerUser(to_id),
			m.getFwdFrom(),
			m.getViaBotId(),
			m.getReplyToMsgId(),
			m.getDate(),
			m.getMessage(),
			null,
			null,
			m.getEntities(),
			null,
			null);
		TLVector<TLAbsMessage> vector = new TLVector<TLAbsMessage>(TLAbsMessage.class);
		vector.add(msg);
		db.saveMessages(vector, Kotlogram.API_LAYER);
		System.out.print('.');
	}
	
	public void onShortSentMessage(TelegramClient c, TLUpdateShortSentMessage m) { if (db==null) return; System.out.println("onShortSentMessage"); }
	public void onUpdateTooLong(TelegramClient c) { if (db==null) return; System.out.println("onUpdateTooLong"); }
	
	private void processUpdate(TLAbsUpdate update, TelegramClient client) {
		if (update instanceof TLUpdateNewMessage) {
			TLAbsMessage abs_msg = ((TLUpdateNewMessage)update).getMessage();
			TLVector<TLAbsMessage> vector = new TLVector<TLAbsMessage>(TLAbsMessage.class);
			vector.add(abs_msg);
			db.saveMessages(vector, Kotlogram.API_LAYER);
			System.out.print('.');
			if (abs_msg instanceof TLMessage) {
				AbstractMediaFileManager fm = FileManagerFactory.getFileManager((TLMessage)abs_msg, user, client);
				if (fm != null && !fm.isEmpty() && !fm.isDownloaded()) {
					try {
						fm.download();
					} catch (Exception e) {
						System.out.println("We got an exception while downloading media, but we're going to ignore it.");
						System.out.println("Here it is anyway:");
						e.printStackTrace();
					}
				}
			}
		} else {
			// ignore everything else...
		}
	}
}
