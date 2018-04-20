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

import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.core.TLVector
import com.github.badoualy.telegram.api.TelegramClient
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.SQLException
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.PreparedStatement
import java.sql.Types
import java.sql.Time
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.LinkedList
import java.util.LinkedHashMap
import java.util.HashMap
import java.util.Date
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.FileAlreadyExistsException
import java.text.SimpleDateFormat
import com.google.gson.*
import com.github.salomonbrys.kotson.*

import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory

class Database constructor(val file_base: String, val user_manager: UserManager) {
	val conn: Connection
	val stmt: Statement
	val logger = LoggerFactory.getLogger(Database::class.java)
	
	init {
		println("Opening database...")
		try {
			Class.forName("org.sqlite.JDBC")
		} catch (e: ClassNotFoundException) {
			throw RuntimeException("Could not load jdbc-sqlite class.")
		}

		val path = "jdbc:sqlite:${file_base}${Config.FILE_NAME_DB}"

		try {
			conn = DriverManager.getConnection(path)!!
			stmt = conn.createStatement()
		} catch (e: SQLException) {
			throw RuntimeException("Could not connect to SQLITE database.")
		}

		// Run updates
		val updates = DatabaseUpdates(conn, this)
		updates.doUpdates()

		println("Database is ready.")
	}

	fun getTopMessageID(): Int = queryInt("SELECT MAX(message_id) FROM messages WHERE source_type IN ('group', 'dialog')")
	fun getMessageCount(): Int = queryInt("SELECT COUNT(*) FROM messages")
	fun getChatCount(): Int = queryInt("SELECT COUNT(*) FROM chats")
	fun getUserCount(): Int = queryInt("SELECT COUNT(*) FROM users")

	fun getMissingIDs(): LinkedList<Int> {
		try {
			val missing = LinkedList<Int>()
			val max = getTopMessageID()
			val rs = executeQuery("SELECT message_id FROM messages WHERE source_type IN ('group', 'dialog') ORDER BY id")
			rs.next()
			var id = rs.getInt(1)
			for (i in 1..max) {
				if (i == id) {
					rs.next()
					if (rs.isClosed()) {
						id = Integer.MAX_VALUE
					} else {
						id = rs.getInt(1)
					}
				} else if (i < id) {
					missing.add(i)
				}
			}
			rs.close()
			return missing
		} catch (e: SQLException) {
			e.printStackTrace()
			throw RuntimeException("Could not get list of ids.")
		}
	}

	fun getMessagesWithMediaCount() = queryInt("SELECT COUNT(*) FROM messages WHERE has_media=1")

	fun getMessagesWithMedia(limit: Int = 0, offset: Int = 0): LinkedList<Pair<Int, JsonObject>> {
		try {
			val list = LinkedList<Pair<Int, JsonObject>>()
			var query = "SELECT id, json FROM messages WHERE has_media=1 AND json IS NOT NULL ORDER BY id"
			if (limit > 0) query += " LIMIT ${limit} OFFSET ${offset}"
			val rs = executeQuery(query)
			val parser = JsonParser()
			while (rs.next()) {
				val obj = parser.parse(rs.getString(2)).obj
				list.add(Pair<Int, JsonObject>(rs.getInt(1), obj))
			}
			rs.close()
			return list
		} catch (e: Exception) {
			e.printStackTrace()
			throw RuntimeException("Exception occured. See above.")
		}

	}

	fun getMessagesFromUserCount() = queryInt("SELECT COUNT(*) FROM messages WHERE sender_id=" + user_manager.id)

	fun getMessageTypesWithCount(): HashMap<String, Int> = getMessageTypesWithCount(GlobalChat())

	fun getMessageMediaTypesWithCount(): HashMap<String, Int> = getMessageMediaTypesWithCount(GlobalChat())
	
