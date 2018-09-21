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

import de.fabianonline.telegram_backup.UserManager
import de.fabianonline.telegram_backup.Database
import de.fabianonline.telegram_backup.DownloadProgressInterface
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.tl.core.TLIntVector
import com.github.badoualy.telegram.tl.core.TLObject
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs
import com.github.badoualy.telegram.tl.api.messages.TLDialogsSlice
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.api.upload.TLFile
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import com.github.badoualy.telegram.tl.api.request.TLRequestUploadGetFile
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import com.google.gson.Gson
import com.github.salomonbrys.kotson.*
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.getAs

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.LinkedList
import java.util.HashMap
import java.util.Random
import java.net.URL
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import org.apache.commons.io.FileUtils

enum class MessageSource(val descr: String) {
	NORMAL(""),
	CHANNEL("channel"),
	SUPERGROUP("supergroup")
}

class DownloadManager(val client: TelegramClient, val prog: DownloadProgressInterface, val db: Database, val user_manager: UserManager, val settings: Settings, val file_base: String) {
	@Throws(RpcErrorException::class, IOException::class)
	fun downloadMessages(limit: Int?) {
		logger.info("This is downloadMessages with limit {}", limit)
		logger.info("Downloading the last dialogs")
		System.out.println("Downloading most recent dialogs... ")
		var max_message_id = 0
		var result: ChatList? = null
		
		Utils.obeyFloodWait() {
			result = getChats()
		}
		
		val chats = result!!
		
		logger.debug("Got {} dialogs, {} supergoups, {} channels", chats.dialogs.size, chats.supergroups.size, chats.channels.size)

		for (d in chats.dialogs) {
			if (d.getTopMessage() > max_message_id) {
				logger.trace("Updating top message id: {} => {}. Dialog type: {}", max_message_id, d.getTopMessage(), d.getPeer().javaClass)
				max_message_id = d.getTopMessage()
			}
		}
		System.out.println("Top message ID is " + max_message_id)
		var max_database_id = db.getTopMessageID()
		System.out.println("Top message ID in database is " + max_database_id)
		if (limit != null) {
			System.out.println("Limit is set to " + limit)
			max_database_id = Math.max(max_database_id, max_message_id - limit)
			System.out.println("New top message id 'in database' is " + max_database_id)
		}
		if (max_message_id - max_database_id > 1000000) {
			System.out.println("Would have to load more than 1 million messages which is not supported by telegram. Capping the list.")
			logger.debug("max_message_id={}, max_database_id={}, difference={}", max_message_id, max_database_id, max_message_id - max_database_id)
			max_database_id = Math.max(0, max_message_id - 1000000)
			logger.debug("new max_database_id: {}", max_database_id)
		}

		if (max_database_id == max_message_id) {
			System.out.println("No new messages to download.")
		} else if (max_database_id > max_message_id) {
			throw RuntimeException("max_database_id is bigger than max_message_id. This shouldn't happen. But the telegram api nonetheless does that sometimes. Just ignore this error, wait a few seconds and then try again.")
		} else {
			val start_id = max_database_id + 1
			val end_id = max_message_id

			val ids = makeIdList(start_id, end_id)
			downloadMessages(ids, null)
		}

		logger.info("Searching for missing messages in the db")
		System.out.println("Checking message database for completeness...")
		val db_count = db.getMessageCount()
		val db_max = db.getTopMessageID()
		logger.debug("db_count: {}", db_count)
		logger.debug("db_max: {}", db_max)

			/*if (db_count != db_max) {
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

					downloadMessages(downloadable_missing_ids, null);
				}

				logger.info("Logging this run");
				db.logRun(Math.min(max_database_id + 1, max_message_id), max_message_id, count_missing);
			}
			*/

		if (settings.download_channels) {
			println("Checking channels...")
			for (channel in chats.channels) { if (channel.download) downloadMessagesFromChannel(channel, limit) }
		}
				
		if (settings.download_supergroups) {
			println("Checking supergroups...")
			for (supergroup in chats.supergroups) { if (supergroup.download) downloadMessagesFromChannel(supergroup, limit) }
		}
	}

	private fun downloadMessagesFromChannel(channel: Channel, limit: Int?) {
		val obj = channel.obj
		var max_known_id = db.getTopMessageIDForChannel(channel.id)
		if (obj.getTopMessage() > max_known_id) {
			if (limit != null) {
				max_known_id = Math.max(max_known_id, obj.getTopMessage() - limit)
			}
			val ids = makeIdList(max_known_id + 1, obj.getTopMessage())
			var channel_name = channel.title

			val input_channel = TLInputChannel(channel.id, channel.access_hash)
			val source_type = channel.message_source
			downloadMessages(ids, input_channel, source_type=source_type, source_name=channel_name)
		}
	}

