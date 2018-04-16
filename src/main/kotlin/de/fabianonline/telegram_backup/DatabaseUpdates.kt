package de.fabianonline.telegram_backup

import java.util.HashMap
import java.util.LinkedHashMap
import java.util.LinkedList
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.sql.ResultSet
import java.sql.PreparedStatement
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.core.TLVector
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager
import com.github.salomonbrys.kotson.*
import com.google.gson.*

class DatabaseUpdates(protected var conn: Connection, protected var db: Database) {

	private val maxPossibleVersion: Int
		get() = updates.size

	init {
		logger.debug("Registering Database Updates...")
		register(DB_Update_1(conn, db))
		register(DB_Update_2(conn, db))
		register(DB_Update_3(conn, db))
		register(DB_Update_4(conn, db))
		register(DB_Update_5(conn, db))
		register(DB_Update_6(conn, db))
		register(DB_Update_7(conn, db))
		register(DB_Update_8(conn, db))
		register(DB_Update_9(conn, db))
		register(DB_Update_10(conn, db))
		register(DB_Update_11(conn, db))
	}

	fun doUpdates() {
		try {
			val stmt = conn.createStatement()
			logger.debug("DatabaseUpdate.doUpdates running")

			logger.debug("Getting current database version")
			var version: Int
			logger.debug("Checking if table database_versions exists")
			val table_count = db.queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='database_versions'")
			if (table_count == 0) {
				logger.debug("Table does not exist")
				version = 0
			} else {
				logger.debug("Table exists. Checking max version")
				version = db.queryInt("SELECT MAX(version) FROM database_versions")
			}
			logger.debug("version: {}", version)
			System.out.println("Database version: " + version)
			logger.debug("Max available database version is {}", maxPossibleVersion)

			if (version == 0) {
				logger.debug("Looking for DatabaseUpdate with create_query...")
				// This is a fresh database - so we search for the latest available version with a create_query
				// and use this as a shortcut.
				var update: DatabaseUpdate? = null
				for (i in maxPossibleVersion downTo 1) {
					update = getUpdateToVersion(i)
					logger.trace("Looking at DatabaseUpdate version {}", update.version)
					if (update.create_query != null) break
					update = null
				}

				if (update != null) {
					logger.debug("Found DatabaseUpdate version {} with create_query.", update.version)
					for (query in update.create_query!!) stmt.execute(query)
					stmt.execute("INSERT INTO database_versions (version) VALUES (${update.version})")
					version = update.version
				}
			}

			if (version < maxPossibleVersion) {
				logger.debug("Update is necessary. {} => {}.", version, maxPossibleVersion)
				var backup = false
				for (i in version + 1..maxPossibleVersion) {
					if (getUpdateToVersion(i).needsBackup) {
						logger.debug("Update to version {} needs a backup", i)
						backup = true
					}
				}
				if (backup) {
					if (version > 0) {
						logger.debug("Performing backup")
						db.backupDatabase(version)
					} else {
						logger.debug("NOT performing a backup, because we are creating a fresh database and don't need a backup of that.")
					}
				}

				logger.debug("Applying updates")
				try {
					for (i in version + 1..maxPossibleVersion) {
						getUpdateToVersion(i).doUpdate()
					}
				} catch (e: SQLException) {
					throw RuntimeException(e)
				}
				
				println("Cleaning up the database (this might take some time)...")
				try { stmt.executeUpdate("VACUUM") } catch (t: Throwable) { logger.debug("Exception during VACUUMing: {}", t) }

			} else {
				logger.debug("No update necessary.")
			}

		} catch (e: SQLException) {
			throw RuntimeException(e)
		}

	}

	private fun getUpdateToVersion(i: Int): DatabaseUpdate {
		return updates.get(i - 1)
	}

	private fun register(d: DatabaseUpdate) {
		logger.debug("Registering {} as update to version {}", d.javaClass, d.version)
		if (d.version != updates.size + 1) {
			throw RuntimeException("Tried to register DB update to version ${d.version}, but would need update to version ${updates.size + 1}")
		}
		updates.add(d)
	}

	companion object {
		private val logger = LoggerFactory.getLogger(DatabaseUpdates::class.java)
		private val updates = LinkedList<DatabaseUpdate>()
	}
}

internal abstract class DatabaseUpdate(protected var conn: Connection, protected var db: Database) {
	protected var stmt: Statement
	abstract val version: Int