	fun getMessageSourceTypeWithCount(): HashMap<String, Int> {
		val map = HashMap<String, Int>()
		try {
			val rs = executeQuery("SELECT COUNT(id), source_type FROM messages GROUP BY source_type ORDER BY source_type")
			while (rs.next()) {
				val source_type = rs.getString(2) ?: "null"
				map.put("count.messages.source_type.${source_type}", rs.getInt(1))
			}
			rs.close()
			return map
		} catch (e:Exception) {
			throw RuntimeException(e)
		}
	}

	fun getMessageApiLayerWithCount(): HashMap<String, Int> {
		val map = HashMap<String, Int>()
		try {
			val rs = executeQuery("SELECT COUNT(id), api_layer FROM messages GROUP BY api_layer ORDER BY api_layer")
			while (rs.next()) {
				var layer = rs.getInt(2)
				map.put("count.messages.api_layer.$layer", rs.getInt(1))
			}
			rs.close()
			return map
		} catch (e: Exception) {
			throw RuntimeException(e)
		}
	}

	fun getMessageAuthorsWithCount(): HashMap<String, Any> = getMessageAuthorsWithCount(GlobalChat())

	fun getMessageTimesMatrix(): Array<IntArray> = getMessageTimesMatrix(GlobalChat())

	fun getEncoding(): String = queryString("PRAGMA encoding")

	fun getListOfChatsForExport(): LinkedList<Chat> {
		val list = LinkedList<Chat>()

		val rs = executeQuery("SELECT chats.id, chats.name, COUNT(messages.id) as c " +
			"FROM chats, messages WHERE messages.source_type IN('group', 'supergroup', 'channel') AND messages.source_id=chats.id " +
			"GROUP BY chats.id ORDER BY c DESC")
		while (rs.next()) {
			list.add(Chat(rs.getInt(1), rs.getString(2), rs.getInt(3)))
		}
		rs.close()
		return list
	}