	@Throws(RpcErrorException::class, IOException::class)
	private fun downloadMessages(ids: MutableList<Int>, channel: TLInputChannel?, source_type: MessageSource = MessageSource.NORMAL, source_name: String? = null) {
		val source_string = if (source_type == MessageSource.NORMAL) {
			null
		} else if (source_name == null) {
			"unknown ${source_type.descr}"
		} else {
			"${source_type.descr} $source_name"
		}
		prog.onMessageDownloadStart(ids.size, source_string)

		logger.debug("Entering download loop")
		while (ids.size > 0) {
			logger.trace("Loop")
			val vector = TLIntVector()
			val download_count = Config.GET_MESSAGES_BATCH_SIZE
			logger.trace("download_count: {}", download_count)
			for (i in 0 until download_count) {
				if (ids.size == 0) break
				vector.add(ids.removeAt(0))
			}
			logger.trace("vector.size(): {}", vector.size)
			logger.trace("ids.size(): {}", ids.size)

			var resp: TLAbsMessages? = null
			try {
				Utils.obeyFloodWait(max_tries=5) {
					if (channel == null) {
						resp = client.messagesGetMessages(vector)
					} else {
						resp = client.channelsGetMessages(channel, vector)
					}
				}
			} catch (e: MaxTriesExceededException) {
				CommandLineController.show_error("Couldn't getMessages after 5 tries. Quitting.")
			}
			val response = resp!!
			logger.trace("response.getMessages().size(): {}", response.getMessages().size)
			if (response.getMessages().size != vector.size) {
				CommandLineController.show_error("Requested ${vector.size} messages, but got ${response.getMessages().size}. That is unexpected. Quitting.")
			}

			prog.onMessageDownloaded(response.getMessages().size)
			db.saveMessages(response.getMessages(), Kotlogram.API_LAYER, source_type=source_type, settings=settings)
			db.saveChats(response.getChats())
			db.saveUsers(response.getUsers())
			logger.trace("Sleeping")
			try { TimeUnit.MILLISECONDS.sleep(Config.DELAY_AFTER_GET_MESSAGES) } catch (e: InterruptedException) { }
		}
		logger.debug("Finished.")

		prog.onMessageDownloadFinished()
	}

	@Throws(RpcErrorException::class, IOException::class)
	fun downloadMedia() {
		download_client = client.getDownloaderClient()
		logger.info("This is _downloadMedia")
		logger.info("Checking if there are messages in the DB with a too old API layer")
		val ids = db.getIdsFromQuery("SELECT id FROM messages WHERE has_media=1 AND api_layer<" + Kotlogram.API_LAYER)
		if (ids.size > 0) {
			System.out.println("You have ${ids.size} messages in your db that need an update. Doing that now.")
			logger.debug("Found {} messages", ids.size)
			downloadMessages(ids, null, source_type=MessageSource.NORMAL)
		}

		val message_count = db.getMessagesWithMediaCount()
		prog.onMediaDownloadStart(message_count)
		var offset = 0
		val limit = 1000
		while (true) {
			logger.debug("Querying messages with media, limit={}, offset={}", limit, offset)
			val messages = db.getMessagesWithMedia(limit, offset)
			if (messages.size == 0) break
			offset += limit
			logger.debug("Database returned {} messages with media", messages.size)
			for (pair in messages) {
				val id = pair.first
				val json = pair.second
				try {
				val m = FileManagerFactory.getFileManager(json, file_base, settings=settings)!!
				logger.trace("message {}, {}, {}, {}, {}",
					id,
					m.javaClass.getSimpleName(),
					if (m.isEmpty) "empty" else "non-empty",
					if (m.downloaded) "downloaded" else "not downloaded")
				if (m.isEmpty) {
					prog.onMediaDownloadedEmpty()
				} else if (m.downloaded) {
					prog.onMediaAlreadyPresent(m)
				} else if (settings.max_file_age>0 && (System.currentTimeMillis() / 1000) - json["date"].int > settings.max_file_age * 24 * 60 * 60) {
					prog.onMediaSkipped()
				} else if (settings.max_file_size>0 && settings.max_file_size*1024*1024 > m.size) {
					prog.onMediaSkipped()
				} else if (settings.blacklist_extensions.contains(m.extension)) {
					prog.onMediaSkipped()
				} else {
					try {
						val result = m.download(prog)
						if (result) {
							prog.onMediaDownloaded(m)
						} else {
							logger.trace("onMediaFailed")
							prog.onMediaFailed()
						}
					} catch (e: TimeoutException) {
						// do nothing - skip this file
						logger.trace("TimeoutException onMedia")
						prog.onMediaFailed()
					}
				}
				} catch (e: IllegalStateException) {
					println(json.toPrettyJson())
					throw e
				}
			}
		}
		prog.onMediaDownloadFinished()
	}