	init {
		try {
			stmt = conn.createStatement()
		} catch (e: SQLException) {
			throw RuntimeException(e)
		}

	}

	@Throws(SQLException::class)
	fun doUpdate() {
		logger.debug("Applying update to version {}", version)
		System.out.println("  Updating to version $version...")
		_doUpdate()
		logger.debug("Saving current database version to the db")
		stmt.executeUpdate("INSERT INTO database_versions (version) VALUES ($version)")
	}

	@Throws(SQLException::class)
	protected abstract fun _doUpdate()

	open val needsBackup = false

	@Throws(SQLException::class)
	protected fun execute(sql: String) {
		logger.debug("Executing: {}", sql)
		stmt.executeUpdate(sql)
	}

	companion object {
		protected val logger = LoggerFactory.getLogger(DatabaseUpdate::class.java)
	}

	open val create_query: List<String>? = null
}

internal class DB_Update_1(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 1

	@Throws(SQLException::class)
	override fun _doUpdate() {
		stmt.executeUpdate("CREATE TABLE messages ("
			+ "id INTEGER PRIMARY KEY ASC, "
			+ "dialog_id INTEGER, "
			+ "to_id INTEGER, "
			+ "from_id INTEGER, "
			+ "from_type TEXT, "
			+ "text TEXT, "
			+ "time TEXT, "
			+ "has_media BOOLEAN, "
			+ "sticker TEXT, "
			+ "data BLOB,"
			+ "type TEXT)")
		stmt.executeUpdate("CREATE TABLE dialogs ("
			+ "id INTEGER PRIMARY KEY ASC, "
			+ "name TEXT, "
			+ "type TEXT)")
		stmt.executeUpdate("CREATE TABLE people ("
			+ "id INTEGER PRIMARY KEY ASC, "
			+ "first_name TEXT, "
			+ "last_name TEXT, "
			+ "username TEXT, "
			+ "type TEXT)")
		stmt.executeUpdate("CREATE TABLE database_versions (" + "version INTEGER)")
	}
}

internal class DB_Update_2(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 2

	@Throws(SQLException::class)
	override fun _doUpdate() {
		stmt.executeUpdate("ALTER TABLE people RENAME TO 'users'")
		stmt.executeUpdate("ALTER TABLE users ADD COLUMN phone TEXT")
	}
}

internal class DB_Update_3(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 3

	@Throws(SQLException::class)
	override fun _doUpdate() {
		stmt.executeUpdate("ALTER TABLE dialogs RENAME TO 'chats'")
	}
}

internal class DB_Update_4(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 4

	@Throws(SQLException::class)
	override fun _doUpdate() {
		stmt.executeUpdate("CREATE TABLE messages_new (id INTEGER PRIMARY KEY ASC, dialog_id INTEGER, to_id INTEGER, from_id INTEGER, from_type TEXT, text TEXT, time INTEGER, has_media BOOLEAN, sticker TEXT, data BLOB, type TEXT);")
		stmt.executeUpdate("INSERT INTO messages_new SELECT * FROM messages")
		stmt.executeUpdate("DROP TABLE messages")
		stmt.executeUpdate("ALTER TABLE messages_new RENAME TO 'messages'")
	}
}

internal class DB_Update_5(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 5

	@Throws(SQLException::class)
	override fun _doUpdate() {
		stmt.executeUpdate("CREATE TABLE runs (id INTEGER PRIMARY KEY ASC, time INTEGER, start_id INTEGER, end_id INTEGER, count_missing INTEGER)")
	}
}

