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

import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.core.TLVector
import com.github.badoualy.telegram.api.TelegramClient
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import javax.sql.rowset.serial.SerialBlob
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.SQLException
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.PreparedStatement
import java.sql.Types
import java.sql.Time
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
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

import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory

object Messages : Table("messages") {
	val id = integer("id").primaryKey().autoIncrement()
    val message_id = integer("message_id")
    val message_type = text("message_type")
    val source_type = text("source_type")
    val source_id = integer("source_id")
    val sender_id = integer("sender_id").nullable()
    val fwd_from_id = integer("fwd_from_id").nullable()
    val text = text("text").nullable()
    val time = integer("time").nullable()
    val has_media = bool("has_media").nullable()
    val media_type = text("media_type").nullable()
    val media_file = text("media_file").nullable()
    val media_size = integer("media_size").nullable()
    val media_json = text("media_json").nullable()
    val markup_json = text("markup_json").nullable()
    val data = blob("data").nullable()
    val api_layer = integer("api_layer").nullable()
    //val unique = uniqueIndex(source_type, source_id, message_id)
}

class Database private constructor(var client: TelegramClient) {
    private var conn: Connection? = null
    private var stmt: Statement? = null
    var user_manager: UserManager
    val db_path: String

    fun getTopMessageID(): Int {
            try {
                val rs = stmt!!.executeQuery("SELECT MAX(message_id) FROM messages WHERE source_type IN ('group', 'dialog')")
                rs.next()
                return rs.getInt(1)
            } catch (e: SQLException) {
                return 0
            }

        }

    fun getMessageCount(): Int = queryInt("SELECT COUNT(*) FROM messages")
    fun getChatCount(): Int = queryInt("SELECT COUNT(*) FROM chats")
    fun getUserCount(): Int = queryInt("SELECT COUNT(*) FROM users")

    val missingIDs: LinkedList<Int>
        get() {
            try {
                val missing = LinkedList<Int>()
                val max = getTopMessageID()
                val rs = stmt!!.executeQuery("SELECT message_id FROM messages WHERE source_type IN ('group', 'dialog') ORDER BY id")
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
                return missing
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException("Could not get list of ids.")
            }

        }

    fun getMessagesWithMedia(): LinkedList<TLMessage?> {
            try {
                val list = LinkedList<TLMessage?>()
                val rs = stmt!!.executeQuery("SELECT data FROM messages WHERE has_media=1")
                while (rs.next()) {
                    list.add(bytesToTLMessage(rs.getBytes(1)))
                }
                rs.close()
                return list
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("Exception occured. See above.")
            }

        }

    fun getMessagesFromUserCount(): Int {
            try {
                val rs = stmt!!.executeQuery("SELECT COUNT(*) FROM messages WHERE sender_id=" + user_manager.user!!.getId())
                rs.next()
                return rs.getInt(1)
        } catch (e: SQLException) {
                throw RuntimeException(e)
            }

        }

    fun getMessageTypesWithCount(): HashMap<String, Int> = getMessageTypesWithCount(GlobalChat())

    fun getMessageMediaTypesWithCount(): HashMap<String, Int> = getMessageMediaTypesWithCount(GlobalChat())