	private fun makeIdList(start: Int, end: Int): MutableList<Int> {
		val a = LinkedList<Int>()
		for (i in start..end) a.add(i)
		return a
	}
	
	fun getChats(): ChatList {
		val cl = ChatList()
		logger.debug("Getting list of chats...")
		val limit = 100
		var offset = 0
		while (true) {
			var temp: TLAbsDialogs? = null
			logger.trace("Calling messagesGetDialogs with offset {}", offset)
			Utils.obeyFloodWait {
				temp = client.messagesGetDialogs(false, offset, 0, TLInputPeerEmpty(), limit)
			}
			val dialogs = temp!!
			val last_message = dialogs.messages.filter{ it is TLMessage || it is TLMessageService }.last()
			offset = when(last_message) {
				is TLMessage -> last_message.date
				is TLMessageService -> last_message.date
				else -> throw RuntimeException("Unexpected last_message type ${last_message.javaClass}")
			}
			logger.trace("Got {} dialogs back", dialogs.dialogs.size)
			logger.trace("New offset will be {}", offset)
			
			// Add dialogs
			cl.dialogs.addAll(dialogs.getDialogs().filter{it.getPeer() !is TLPeerChannel})
			
			// Add supergoups and channels
			for (tl_channel in dialogs.getChats().filter{it is TLChannel}.map{it as TLChannel}) {
				val tl_peer_channel = dialogs.getDialogs().find{var p = it.getPeer() ; p is TLPeerChannel && p.getChannelId()==tl_channel.getId()}
				
				if (tl_peer_channel == null) continue
				
				var download = true
				if (settings.whitelist_channels.isNotEmpty()) {
					download = settings.whitelist_channels.contains(tl_channel.getId().toString())
				} else if (settings.blacklist_channels.isNotEmpty()) {
					download = !settings.blacklist_channels.contains(tl_channel.getId().toString())
				}
				val channel = Channel(id=tl_channel.getId(), access_hash=tl_channel.getAccessHash(), title=tl_channel.getTitle(), obj=tl_peer_channel, download=download)
				if (tl_channel.getMegagroup()) {
					channel.message_source = MessageSource.SUPERGROUP
					cl.supergroups.add(channel)
				} else {
					channel.message_source = MessageSource.CHANNEL
					cl.channels.add(channel)
				}
			}
			
			if (dialogs.dialogs.size < limit) {
				logger.debug("Got only ${dialogs.dialogs.size} back instead of ${limit}. Stopping the loop.")
				logger.debug("Got ${cl.dialogs.size} groups, ${cl.channels.size} channels and ${cl.supergroups.size} supergroups.")
				break;
			}
		}
		
		return cl
	}
	
	class ChatList {
		val dialogs = mutableListOf<TLDialog>()
		val supergroups = mutableListOf<Channel>()
		val channels = mutableListOf<Channel>()
	}
	
	class Channel(val id: Int, val access_hash: Long, val title: String, val obj: TLDialog, val download: Boolean) {
		lateinit var message_source: MessageSource
	}