internal class DB_Update_6(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 6

	override val needsBackup = true

	@Throws(SQLException::class)
	override fun _doUpdate() {
		stmt.executeUpdate(
			"CREATE TABLE messages_new (\n" +
				"    id INTEGER PRIMARY KEY ASC,\n" +
				"    message_type TEXT,\n" +
				"    dialog_id INTEGER,\n" +
				"    chat_id INTEGER,\n" +
				"    sender_id INTEGER,\n" +
				"    fwd_from_id INTEGER,\n" +
				"    text TEXT,\n" +
				"    time INTEGER,\n" +
				"    has_media BOOLEAN,\n" +
				"    media_type TEXT,\n" +
				"    media_file TEXT,\n" +
				"    media_size INTEGER,\n" +
				"    media_json TEXT,\n" +
				"    markup_json TEXT,\n" +
				"    data BLOB)")
		val mappings = LinkedHashMap<String, String>()
		mappings.put("id", "id")
		mappings.put("message_type", "type")
		mappings.put("dialog_id", "CASE from_type WHEN 'user' THEN dialog_id ELSE NULL END")
		mappings.put("chat_id", "CASE from_type WHEN 'chat' THEN dialog_id ELSE NULL END")
		mappings.put("sender_id", "from_id")
		mappings.put("text", "text")
		mappings.put("time", "time")
		mappings.put("has_media", "has_media")
		mappings.put("data", "data")
		val query = StringBuilder("INSERT INTO messages_new\n(")
		var first: Boolean
		first = true
		for (s in mappings.keys) {
			if (!first) query.append(", ")
			query.append(s)
			first = false
		}
		query.append(")\nSELECT \n")
		first = true
		for (s in mappings.values) {
			if (!first) query.append(", ")
			query.append(s)
			first = false
		}
		query.append("\nFROM messages")
		stmt.executeUpdate(query.toString())

		System.out.println("    Updating the data (this might take some time)...")
		val rs = stmt.executeQuery("SELECT id, data FROM messages_new")
		val ps = conn.prepareStatement("UPDATE messages_new SET fwd_from_id=?, media_type=?, media_file=?, media_size=? WHERE id=?")
		while (rs.next()) {
			ps.setInt(5, rs.getInt(1))
			val msg = Database.bytesToTLMessage(rs.getBytes(2))
			if (msg == null || msg.getFwdFrom() == null) {
				ps.setNull(1, Types.INTEGER)
			} else {
				ps.setInt(1, msg.getFwdFrom().getFromId())
			}
			val f = FileManagerFactory.getFileManager(msg, db.user_manager, db.file_base, settings = null)
			if (f == null) {
				ps.setNull(2, Types.VARCHAR)
				ps.setNull(3, Types.VARCHAR)
				ps.setNull(4, Types.INTEGER)
			} else {
				ps.setString(2, f.name)
				ps.setString(3, f.targetFilename)
				ps.setInt(4, f.size)
			}
			ps.addBatch()
		}
		rs.close()
		conn.setAutoCommit(false)
		ps.executeBatch()
		ps.close()
		conn.commit()
		conn.setAutoCommit(true)
		stmt.executeUpdate("DROP TABLE messages")
		stmt.executeUpdate("ALTER TABLE messages_new RENAME TO messages")
	}
}

internal class DB_Update_7(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 7

	override val needsBackup = true

	@Throws(SQLException::class)
	override fun _doUpdate() {
		stmt.executeUpdate("ALTER TABLE messages ADD COLUMN api_layer INTEGER")

		stmt.executeUpdate("UPDATE messages SET api_layer=51")
	}
}

internal class DB_Update_8(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 8

	override val needsBackup = true

	@Throws(SQLException::class)
	override fun _doUpdate() {
		execute("ALTER TABLE messages ADD COLUMN source_type TEXT")
		execute("ALTER TABLE messages ADD COLUMN source_id INTEGER")
		execute("update messages set source_type='dialog', source_id=dialog_id where dialog_id is not null")
		execute("update messages set source_type='group', source_id=chat_id where chat_id is not null")

		execute("CREATE TABLE messages_new (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT," +
			"message_id INTEGER," +
			"message_type TEXT," +
			"source_type TEXT," +
			"source_id INTEGER," +
			"sender_id INTEGER," +
			"fwd_from_id INTEGER," +
			"text TEXT," +
			"time INTEGER," +
			"has_media BOOLEAN," +
			"media_type TEXT," +
			"media_file TEXT," +
			"media_size INTEGER," +
			"media_json TEXT," +
			"markup_json TEXT," +
			"data BLOB," +
			"api_layer INTEGER)")
		execute("INSERT INTO messages_new" +
			"(message_id, message_type, source_type, source_id, sender_id, fwd_from_id, text, time, has_media, media_type," +
			"media_file, media_size, media_json, markup_json, data, api_layer)" +
			"SELECT " +
			"id, message_type, source_type, source_id, sender_id, fwd_from_id, text, time, has_media, media_type," +
			"media_file, media_size, media_json, markup_json, data, api_layer FROM messages")
		execute("DROP TABLE messages")
		execute("ALTER TABLE messages_new RENAME TO 'messages'")
		execute("CREATE UNIQUE INDEX unique_messages ON messages (source_type, source_id, message_id)")
	}
}