    fun getMessageApiLayerWithCount(): HashMap<String, Int> {
            val map = HashMap<String, Int>()
            try {
                val rs = stmt!!.executeQuery("SELECT COUNT(id), api_layer FROM messages GROUP BY api_layer ORDER BY api_layer")
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

    fun getEncoding(): String {
            try {
                val rs = stmt!!.executeQuery("PRAGMA encoding")
                rs.next()
                return rs.getString(1)
            } catch (e: SQLException) {
                logger.debug("SQLException: {}", e)
                return "unknown"
            }

        }


    fun getListOfChatsForExport(): LinkedList<Chat> {
            val list = LinkedList<Chat>()
            try {
                val rs = stmt!!.executeQuery("SELECT chats.id, chats.name, COUNT(messages.id) as c " +
                        "FROM chats, messages WHERE messages.source_type IN('group', 'supergroup', 'channel') AND messages.source_id=chats.id " +
                        "GROUP BY chats.id ORDER BY c DESC")
                while (rs.next()) {
                    list.add(Chat(rs.getInt(1), rs.getString(2), rs.getInt(3)))
                }
                rs.close()
                return list
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("Exception above!")
            }

        }


    fun getListOfDialogsForExport(): LinkedList<Dialog> {
            val list = LinkedList<Dialog>()
            try {
                val rs = stmt!!.executeQuery(
                        "SELECT users.id, first_name, last_name, username, COUNT(messages.id) as c " +
                                "FROM users, messages WHERE messages.source_type='dialog' AND messages.source_id=users.id " +
                                "GROUP BY users.id ORDER BY c DESC")
                while (rs.next()) {
                    list.add(Dialog(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)))
                }
                rs.close()
                return list
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("Exception above!")
            }

        }

    init {
        this.user_manager = UserManager.getInstance()
        System.out.println("Opening database...")
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            CommandLineController.show_error("Could not load jdbc-sqlite class.")
        }

        val path = "jdbc:sqlite:${user_manager.fileBase}${Config.FILE_NAME_DB}"
        db_path = path

        try {
            conn = DriverManager.getConnection(path)
            stmt = conn!!.createStatement()
        } catch (e: SQLException) {
            CommandLineController.show_error("Could not connect to SQLITE database.")
        }

        // Run updates
        val updates = DatabaseUpdates(conn!!, this)
        updates.doUpdates()
        
        System.out.println("Database is ready.")
    }