	companion object {
		internal var download_client: TelegramClient? = null
		internal var last_download_succeeded = true
		internal val logger = LoggerFactory.getLogger(DownloadManager::class.java)

		@Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
		fun downloadFile(targetFilename: String, size: Int, dcId: Int, volumeId: Long, localId: Int, secret: Long, prog: DownloadProgressInterface?) {
			val loc = TLInputFileLocation(volumeId, localId, secret)
			downloadFileFromDc(targetFilename, loc, dcId, size, prog)
		}

		@Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
		fun downloadFile(targetFilename: String, size: Int, dcId: Int, id: Long, accessHash: Long, version: Int = 0, prog: DownloadProgressInterface?) {
			val loc = TLInputDocumentFileLocation(id, accessHash, version)
			logger.trace("TLInputDocumentFileLocation: {}", loc)
			downloadFileFromDc(targetFilename, loc, dcId, size, prog)
		}

		@Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
		private fun downloadFileFromDc(target: String, loc: TLAbsInputFileLocation, dcID: Int, size: Int, prog: DownloadProgressInterface?): Boolean {
			var fos: FileOutputStream? = null
			try {
				val temp_filename = target + ".downloading"
				logger.debug("Downloading file {}", target)
				logger.trace("Temporary filename: {}", temp_filename)

				var offset = 0
				if (File(temp_filename).isFile()) {
					logger.info("Temporary filename already exists; continuing this file")
					offset = File(temp_filename).length().toInt()
					if (offset >= size) {
						logger.warn("Temporary file size is >= the target size. Assuming corrupt file & deleting it")
						File(temp_filename).delete()
						offset = 0
					}
				}
				logger.trace("offset before the loop is {}", offset)
				fos = FileOutputStream(temp_filename, true)
				if (prog != null) prog.onMediaFileDownloadStarted()
				do {
					logger.trace("offset: {} block_size: {} size: {}", offset, size, size)
					val req = TLRequestUploadGetFile(loc, offset, 1024*1024)
					var resp: TLFile? = null
					try {
						Utils.obeyFloodWait() {
							resp = download_client!!.executeRpcQuery(req, dcID) as TLFile
						}
					} catch (e: RpcErrorException) {
						logger.trace("RpcErrorException")
						logger.trace("{}", e.localizedMessage)
						if (e.getCode() == 400) {
							logger.trace("code 400")
							// Somehow this file is broken. No idea why. Let's skip it for now.
							return false
						}
						throw e
					}
					
					val response = resp!!
					if (prog!=null) prog.onMediaFileDownloadStep()
					offset += response.getBytes().getData().size
					logger.trace("response: {} total size: {}", response.getBytes().getData().size, offset)

					fos.write(response.getBytes().getData())
					fos.flush()
					try { TimeUnit.MILLISECONDS.sleep(Config.DELAY_AFTER_GET_FILE) } catch (e: InterruptedException) { }

				} while (offset < size && response.getBytes().getData().size > 0)
				if (prog != null) prog.onMediaFileDownloadFinished()
				fos.close()
				if (offset < size) {
					System.out.println("Requested file $target with $size bytes, but got only $offset bytes.")
					File(temp_filename).delete()
					System.exit(1)
				}
				logger.trace("Renaming {} to {}", temp_filename, target)
				var rename_tries = 0
				var last_exception: IOException? = null
				while (rename_tries <= Config.RENAMING_MAX_TRIES) {
					rename_tries++
					try {
						Files.move(File(temp_filename).toPath(), File(target).toPath(), StandardCopyOption.REPLACE_EXISTING)
						last_exception = null
						break
					} catch (e: IOException) {
						logger.debug("Exception during move. rename_tries: {}. Exception: {}", rename_tries, e)
						last_exception = e
						try {
							TimeUnit.MILLISECONDS.sleep(Config.RENAMING_DELAY)
						} catch (e2: InterruptedException) {
						}

					}

				}
				if (last_exception != null) {
					throw last_exception
				}
				last_download_succeeded = true
				return true
			} catch (ex: java.io.IOException) {
				if (fos != null) fos.close()
				System.out.println("IOException happened while downloading " + target)
				throw ex
			} catch (ex: RpcErrorException) {
				if (fos != null) fos.close()
				if (ex.getCode() == 500) {
					if (!last_download_succeeded) {
						System.out.println("Got an Internal Server Error from Telegram. Since the file downloaded before also happened to get this error, we will stop downloading now. Please try again later.")
						throw ex
					}
					last_download_succeeded = false
					System.out.println("Got an Internal Server Error from Telegram. Skipping this file for now. Next run of telegram_backup will continue to download this file.")
					logger.warn(ex.toString())
					return false
				}
				System.out.println("RpcErrorException happened while downloading " + target)
				throw ex
			}

		}

		@Throws(IOException::class)
		fun downloadExternalFile(target: String, url: String, prog: DownloadProgressInterface?): Boolean {
			if (prog != null) prog.onMediaFileDownloadStarted()
			var success = true
			Fuel.download(url).destination { _, _ ->
				File(target)
			}.progress { _, _ ->
				if (prog != null) prog.onMediaFileDownloadStep()
			}.response { _, _, result ->
				success = (result is Result.Success)
			}
			if (prog != null) prog.onMediaFileDownloadFinished()
			return success
		}
	}
}