internal class DB_Update_9(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 9
	override val needsBackup = true

	override val create_query = listOf(
		"CREATE TABLE \"chats\" (id INTEGER PRIMARY KEY ASC, name TEXT, type TEXT);",
		"CREATE TABLE \"users\" (id INTEGER PRIMARY KEY ASC, first_name TEXT, last_name TEXT, username TEXT, type TEXT, phone TEXT);",
		"CREATE TABLE database_versions (version INTEGER);",
		"CREATE TABLE runs (id INTEGER PRIMARY KEY ASC, time INTEGER, start_id INTEGER, end_id INTEGER, count_missing INTEGER);",
		"CREATE TABLE \"messages\" (id INTEGER PRIMARY KEY AUTOINCREMENT,message_id INTEGER,message_type TEXT,source_type TEXT,source_id INTEGER,sender_id INTEGER,fwd_from_id INTEGER,text TEXT,time INTEGER,has_media BOOLEAN,media_type TEXT,media_file TEXT,media_size INTEGER,media_json TEXT,markup_json TEXT,data BLOB,api_layer INTEGER);",
		"CREATE UNIQUE INDEX unique_messages ON messages (source_type, source_id, message_id);"
	)
	
	@Throws(SQLException::class)
	override fun _doUpdate() {
		val logger = LoggerFactory.getLogger(DB_Update_9::class.java)
		println("    Updating supergroup channel message data (this might take some time)...")
		print("    ")
		val count = db.queryInt("SELECT COUNT(*) FROM messages WHERE source_type='channel' and sender_id IS NULL and api_layer=53")
		logger.debug("Found $count candidates for conversion")
		val limit = 5000
		var offset = 0
		var i = 0
		while (offset < count) {
			logger.debug("Querying with limit $limit and offset $offset")
			val rs = stmt.executeQuery("SELECT id, data, source_id FROM messages WHERE source_type='channel' and sender_id IS NULL and api_layer=53 ORDER BY id LIMIT ${limit} OFFSET ${offset}")
			val messages = TLVector<TLAbsMessage>()
			val messages_to_delete = mutableListOf<Int>()
			while (rs.next()) {
				val msg = Database.bytesToTLMessage(rs.getBytes(2))
				if (msg!!.getFromId() != null) {
					i++
					messages.add(msg)
					messages_to_delete.add(rs.getInt(1))
				}
			}
			rs.close()
			db.saveMessages(messages, api_layer=53, source_type=MessageSource.SUPERGROUP, settings=null)
			execute("DELETE FROM messages WHERE id IN (" + messages_to_delete.joinToString() + ")")
			print(".")
			
			offset += limit
		}
		println()
		logger.info("Converted ${i} of ${count} messages.")
	}
}

internal class DB_Update_10(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version: Int
		get() = 10
	
	@Throws(SQLException::class)
	override fun _doUpdate() {
		execute("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT)")
	}
}

internal class DB_Update_11(conn: Connection, db: Database) : DatabaseUpdate(conn, db) {
	override val version = 11
	val logger = LoggerFactory.getLogger(DB_Update_11::class.java)
	
	override fun _doUpdate() {
		execute("ALTER TABLE messages ADD COLUMN json TEXT NULL")
		execute("ALTER TABLE chats ADD COLUMN json TEXT NULL")
		execute("ALTER TABLE chats ADD COLUMN api_layer INTEGER NULL")
		execute("ALTER TABLE users ADD COLUMN json TEXT NULL")
		execute("ALTER TABLE users ADD COLUMN api_layer INTEGER NULL")
		val limit = 5000
		var offset = 0
		var i: Int
		val ps = conn.prepareStatement("UPDATE messages SET json=? WHERE id=?")
		println("    Updating messages to add their JSON representation to the database. This might take a few moments...")
		print("    ")
		do {
			i = 0
			logger.debug("Querying with limit $limit, offset is now $offset")
			val rs = db.executeQuery("SELECT id, data FROM messages WHERE json IS NULL AND api_layer=53 LIMIT $limit")
			while (rs.next()) {
				i++
				val id = rs.getInt(1)
				val msg = Database.bytesToTLMessage(rs.getBytes(2))
				val json = if (msg==null) Gson().toJson(null) else msg.toJson()
				ps.setString(1, json)
				ps.setInt(2, id)
				ps.addBatch()
			}
			rs.close()
			conn.setAutoCommit(false)
			ps.executeBatch()
			ps.clearBatch()
			conn.commit()
			conn.setAutoCommit(true)
			offset += limit
			print(".")
		} while (i >= limit)
		println()
		ps.close()
	}
}