    fun backupDatabase(currentVersion: Int) {
        val filename = String.format(Config.FILE_NAME_DB_BACKUP, currentVersion)
        System.out.println("  Creating a backup of your database as " + filename)
        try {
            val src = user_manager.fileBase + Config.FILE_NAME_DB
            val dst = user_manager.fileBase + filename
            logger.debug("Copying {} to {}", src, dst)
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

    fun getTopMessageIDForChannel(id: Int): Int {
        return queryInt("SELECT MAX(message_id) FROM messages WHERE source_id=$id AND source_type IN('channel', 'supergroup')")
    }

    fun logRun(start_id: Int, end_id: Int, count: Int) {
        try {
            val ps = conn!!.prepareStatement("INSERT INTO runs " +
                    "(time,        start_id, end_id, count_missing) " +
                    "VALUES " +
                    "(DateTime('now'),    ?,        ?,      ?            )")
            ps.setInt(1, start_id)
            ps.setInt(2, end_id)
            ps.setInt(3, count)
            ps.execute()
        } catch (e: SQLException) {
        }

    }

    fun queryInt(query: String): Int {
        try {
            val rs = stmt!!.executeQuery(query)
            rs.next()
            return rs.getInt(1)
        } catch (e: SQLException) {
            throw RuntimeException("Could not get count of messages.")
        }

    }

    @Synchronized
    fun saveMessages(all: TLVector<TLAbsMessage>, my_api_layer: Int) {
        try {
            //"(id, dialog_id, from_id, from_type, text, time, has_media, data, sticker, type) " +
            //"VALUES " +
            //"(?,  ?,         ?,       ?,         ?,    ?,    ?,         ?,    ?,       ?)");
            val columns = "(message_id, message_type, source_type, source_id, sender_id, fwd_from_id, text, time, has_media, media_type, media_file, media_size, data, api_layer) " +
                    "VALUES " +
                    "(?,          ?,            ?,           ?,         ?,         ?,           ?,    ?,    ?,         ?,          ?,          ?,          ?,    ?)"
                          //1           2             3            4          5          6            7     8     9          10          11          12          13    14
            val ps = conn!!.prepareStatement("INSERT OR REPLACE INTO messages " + columns)
            val ps_insert_or_ignore = conn!!.prepareStatement("INSERT OR IGNORE INTO messages " + columns)
            
            logger.debug("Saving messages...")
            conn!!.close()
            println(db_path)
            val db = org.jetbrains.exposed.sql.Database.connect(db_path, driver="org.sqlite.JDBC")
            
            (exposedLogger as ch.qos.logback.classic.Logger).addAppender((logger as ch.qos.logback.classic.Logger).getLoggerContext().getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("root"))
            (exposedLogger as ch.qos.logback.classic.Logger).level = ch.qos.logback.classic.Level.TRACE
            transaction({
            	logger.addLogger(StdOutSqlLogger)
            	println(0)
            	Messages.selectAll()//.limit(5,0).forEach({println("${it[Messages.text]}")})
            	println("0a")
			})
				for (abs in all) {
					print(1)
					transaction {
					debug = true
					print(2)
            	logger.addLogger(StdOutSqlLogger)
            		print(3)
					if (abs is TLMessage) {
						print(4)
						val msg = abs
						println("4a")
						try {
						println("4b")
						val a = Messages.insert {
							println(5)
							it[message_id] = msg.getId()
							/*it[message_type] = "message"
							
							val peer = msg.getToId()
							if (peer is TLPeerChat) {
								it[source_type] = "group"
								it[source_id] = peer.getChatId()
							} else if (peer is TLPeerUser) {
								var id = peer.getUserId()
								if (id == user_manager.user!!.getId()) {
									id = msg.getFromId()
								}
								it[source_type] = "dialog"
								it[source_id] = id
							} else if (peer is TLPeerChannel) {
								it[source_type] = "channel"
								it[source_id] = peer.getChannelId()
							} else {
								throw RuntimeException("Unexpected Peer type: " + peer.javaClass)
							}
							
							// Messages in a channel don't have a sender -> insert a null
							it[sender_id] = if (peer is TLPeerChannel) null else msg.getFromId()
							print(6)
							it[fwd_from_id] = msg.getFwdFrom()?.getFromId()
							
							var msg_text = msg.getMessage()
							if ((msg_text == null || msg_text.equals("")) && msg.getMedia() != null) {
								val media = msg.getMedia()
								if (media is TLMessageMediaDocument) {
									msg_text = media.getCaption()
								} else if (media is TLMessageMediaPhoto) {
									msg_text = media.getCaption()
								}
							}
							it[text] = msg_text
							it[time] = msg.getDate()
							
							val f = FileManagerFactory.getFileManager(msg, user_manager, client)
							if (f == null) {
								it[has_media] = null
								it[media_type] = null
								it[media_file] = null
								it[media_size] = null
							} else {
								it[has_media] = true
								it[media_type] = f.name
								it[media_file] = f.targetFilename
								it[media_size] = f.size
							}
							print(7)
							val stream = ByteArrayOutputStream()
							msg.serializeBody(stream)
							it[data] = SerialBlob(stream.toByteArray())
							it[api_layer] = my_api_layer
							print(8)*/
						}
						} catch(e: Exception) {
							println("e")
						}
					} else if (abs is TLMessageService) {
						Messages.insert {
							it[message_id] = abs.getId()
							//it[message_type] = "service_message"
							//it[api_layer] = my_api_layer
						}
					} else if (abs is TLMessageEmpty) {
						Messages.insert {
							it[message_id] = abs.getId()
							//it[message_type] = "empty_message"
							//it[api_layer] = my_api_layer
						}
					} else {
						throw RuntimeException("Unexpected Message type: " + abs.javaClass)
					}
					print(9)
				}
				print("a")
			} // transaction
			logger.debug("Messages saved.")
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Exception shown above happened.")
        }

    }

    @Synchronized
    fun saveChats(all: TLVector<TLAbsChat>) {
        try {
            val ps_insert_or_replace = conn!!.prepareStatement(
                    "INSERT OR REPLACE INTO chats " +
                            "(id, name, type) " +
                            "VALUES " +
                            "(?,  ?,    ?)")
            val ps_insert_or_ignore = conn!!.prepareStatement(
                    "INSERT OR IGNORE INTO chats " +
                            "(id, name, type) " +
                            "VALUES " +
                            "(?,  ?,    ?)")

            for (abs in all) {
                ps_insert_or_replace.setInt(1, abs.getId())
                ps_insert_or_ignore.setInt(1, abs.getId())
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
            conn!!.setAutoCommit(false)
            ps_insert_or_ignore.executeBatch()
            ps_insert_or_ignore.clearBatch()
            ps_insert_or_replace.executeBatch()
            ps_insert_or_replace.clearBatch()
            conn!!.commit()
            conn!!.setAutoCommit(true)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Exception shown above happened.")
        }

    }

    @Synchronized
    fun saveUsers(all: TLVector<TLAbsUser>) {
        try {
            val ps_insert_or_replace = conn!!.prepareStatement(
                    "INSERT OR REPLACE INTO users " +
                            "(id, first_name, last_name, username, type, phone) " +
                            "VALUES " +
                            "(?,  ?,          ?,         ?,        ?,    ?)")
            val ps_insert_or_ignore = conn!!.prepareStatement(
                    "INSERT OR IGNORE INTO users " +
                            "(id, first_name, last_name, username, type, phone) " +
                            "VALUES " +
                            "(?,  ?,          ?,         ?,        ?,    ?)")
            for (abs in all) {
                if (abs is TLUser) {
                    val user = abs
                    ps_insert_or_replace.setInt(1, user.getId())
                    ps_insert_or_replace.setString(2, user.getFirstName())
                    ps_insert_or_replace.setString(3, user.getLastName())
                    ps_insert_or_replace.setString(4, user.getUsername())
                    ps_insert_or_replace.setString(5, "user")
                    ps_insert_or_replace.setString(6, user.getPhone())
                    ps_insert_or_replace.addBatch()
                } else if (abs is TLUserEmpty) {
                    ps_insert_or_ignore.setInt(1, abs.getId())
                    ps_insert_or_ignore.setNull(2, Types.VARCHAR)
                    ps_insert_or_ignore.setNull(3, Types.VARCHAR)
                    ps_insert_or_ignore.setNull(4, Types.VARCHAR)
                    ps_insert_or_ignore.setString(5, "empty_user")
                    ps_insert_or_ignore.setNull(6, Types.VARCHAR)
                    ps_insert_or_ignore.addBatch()
                } else {
                    throw RuntimeException("Unexpected " + abs.javaClass)
                }
            }
            conn!!.setAutoCommit(false)
            ps_insert_or_ignore.executeBatch()
            ps_insert_or_ignore.clearBatch()
            ps_insert_or_replace.executeBatch()
            ps_insert_or_replace.clearBatch()
            conn!!.commit()
            conn!!.setAutoCommit(true)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Exception shown above happened.")
        }

    }

    fun getIdsFromQuery(query: String): LinkedList<Int> {
        try {
            val list = LinkedList<Int>()
            val rs = stmt!!.executeQuery(query)
            while (rs.next()) {
                list.add(rs.getInt(1))
            }
            rs.close()
            return list
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }

    }

    fun getMessageTypesWithCount(c: AbstractChat): HashMap<String, Int> {
        val map = HashMap<String, Int>()
        try {
            val rs = stmt!!.executeQuery("SELECT message_type, COUNT(message_id) FROM messages WHERE " + c.query + " GROUP BY message_type")
            while (rs.next()) {
                map.put("count.messages.type." + rs.getString(1), rs.getInt(2))
            }
            return map
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    fun getMessageMediaTypesWithCount(c: AbstractChat): HashMap<String, Int> {
        val map = HashMap<String, Int>()
        try {
            var count = 0
            val rs = stmt!!.executeQuery("SELECT media_type, COUNT(message_id) FROM messages WHERE " + c.query + " GROUP BY media_type")
            while (rs.next()) {
                var s = rs.getString(1)
                if (s == null) {
                    s = "null"
                } else {
                    count += rs.getInt(2)
                }
                map.put("count.messages.media_type.$s", rs.getInt(2))
            }
            map.put("count.messages.media_type.any", count)
            return map
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    fun getMessageAuthorsWithCount(c: AbstractChat): HashMap<String, Any> {
        val map = HashMap<String, Any>()
        val user_map = HashMap<User, Int>()
        var count_others = 0
        // Set a default value for 'me' to fix the charts for channels - cause I
        // possibly didn't send any messages there.
        map.put("authors.count.me", 0)
        try {
            val rs = stmt!!.executeQuery("SELECT users.id, users.first_name, users.last_name, users.username, COUNT(messages.id) " +
                    "FROM messages " +
                    "LEFT JOIN users ON users.id=messages.sender_id " +
                    "WHERE " + c.query + " GROUP BY sender_id")
            while (rs.next()) {
                val u: User
                if (rs.getString(2) != null || rs.getString(3) != null || rs.getString(4) != null) {
                    u = User(rs.getInt(1), rs.getString(2), rs.getString(3))
                } else {
                    u = User(rs.getInt(1), "Unknown", "")
                }
                if (u.isMe) {
                    map.put("authors.count.me", rs.getInt(5))
                } else {
                    user_map.put(u, rs.getInt(5))
                    count_others += rs.getInt(5)
                }
            }
            map.put("authors.count.others", count_others)
            map.put("authors.all", user_map)
            return map
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    fun getMessageTimesMatrix(c: AbstractChat): Array<IntArray> {
        val result = Array(7) { IntArray(24) }
        try {
            val rs = stmt!!.executeQuery("SELECT STRFTIME('%w', time, 'unixepoch') as DAY, " +
                    "STRFTIME('%H', time, 'unixepoch') AS hour, " +
                    "COUNT(id) FROM messages WHERE " + c.query + " GROUP BY hour, day " +
                    "ORDER BY hour, day")
            while (rs.next()) {
                result[if (rs.getInt(1) == 0) 6 else rs.getInt(1) - 1][rs.getInt(2)] = rs.getInt(3)
            }
            return result
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    fun getMessagesForExport(c: AbstractChat): LinkedList<HashMap<String, Any>> {
        try {

            val rs = stmt!!.executeQuery("SELECT messages.message_id as message_id, text, time*1000 as time, has_media, " +
                    "media_type, media_file, media_size, users.first_name as user_first_name, users.last_name as user_last_name, " +
                    "users.username as user_username, users.id as user_id, " +
                    "users_fwd.first_name as user_fwd_first_name, users_fwd.last_name as user_fwd_last_name, users_fwd.username as user_fwd_username " +
                    "FROM messages " +
                    "LEFT JOIN users ON users.id=messages.sender_id " +
                    "LEFT JOIN users AS users_fwd ON users_fwd.id=fwd_from_id WHERE " +
                    c.query + " " +
                    "ORDER BY messages.message_id")
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
                h.put("from_me", rs.getInt("user_id") == user_manager.user!!.getId())
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
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Exception above!")
        }

    }


    abstract inner class AbstractChat {
        abstract val query: String
    }

    inner class Dialog(var id: Int, var first_name: String?, var last_name: String?, var username: String?, var count: Int?) : AbstractChat() {

        override val query: String
            get() = "source_type='dialog' AND source_id=" + id
    }

    inner class Chat(var id: Int, var name: String?, var count: Int?) : AbstractChat() {

        override val query: String
            get() = "source_type IN('group', 'supergroup', 'channel') AND source_id=" + id
    }

    inner class User(id: Int, first_name: String?, last_name: String?) {
        var name: String
        var isMe: Boolean = false

        init {
            isMe = id == user_manager.user!!.getId()
            val s = StringBuilder()
            if (first_name != null) s.append(first_name + " ")
            if (last_name != null) s.append(last_name)
            name = s.toString().trim()
        }
    }

    inner class GlobalChat : AbstractChat() {
        override val query: String
            get() = "1=1"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Database::class.java)
        internal var instance: Database? = null

        fun init(c: TelegramClient) {
            instance = Database(c)
        }

        fun getInstance(): Database {
            if (instance == null) throw RuntimeException("Database is not initialized but getInstance() was called.")
            return instance!!
        }

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
