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

package de.fabianonline.telegram_backup

import com.github.badoualy.telegram.api.UpdateCallback
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.core.TLVector

import de.fabianonline.telegram_backup.Database
import de.fabianonline.telegram_backup.UserManager
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory
import org.slf4j.LoggerFactory

internal class TelegramUpdateHandler(val user_manager: UserManager, val db: Database, val file_base: String, val settings: Settings) : UpdateCallback {
	val logger = LoggerFactory.getLogger(TelegramUpdateHandler::class.java)

	override fun onUpdates(client: TelegramClient, updates: TLUpdates) {

		logger.debug("onUpdates - " + updates.getUpdates().size + " Updates, " + updates.getUsers().size + " Users, " + updates.getChats().size + " Chats")
		for (update in updates.getUpdates()) {
			processUpdate(update)
			logger.debug("  " + update.javaClass.getName())
		}
		db.saveUsers(updates.getUsers())
		db.saveChats(updates.getChats())
	}

	override fun onUpdatesCombined(client: TelegramClient, updates: TLUpdatesCombined) {
		logger.debug("onUpdatesCombined")
		for (update in updates.getUpdates()) {
			processUpdate(update)
		}
		db.saveUsers(updates.getUsers())
		db.saveChats(updates.getChats())
	}

	override fun onUpdateShort(client: TelegramClient, update: TLUpdateShort) {
		logger.debug("onUpdateShort")
		processUpdate(update.getUpdate())
		logger.debug("  " + update.getUpdate().javaClass.getName())
	}

	override fun onShortChatMessage(client: TelegramClient, message: TLUpdateShortChatMessage) {
		logger.debug("onShortChatMessage - " + message.getMessage())
		val msg = TLMessage(
			message.getOut(),
			message.getMentioned(),
			message.getMediaUnread(),
			message.getSilent(),
			false,
			message.getId(),
			message.getFromId(),
			TLPeerChat(message.getChatId()),
			message.getFwdFrom(),
			message.getViaBotId(),
			message.getReplyToMsgId(),
			message.getDate(),
			message.getMessage(), null, null,
			message.getEntities(), null, null)
		val vector = TLVector<TLAbsMessage>(TLAbsMessage::class.java)
		vector.add(msg)
		db.saveMessages(vector, Kotlogram.API_LAYER, settings=settings)
		System.out.print('.')
	}

	override fun onShortMessage(client: TelegramClient, message: TLUpdateShortMessage) {
		val m = message
		logger.debug("onShortMessage - " + m.getOut() + " - " + m.getUserId() + " - " + m.getMessage())
		val from_id: Int
		val to_id: Int
		if (m.getOut() == true) {
			from_id = user_manager.id
			to_id = m.getUserId()
		} else {
			to_id = user_manager.id
			from_id = m.getUserId()
		}
		val msg = TLMessage(
			m.getOut(),
			m.getMentioned(),
			m.getMediaUnread(),
			m.getSilent(),
			false,
			m.getId(),
			from_id,
			TLPeerUser(to_id),
			m.getFwdFrom(),
			m.getViaBotId(),
			m.getReplyToMsgId(),
			m.getDate(),
			m.getMessage(), null, null,
			m.getEntities(), null, null)
		val vector = TLVector<TLAbsMessage>(TLAbsMessage::class.java)
		vector.add(msg)
		db.saveMessages(vector, Kotlogram.API_LAYER, settings=settings)
		System.out.print('.')
	}

	override fun onShortSentMessage(client: TelegramClient, message: TLUpdateShortSentMessage) {
		logger.debug("onShortSentMessage")
	}

	override fun onUpdateTooLong(client: TelegramClient) {
		logger.debug("onUpdateTooLong")
	}

	private fun processUpdate(update: TLAbsUpdate) {
		if (update is TLUpdateNewMessage) {
			val abs_msg = update.getMessage()
			val vector = TLVector<TLAbsMessage>(TLAbsMessage::class.java)
			vector.add(abs_msg)
			db.saveMessages(vector, Kotlogram.API_LAYER, settings=settings)
			System.out.print('.')
			if (abs_msg is TLMessage && settings.download_media==true) {
				val fm = FileManagerFactory.getFileManager(abs_msg, user_manager, file_base, settings)
				if (fm != null && !fm.isEmpty && !fm.downloaded) {
					try {
						fm.download()
					} catch (e: Exception) {
						System.out.println("We got an exception while downloading media, but we're going to ignore it.")
						System.out.println("Here it is anyway:")
						e.printStackTrace()
					}

				}
			}
		} else {
			// ignore everything else...
		}
	}
}