	fun getListOfDialogsForExport(): LinkedList<Dialog> {
		val list = LinkedList<Dialog>()
		val rs = executeQuery(
			"SELECT users.id, first_name, last_name, username, COUNT(messages.id) as c " +
				"FROM users, messages WHERE messages.source_type='dialog' AND messages.source_id=users.id " +
				"GROUP BY users.id ORDER BY c DESC")
		while (rs.next()) {
			list.add(Dialog(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)))
		}
		rs.close()
		return list
	}

	fun backupDatabase(currentVersion: Int) {
		val filename = String.format(Config.FILE_NAME_DB_BACKUP, currentVersion)
		System.out.println("  Creating a backup of your database as " + filename)
		try {
			val src = file_base + Config.FILE_NAME_DB
			val dst = file_base + filename
			logger.debug("Copying {} to {}", src.anonymize(), dst.anonymize())
			Files.copy(
				File(src).toPath(),
				File(dst).toPath())
		} catch (e: FileAlreadyExistsException) {
			logger.warn("Backup already exists:", e)
		} catch (e: IOException) {
			e.printStackTrace()
			throw RuntimeException("Could not create backup.")
		}

	}

	fun getTopMessageIDForChannel(id: Int): Int = queryInt("SELECT MAX(message_id) FROM messages WHERE source_id=$id AND source_type IN('channel', 'supergroup')")

	fun logRun(start_id: Int, end_id: Int, count: Int) {
		val ps = conn.prepareStatement("INSERT INTO runs " +
			"(time,        start_id, end_id, count_missing) " +
			"VALUES " +
			"(DateTime('now'),    ?,        ?,      ?            )")
		ps.setInt(1, start_id)
		ps.setInt(2, end_id)
		ps.setInt(3, count)
		ps.execute()
		ps.close()
	}
	
	fun executeQuery(query: String): ResultSet {
		logger.trace("Query: {}", query)
		try {
			return stmt.executeQuery(query)
		} catch (e: SQLException) {
			throw RuntimeException("An SQL error happened. Query: ${query} Error message: ${e.message}", e)
		}
	}

	fun queryInt(query: String): Int {
		val rs = executeQuery(query)
		rs.next()
		val result = rs.getInt(1)
		rs.close()
		return result
	}
	
	fun queryString(query: String): String {
		val rs = executeQuery(query)
		rs.next()
		val result = rs.getString(1)
		rs.close()
		return result
	}
	
	fun queryStringMap(query: String): Map<String, String> {
		val map = mutableMapOf<String, String>()
		val rs = executeQuery(query)
		while(rs.next()) {
			map.put(rs.getString(1), rs.getString(2))
		}
		rs.close()
		return map
	}

	@Synchronized
	fun saveMessages(all: TLVector<TLAbsMessage>, api_layer: Int, source_type: MessageSource = MessageSource.NORMAL, settings: Settings?) {
		val columns = "(message_id, message_type, source_type, source_id, sender_id, fwd_from_id, text, time, has_media, media_type, media_file, media_size, data, api_layer, json) " +
			"VALUES " +
		              "(?,          ?,            ?,           ?,         ?,         ?,           ?,    ?,    ?,         ?,          ?,          ?,          ?,    ?,         ?)"
		              //1           2             3            4          5          6            7     8     9          10          11          12          13    14         15
		val ps = conn.prepareStatement("INSERT OR REPLACE INTO messages " + columns)
		val ps_insert_or_ignore = conn.prepareStatement("INSERT OR IGNORE INTO messages " + columns)

		for (msg in all) {
			if (msg is TLMessage) {
				ps.setInt(1, msg.getId())
				ps.setString(2, "message")
				val peer = msg.getToId()
				if (peer is TLPeerChat) {
					ps.setString(3, "group")
					ps.setInt(4, peer.getChatId())
				} else if (peer is TLPeerUser) {
					var id = peer.getUserId()
					if (id == user_manager.id) {
						id = msg.getFromId()
					}
					ps.setString(3, "dialog")
					ps.setInt(4, id)
				} else if (peer is TLPeerChannel) {
					if (source_type == MessageSource.CHANNEL) {
						ps.setString(3, "channel")
					} else if (source_type == MessageSource.SUPERGROUP) {
						ps.setString(3, "supergroup")
					} else {
						throw RuntimeException("Got a TLPeerChannel, but were expecting $source_type")
					}
					ps.setInt(4, peer.getChannelId())
				} else {
					throw RuntimeException("Unexpected Peer type: " + peer.javaClass)
				}

				if (peer is TLPeerChannel && msg.getFromId() == null) {
					ps.setNull(5, Types.INTEGER)
				} else {
					ps.setInt(5, msg.getFromId())
				}

				if (msg.getFwdFrom() != null && msg.getFwdFrom().getFromId() != null) {
					ps.setInt(6, msg.getFwdFrom().getFromId())
				} else {
					ps.setNull(6, Types.INTEGER)
				}

				var text = msg.getMessage()
				if ((text == null || text.equals("")) && msg.getMedia() != null) {
					val media = msg.getMedia()
					if (media is TLMessageMediaDocument) {
						text = media.getCaption()
					} else if (media is TLMessageMediaPhoto) {
						text = media.getCaption()
					}
				}
				ps.setString(7, text)
				ps.setString(8, "" + msg.getDate())
				val f = FileManagerFactory.getFileManager(msg, file_base, settings)
				if (f == null) {
					ps.setNull(9, Types.BOOLEAN)
					ps.setNull(10, Types.VARCHAR)
					ps.setNull(11, Types.VARCHAR)
					ps.setNull(12, Types.INTEGER)
				} else {
					ps.setBoolean(9, true)
					ps.setString(10, f.name)
					ps.setString(11, f.targetFilename)
					ps.setInt(12, f.size)
				}
				val stream = ByteArrayOutputStream()
				msg.serializeBody(stream)
				ps.setBytes(13, stream.toByteArray())
				ps.setInt(14, api_layer)
				ps.setString(15, msg.toJson())
				ps.addBatch()
			} else if (msg is TLMessageService) {
				ps_insert_or_ignore.setInt(1, msg.getId())
				ps_insert_or_ignore.setString(2, "service_message")
				
				val peer = msg.getToId()
				if (peer is TLPeerChat) {
					ps.setString(3, "group")
					ps.setInt(4, peer.getChatId())
				} else if (peer is TLPeerUser) {
					var id = peer.getUserId()
					if (id == user_manager.id) {
						id = msg.getFromId()
					}
					ps.setString(3, "dialog")
					ps.setInt(4, id)
				} else if (peer is TLPeerChannel) {
					// Messages in channels don't have a sender.
					if (msg.getFromId() == null) {
						ps.setString(3, "channel")
					} else {
						ps.setString(3, "supergroup")
					}
					ps.setInt(4, peer.getChannelId())
				} else {
					throw RuntimeException("Unexpected Peer type: " + peer.javaClass)
				}
				
				ps_insert_or_ignore.setNull(5, Types.INTEGER)
				ps_insert_or_ignore.setNull(6, Types.INTEGER)
				ps_insert_or_ignore.setNull(7, Types.VARCHAR)
				ps_insert_or_ignore.setNull(8, Types.INTEGER)
				ps_insert_or_ignore.setNull(9, Types.BOOLEAN)
				ps_insert_or_ignore.setNull(10, Types.VARCHAR)
				ps_insert_or_ignore.setNull(11, Types.VARCHAR)
				ps_insert_or_ignore.setNull(12, Types.INTEGER)
				ps_insert_or_ignore.setNull(13, Types.BLOB)
				ps_insert_or_ignore.setInt(14, api_layer)
				ps_insert_or_ignore.setString(15, msg.toJson())
				ps_insert_or_ignore.addBatch()
			} else if (msg is TLMessageEmpty) {
				ps_insert_or_ignore.setInt(1, msg.getId())
				ps_insert_or_ignore.setString(2, "empty_message")
				ps_insert_or_ignore.setNull(3, Types.INTEGER)
				ps_insert_or_ignore.setNull(4, Types.INTEGER)
				ps_insert_or_ignore.setNull(5, Types.INTEGER)
				ps_insert_or_ignore.setNull(6, Types.INTEGER)
				ps_insert_or_ignore.setNull(7, Types.VARCHAR)
				ps_insert_or_ignore.setNull(8, Types.INTEGER)
				ps_insert_or_ignore.setNull(9, Types.BOOLEAN)
				ps_insert_or_ignore.setNull(10, Types.VARCHAR)
				ps_insert_or_ignore.setNull(11, Types.VARCHAR)
				ps_insert_or_ignore.setNull(12, Types.INTEGER)
				ps_insert_or_ignore.setNull(13, Types.BLOB)
				ps_insert_or_ignore.setInt(14, api_layer)
				ps_insert_or_ignore.setNull(15, Types.VARCHAR)
				ps_insert_or_ignore.addBatch()
			} else {
				throw RuntimeException("Unexpected Message type: " + msg.javaClass)
			}
		}
		conn.setAutoCommit(false)
		ps.executeBatch()
		ps.clearBatch()
		ps_insert_or_ignore.executeBatch()
		ps_insert_or_ignore.clearBatch()
		conn.commit()
		conn.setAutoCommit(true)
		
		ps.close()
		ps_insert_or_ignore.close()
	}

	@Synchronized
	fun saveChats(all: TLVector<TLAbsChat>) {
		val ps_insert_or_replace = conn.prepareStatement(
			"INSERT OR REPLACE INTO chats " +
				"(id, name, type, json, api_layer) " +
				"VALUES " +
				"(?,  ?,    ?,    ?,    ?)")
		val ps_insert_or_ignore = conn.prepareStatement(
			"INSERT OR IGNORE INTO chats " +
				"(id, name, type, json, api_layer) " +
				"VALUES " +
				"(?,  ?,    ?,    ?,    ?)")

		for (abs in all) {
			ps_insert_or_replace.setInt(1, abs.getId())
			ps_insert_or_ignore.setInt(1, abs.getId())
			val json = abs.toJson()
			ps_insert_or_replace.setString(4, json)
			ps_insert_or_ignore.setString(4, json)
			ps_insert_or_replace.setInt(5, Kotlogram.API_LAYER)
			ps_insert_or_ignore.setInt(5, Kotlogram.API_LAYER)
			if (abs is TLChatEmpty) {
				ps_insert_or_ignore.setNull(2, Types.VARCHAR)
				ps_insert_or_ignore.setString(3, "empty_chat")
				ps_insert_or_ignore.addBatch()
			} else if (abs is TLChatForbidden) {
				ps_insert_or_replace.setString(2, abs.getTitle())
				ps_insert_or_replace.setString(3, "chat")
				ps_insert_or_replace.addBatch()
			} else if (abs is TLChannelForbidden) {
				ps_insert_or_replace.setString(2, abs.getTitle())
				ps_insert_or_replace.setString(3, "channel")
				ps_insert_or_replace.addBatch()
			} else if (abs is TLChat) {
				ps_insert_or_replace.setString(2, abs.getTitle())
				ps_insert_or_replace.setString(3, "chat")
				ps_insert_or_replace.addBatch()
			} else if (abs is TLChannel) {
				ps_insert_or_replace.setString(2, abs.getTitle())
				ps_insert_or_replace.setString(3, "channel")
				ps_insert_or_replace.addBatch()
			} else {
				throw RuntimeException("Unexpected " + abs.javaClass)
			}
		}
		conn.setAutoCommit(false)
		ps_insert_or_ignore.executeBatch()
		ps_insert_or_ignore.clearBatch()
		ps_insert_or_replace.executeBatch()
		ps_insert_or_replace.clearBatch()
		conn.commit()
		conn.setAutoCommit(true)
		
		ps_insert_or_ignore.close()
		ps_insert_or_replace.close()
	}

	@Synchronized
	fun saveUsers(all: TLVector<TLAbsUser>) {
		val ps_insert_or_replace = conn.prepareStatement(
			"INSERT OR REPLACE INTO users " +
				"(id, first_name, last_name, username, type, phone, json, api_layer) " +
				"VALUES " +
				"(?,  ?,          ?,         ?,        ?,    ?,     ?,    ?)")
		val ps_insert_or_ignore = conn.prepareStatement(
			"INSERT OR IGNORE INTO users " +
				"(id, first_name, last_name, username, type, phone, json, api_layer) " +
				"VALUES " +
				"(?,  ?,          ?,         ?,        ?,    ?,     ?,    ?)")
		for (abs in all) {
			if (abs is TLUser) {
				val user = abs
				ps_insert_or_replace.setInt(1, user.getId())
				ps_insert_or_replace.setString(2, user.getFirstName())
				ps_insert_or_replace.setString(3, user.getLastName())
				ps_insert_or_replace.setString(4, user.getUsername())
				ps_insert_or_replace.setString(5, "user")
				ps_insert_or_replace.setString(6, user.getPhone())
				ps_insert_or_replace.setString(7, user.toJson())
				ps_insert_or_replace.setInt(8, Kotlogram.API_LAYER)
				ps_insert_or_replace.addBatch()
			} else if (abs is TLUserEmpty) {
				ps_insert_or_ignore.setInt(1, abs.getId())
				ps_insert_or_ignore.setNull(2, Types.VARCHAR)
				ps_insert_or_ignore.setNull(3, Types.VARCHAR)
				ps_insert_or_ignore.setNull(4, Types.VARCHAR)
				ps_insert_or_ignore.setString(5, "empty_user")
				ps_insert_or_ignore.setNull(6, Types.VARCHAR)
				ps_insert_or_ignore.setNull(7, Types.VARCHAR)
				ps_insert_or_ignore.setInt(8, Kotlogram.API_LAYER)
				ps_insert_or_ignore.addBatch()
			} else {
				throw RuntimeException("Unexpected " + abs.javaClass)
			}
		}
		conn.setAutoCommit(false)
		ps_insert_or_ignore.executeBatch()
		ps_insert_or_ignore.clearBatch()
		ps_insert_or_replace.executeBatch()
		ps_insert_or_replace.clearBatch()
		conn.commit()
		conn.setAutoCommit(true)
		
		ps_insert_or_ignore.close()
		ps_insert_or_replace.close()
	}
	
	fun fetchSettings() = queryStringMap("SELECT key, value FROM settings")
	
	fun saveSetting(key: String, value: String?) {
		val ps = conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")
		ps.setString(1, key)
		if (value==null) {
			ps.setNull(2, Types.VARCHAR)
		} else {
			ps.setString(2, value)
		}
		ps.execute()
		ps.close()
	}
	
	fun getIdsFromQuery(query: String): LinkedList<Int> {
		val list = LinkedList<Int>()
		val rs = executeQuery(query)
		while (rs.next()) {
			list.add(rs.getInt(1))
		}
		rs.close()
		return list
	}

	fun getMessageTypesWithCount(c: AbstractChat): HashMap<String, Int> {
		val map = HashMap<String, Int>()
		val rs = executeQuery("SELECT message_type, COUNT(message_id) FROM messages WHERE " + c.query + " GROUP BY message_type")
		while (rs.next()) {
			map.put("count.messages.type." + rs.getString(1), rs.getInt(2))
		}
		rs.close()
		return map
	}

	fun getMessageMediaTypesWithCount(c: AbstractChat): HashMap<String, Int> {
		val map = HashMap<String, Int>()
		var count = 0
		val rs = executeQuery("SELECT media_type, COUNT(message_id) FROM messages WHERE " + c.query + " GROUP BY media_type")
		while (rs.next()) {
			var type = rs.getString(1) ?: "null"
			if (type != "null") count += rs.getInt(2)
			map.put("count.messages.media_type.${type}", rs.getInt(2))
		}
		map.put("count.messages.media_type.any", count)
		rs.close()
		return map
	}

	fun getMessageAuthorsWithCount(c: AbstractChat): HashMap<String, Any> {
		val map = HashMap<String, Any>()
		val all_data = LinkedList<HashMap<String, String>>()
		var count_others = 0
		// Set a default value for 'me' to fix the charts for channels - cause I
		// possibly didn't send any messages there.
		map.put("authors.count.me", 0)
		val rs = executeQuery("SELECT users.id, users.first_name, users.last_name, users.username, COUNT(messages.id) " +
			"FROM messages " +
			"LEFT JOIN users ON users.id=messages.sender_id " +
			"WHERE " + c.query + " GROUP BY sender_id")
		while (rs.next()) {
			val u: User
			val data = HashMap<String, String>()
			if (rs.getString(2) != null || rs.getString(3) != null || rs.getString(4) != null) {
				u = User(rs.getInt(1), rs.getString(2), rs.getString(3))
			} else {
				u = User(rs.getInt(1), "Unknown", "")
			}
			if (u.isMe) {
				map.put("authors.count.me", rs.getInt(5))
			} else {
				count_others += rs.getInt(5)
				data.put("name", u.name)
				data.put("count", ""+rs.getInt(5))
				all_data.add(data)
			}
			
		}
		map.put("authors.count.others", count_others)
		map.put("authors.all", all_data)
		rs.close()
		return map
	}
	
	fun getMessageCountForExport(c: AbstractChat): Int = queryInt("SELECT COUNT(*) FROM messages WHERE " + c.query)

	fun getMessageTimesMatrix(c: AbstractChat): Array<IntArray> {
		val result = Array(7) { IntArray(24) }
		val rs = executeQuery("SELECT STRFTIME('%w', time, 'unixepoch') as DAY, " +
			"STRFTIME('%H', time, 'unixepoch') AS hour, " +
			"COUNT(id) FROM messages WHERE " + c.query + " GROUP BY hour, day " +
			"ORDER BY hour, day")
		while (rs.next()) {
			result[if (rs.getInt(1) == 0) 6 else rs.getInt(1) - 1][rs.getInt(2)] = rs.getInt(3)
		}
		rs.close()
		return result
	}

	fun getMessagesForExport(c: AbstractChat, limit: Int=-1, offset: Int=0): LinkedList<HashMap<String, Any>> {
		var query = "SELECT messages.message_id as message_id, text, time*1000 as time, has_media, " +
			"media_type, media_file, media_size, users.first_name as user_first_name, users.last_name as user_last_name, " +
			"users.username as user_username, users.id as user_id, " +
			"users_fwd.first_name as user_fwd_first_name, users_fwd.last_name as user_fwd_last_name, users_fwd.username as user_fwd_username " +
			"FROM messages " +
			"LEFT JOIN users ON users.id=messages.sender_id " +
			"LEFT JOIN users AS users_fwd ON users_fwd.id=fwd_from_id WHERE " +
			c.query + " " +
			"ORDER BY messages.message_id"
		
		if ( limit != -1 ) {
			query = query + " LIMIT ${limit} OFFSET ${offset}"
		}	
		
		val rs = executeQuery(query)
		
		val format_time = SimpleDateFormat("HH:mm:ss")
		val format_date = SimpleDateFormat("d MMM yy")
		val meta = rs.getMetaData()
		val columns = meta.getColumnCount()
		val list = LinkedList<HashMap<String, Any>>()

		var count = 0
		var old_date: String? = null
		var old_user = 0
		while (rs.next()) {
			val h = HashMap<String, Any>(columns)
			for (i in 1..columns) {
				h.put(meta.getColumnName(i), rs.getObject(i))
			}
			// Additional values to make up for Mustache's inability to format dates
			val d = rs.getTime("time")
			val date = format_date.format(d)
			h.put("formatted_time", format_time.format(d))
			h.put("formatted_date", date)
			if (rs.getString("media_type") != null) {
				h.put("media_" + rs.getString("media_type"), true)
			}
			h.put("from_me", rs.getInt("user_id") == user_manager.id)
			h.put("is_new_date", !date.equals(old_date))
			h.put("odd_even", if (count % 2 == 0) "even" else "odd")
			h.put("same_user", old_user != 0 && rs.getInt("user_id") == old_user)
			old_user = rs.getInt("user_id")
			old_date = date

			list.add(h)
			count++
		}
		rs.close()
		return list
	}
	
	fun close() {
		logger.debug("Closing database.")
		try { stmt.close() } catch (e: Throwable) { logger.debug("Exception during stmt.close()", e) }
		try { conn.close() } catch (e: Throwable) { logger.debug("Exception during conn.close()", e) }
		logger.debug("Database closed.")
	}


	abstract inner class AbstractChat {
		abstract val query: String
		abstract val type: String
	}

	inner class Dialog(var id: Int, var first_name: String?, var last_name: String?, var username: String?, var count: Int?) : AbstractChat() {
		override val query = "source_type='dialog' AND source_id=" + id
		override val type = "dialog"
	}

	inner class Chat(var id: Int, var name: String?, var count: Int?) : AbstractChat() {
		override val query = "source_type IN('group', 'supergroup', 'channel') AND source_id=" + id
		override val type = "chat"
	}

	inner class User(id: Int, first_name: String?, last_name: String?) {
		var name: String
		var isMe: Boolean = false

		init {
			isMe = id == user_manager.id
			val s = StringBuilder()
			if (first_name != null) s.append(first_name + " ")
			if (last_name != null) s.append(last_name)
			name = s.toString().trim()
		}
	}

	inner class GlobalChat : AbstractChat() {
		override val query = "1=1"
		override val type = "GlobalChat"
	}

	companion object {
		fun bytesToTLMessage(b: ByteArray?): TLMessage? {
			try {
				if (b == null) return null
				val stream = ByteArrayInputStream(b)
				val msg = TLMessage()
				msg.deserializeBody(stream, TLApiContext.getInstance())
				return msg
			} catch (e: IOException) {
				e.printStackTrace()
				throw RuntimeException("Could not deserialize message.")
			}

		}
	}
}
