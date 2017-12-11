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
import de.fabianonline.telegram_backup.StickerConverter
import de.fabianonline.telegram_backup.DownloadProgressInterface
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.tl.core.TLIntVector
import com.github.badoualy.telegram.tl.core.TLObject
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.api.upload.TLFile
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import com.github.badoualy.telegram.tl.api.request.TLRequestUploadGetFile
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import com.google.gson.Gson

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

class DownloadManager(internal var client: TelegramClient?, p: DownloadProgressInterface) {
    internal var user: UserManager? = null
    internal var db: Database? = null
    internal var prog: DownloadProgressInterface? = null
    internal var has_seen_flood_wait_message = false

    init {
        this.user = UserManager.getInstance()
        this.prog = p
        this.db = Database.getInstance()
    }

    @Throws(RpcErrorException::class, IOException::class)
    fun downloadMessages(limit: Int?) {
        var completed = true
        do {
            completed = true
            try {
                _downloadMessages(limit)
            } catch (e: RpcErrorException) {
                if (e.getCode() === 420) { // FLOOD_WAIT
                    completed = false
                    Utils.obeyFloodWaitException(e)
                } else {
                    throw e
                }
            } catch (e: TimeoutException) {
                completed = false
                System.out.println("")
                System.out.println("Telegram took too long to respond to our request.")
                System.out.println("I'm going to wait a minute and then try again.")
                try {
                    TimeUnit.MINUTES.sleep(1)
                } catch (e2: InterruptedException) {
                }

                System.out.println("")
            }

        } while (!completed)
    }

    @Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
    fun _downloadMessages(limit: Int?) {
        logger.info("This is _downloadMessages with limit {}", limit)
        val dialog_limit = 100
        logger.info("Downloading the last {} dialogs", dialog_limit)
        System.out.println("Downloading most recent dialogs... ")
        var max_message_id = 0
        val dialogs = client!!.messagesGetDialogs(
                0,
                0,
                TLInputPeerEmpty(),
                dialog_limit)
        logger.debug("Got {} dialogs", dialogs.getDialogs().size)

        for (d in dialogs.getDialogs()) {
            if (d.getTopMessage() > max_message_id && d.getPeer() !is TLPeerChannel) {
                logger.trace("Updating top message id: {} => {}. Dialog type: {}", max_message_id, d.getTopMessage(), d.getPeer().javaClass)
                max_message_id = d.getTopMessage()
            }
        }
        System.out.println("Top message ID is " + max_message_id)
        var max_database_id = db!!.getTopMessageID()
        System.out.println("Top message ID in database is " + max_database_id)
        if (limit != null) {
            System.out.println("Limit is set to " + limit!!)
            max_database_id = Math.max(max_database_id, max_message_id - limit!!)
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
            throw RuntimeException("max_database_id is bigger then max_message_id. This shouldn't happen. But the telegram api nonetheless does that sometimes. Just ignore this error, wait a few seconds and then try again.")
        } else {
            val start_id = max_database_id + 1
            val end_id = max_message_id

            val ids = makeIdList(start_id, end_id)
            downloadMessages(ids, null, null)
        }

        logger.info("Searching for missing messages in the db")
        val count_missing = 0
        System.out.println("Checking message database for completeness...")
        val db_count = db!!.getMessageCount()
        val db_max = db!!.getTopMessageID()
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

        if (CommandLineOptions.cmd_channels || CommandLineOptions.cmd_supergroups) {
            System.out.println("Processing channels and/or supergroups...")
            System.out.println("Please note that only channels/supergroups in the last 100 active chats are processed.")

            val channel_access_hashes = HashMap<Int, Long>()
            val channel_names = HashMap<Int, String>()
            val channels = LinkedList<Int>()
            val supergroups = LinkedList<Int>()

            // TODO Add chat title (and other stuff?) to the database
            for (c in dialogs.getChats()) {
                if (c is TLChannel) {
                    val ch = c as TLChannel
                    channel_access_hashes.put(c.getId(), ch.getAccessHash())
                    channel_names.put(c.getId(), ch.getTitle())
                    if (ch.getMegagroup()) {
                        supergroups.add(c.getId())
                    } else {
                        channels.add(c.getId())
                    }
                    // Channel: TLChannel
                    // Supergroup: getMegagroup()==true
                }
            }



            for (d in dialogs.getDialogs()) {
                if (d.getPeer() is TLPeerChannel) {
                    val channel_id = (d.getPeer() as TLPeerChannel).getChannelId()

                    // If this is a channel and we don't want to download channels OR
                    // it is a supergroups and we don't want to download supergroups, then
                    if (channels.contains(channel_id) && !CommandLineOptions.cmd_channels || supergroups.contains(channel_id) && !CommandLineOptions.cmd_supergroups) {
                        // Skip this chat.
                        continue
                    }
                    val max_known_id = db!!.getTopMessageIDForChannel(channel_id)
                    if (d.getTopMessage() > max_known_id) {
                        val ids = makeIdList(max_known_id + 1, d.getTopMessage())
                        val access_hash = channel_access_hashes.get(channel_id) ?: throw RuntimeException("AccessHash for Channel missing.")
                        var channel_name = channel_names.get(channel_id)
                        if (channel_name == null) {
                            channel_name = "?"
                        }
                        val channel = TLInputChannel(channel_id, access_hash)
                        downloadMessages(ids, channel, "channel " + channel_name!!)
                    }
                }
            }
        }
    }

    @Throws(RpcErrorException::class, IOException::class)
    private fun downloadMessages(ids: MutableList<Int>, channel: TLInputChannel?, source_string: String?) {
        prog!!.onMessageDownloadStart(ids.size, source_string)

        logger.debug("Entering download loop")
        while (ids.size > 0) {
            logger.trace("Loop")
            val vector = TLIntVector()
            val download_count = Config.GET_MESSAGES_BATCH_SIZE
            logger.trace("download_count: {}", download_count)
            for (i in 0 until download_count) {
                if (ids.size === 0) break
                vector.add(ids.removeAt(0))
            }
            logger.trace("vector.size(): {}", vector.size)
            logger.trace("ids.size(): {}", ids.size)

            var response: TLAbsMessages
            var tries = 0
            while (true) {
                logger.trace("Trying getMessages(), tries={}", tries)
                if (tries >= 5) {
                    CommandLineController.show_error("Couldn't getMessages after 5 tries. Quitting.")
                }
                tries++
                try {
                    if (channel == null) {
                        response = client!!.messagesGetMessages(vector)
                    } else {
                        response = client!!.channelsGetMessages(channel, vector)
                    }
                    break
                } catch (e: RpcErrorException) {
                    if (e.getCode() === 420) { // FLOOD_WAIT
                        Utils.obeyFloodWaitException(e, has_seen_flood_wait_message)
                        has_seen_flood_wait_message = true
                    } else {
                        throw e
                    }
                }

            }
            logger.trace("response.getMessages().size(): {}", response.getMessages().size)
            if (response.getMessages().size !== vector.size) {
                CommandLineController.show_error("Requested ${vector.size} messages, but got ${response.getMessages().size}. That is unexpected. Quitting.")
            }

            prog!!.onMessageDownloaded(response.getMessages().size)
            db!!.saveMessages(response.getMessages(), Kotlogram.API_LAYER)
            db!!.saveChats(response.getChats())
            db!!.saveUsers(response.getUsers())
            logger.trace("Sleeping")
            try {
                TimeUnit.MILLISECONDS.sleep(Config.DELAY_AFTER_GET_MESSAGES)
            } catch (e: InterruptedException) {
            }

        }
        logger.debug("Finished.")

        prog!!.onMessageDownloadFinished()
    }

    @Throws(RpcErrorException::class, IOException::class)
    fun downloadMedia() {
        download_client = client!!.getDownloaderClient()
        var completed = true
        do {
            completed = true
            try {
                _downloadMedia()
            } catch (e: RpcErrorException) {
                if (e.getTag().startsWith("420: FLOOD_WAIT_")) {
                    completed = false
                    Utils.obeyFloodWaitException(e)
                } else {
                    throw e
                }
            }
            /*catch (TimeoutException e) {
				completed = false;
				System.out.println("");
				System.out.println("Telegram took too long to respond to our request.");
				System.out.println("I'm going to wait a minute and then try again.");
				logger.warn("TimeoutException caught", e);
				try { TimeUnit.MINUTES.sleep(1); } catch(InterruptedException e2) {}
				System.out.println("");
			}*/
        } while (!completed)
    }

    @Throws(RpcErrorException::class, IOException::class)
    private fun _downloadMedia() {
        logger.info("This is _downloadMedia")
        logger.info("Checking if there are messages in the DB with a too old API layer")
        val ids = db!!.getIdsFromQuery("SELECT id FROM messages WHERE has_media=1 AND api_layer<" + Kotlogram.API_LAYER)
        if (ids.size > 0) {
            System.out.println("You have ${ids.size} messages in your db that need an update. Doing that now.")
            logger.debug("Found {} messages", ids.size)
            downloadMessages(ids, null, null)
        }

        val messages = this.db!!.getMessagesWithMedia()
        logger.debug("Database returned {} messages with media", messages.size)
        prog!!.onMediaDownloadStart(messages.size)
        for (msg in messages) {
        	if (msg==null) continue
            val m = FileManagerFactory.getFileManager(msg, user!!, client!!)
            logger.trace("message {}, {}, {}, {}, {}",
                    msg.getId(),
                    msg.getMedia().javaClass.getSimpleName().replace("TLMessageMedia", "…"),
                    m!!.javaClass.getSimpleName(),
                    if (m.isEmpty) "empty" else "non-empty",
                    if (m.downloaded) "downloaded" else "not downloaded")
            if (m.isEmpty) {
                prog!!.onMediaDownloadedEmpty()
            } else if (m.downloaded) {
                prog!!.onMediaAlreadyPresent(m)
            } else {
                try {
                    m.download()
                    prog!!.onMediaDownloaded(m)
                } catch (e: TimeoutException) {
                    // do nothing - skip this file
                    prog!!.onMediaSkipped()
                }

            }
        }
        prog!!.onMediaDownloadFinished()
    }

    private fun makeIdList(start: Int, end: Int): MutableList<Int> {
        val a = LinkedList<Int>()
        for (i in start..end) a.add(i)
        return a
    }

    companion object {
        internal var download_client: TelegramClient? = null
        internal var last_download_succeeded = true
        internal val logger = LoggerFactory.getLogger(DownloadManager::class.java)

        @Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
        fun downloadFile(client: TelegramClient, targetFilename: String, size: Int, dcId: Int, volumeId: Long, localId: Int, secret: Long) {
            val loc = TLInputFileLocation(volumeId, localId, secret)
            downloadFileFromDc(client, targetFilename, loc, dcId, size)
        }

        @Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
        fun downloadFile(client: TelegramClient, targetFilename: String, size: Int, dcId: Int, id: Long, accessHash: Long) {
            val loc = TLInputDocumentFileLocation(id, accessHash)
            downloadFileFromDc(client, targetFilename, loc, dcId, size)
        }

        @Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
        private fun downloadFileFromDc(client: TelegramClient, target: String, loc: TLAbsInputFileLocation, dcID: Int, size: Int): Boolean {
            var fos: FileOutputStream? = null
            try {
                val temp_filename = target + ".downloading"
                logger.debug("Downloading file {}", target)
                logger.trace("Temporary filename: {}", temp_filename)

                var offset = 0
                if (File(temp_filename).isFile()) {
                    logger.info("Temporary filename already exists; continuing this file")
                    offset = File(temp_filename).length() as Int
                    if (offset >= size) {
                        logger.warn("Temporary file size is >= the target size. Assuming corrupt file & deleting it")
                        File(temp_filename).delete()
                        offset = 0
                    }
                }
                logger.trace("offset before the loop is {}", offset)
                fos = FileOutputStream(temp_filename, true)
                var response: TLFile? = null
                var try_again: Boolean
                do {
                    try_again = false
                    logger.trace("offset: {} block_size: {} size: {}", offset, size, size)
                    val req = TLRequestUploadGetFile(loc, offset, size)
                    try {
                        if (dcID == null) {
                            response = download_client!!.executeRpcQuery(req) as TLFile
                        } else {
                            response = download_client!!.executeRpcQuery(req, dcID) as TLFile
                        }
                    } catch (e: RpcErrorException) {
                        if (e.getTag().startsWith("420: FLOOD_WAIT_")) {
                            try_again = true
                            Utils.obeyFloodWaitException(e)
                        } else if (e.getCode() === 400) {
                            //Somehow this file is broken. No idea why. Let's skip it for now
                            try_again = true
                            return false
                        } else {
                            throw e
                        }
                    }

                    offset += response!!.getBytes().getData().size
                    logger.trace("response: {} total size: {}", response!!.getBytes().getData().size, offset)

                    fos!!.write(response!!.getBytes().getData())
                    fos!!.flush()
                    try {
                        TimeUnit.MILLISECONDS.sleep(Config.DELAY_AFTER_GET_FILE)
                    } catch (e: InterruptedException) {
                    }

                } while (offset < size && (response!!.getBytes().getData().size > 0 || try_again))
                fos!!.close()
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
                if (fos != null) fos!!.close()
                System.out.println("IOException happened while downloading " + target)
                throw ex
            } catch (ex: RpcErrorException) {
                if (fos != null) fos!!.close()
                if (ex.getCode() === 500) {
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
        fun downloadExternalFile(target: String, url: String): Boolean {
            FileUtils.copyURLToFile(URL(url), File(target), 5000, 5000)
            return true
        }
    }
}
