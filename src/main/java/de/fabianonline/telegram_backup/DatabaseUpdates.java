package de.fabianonline.telegram_backup;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import com.github.badoualy.telegram.tl.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory;
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager;

public class DatabaseUpdates {
	protected Connection conn;
	protected Database db;
	private static final Logger logger = LoggerFactory.getLogger(DatabaseUpdates.class);
	private static LinkedList<DatabaseUpdate> updates = new LinkedList<DatabaseUpdate>();
	
	public DatabaseUpdates(Connection conn, Database db) {
		this.conn = conn;
		this.db = db;
		logger.debug("Registering Database Updates...");
		register(new DB_Update_1(conn, db));
		register(new DB_Update_2(conn, db));
		register(new DB_Update_3(conn, db));
		register(new DB_Update_4(conn, db));
		register(new DB_Update_5(conn, db));
		register(new DB_Update_6(conn, db));
		register(new DB_Update_7(conn, db));
	}
	
	public void doUpdates() {
		try {
			Statement stmt = conn.createStatement();
			ResultSet rs;
			logger.debug("DatabaseUpdate.doUpdates running");

			logger.debug("Getting current database version");
			int version;
			logger.debug("Checking if table database_versions exists");
			rs = stmt.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='database_versions'");
			rs.next();
			if (rs.getInt(1)==0) {
				logger.debug("Table does not exist");
				version = 0;
			} else {
				logger.debug("Table exists. Checking max version");
				rs.close();
				rs = stmt.executeQuery("SELECT MAX(version) FROM database_versions");
				rs.next();
				version = rs.getInt(1);
				rs.close();
			}
			logger.debug("version: {}", version);
			System.out.println("Database version: " + version);
			logger.debug("Max available database version is {}", getMaxPossibleVersion());
			
			if (version < getMaxPossibleVersion()) {
				logger.debug("Update is necessary. {} => {}.", version, getMaxPossibleVersion());
				boolean backup = false;
				for (int i=version+1; i<=getMaxPossibleVersion(); i++) {
					if (getUpdateToVersion(i).needsBackup()) {
						logger.debug("Update to version {} needs a backup", i);
						backup=true;
					}
				}
				if (backup) {
					logger.debug("Performing backup");
					db.backupDatabase(version);
				}
				
				logger.debug("Applying updates");
				try {
					for (int i=version+1; i<=getMaxPossibleVersion(); i++) {
						getUpdateToVersion(i).doUpdate();
					}
				} catch (SQLException e) { throw new RuntimeException(e); }
			} else {
				logger.debug("No update necessary.");
			}
			
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private DatabaseUpdate getUpdateToVersion(int i) { return updates.get(i-1); }
	
	private int getMaxPossibleVersion() {
		return updates.size();
	}
	
	private void register(DatabaseUpdate d) {
		logger.debug("Registering {} as update to version {}", d.getClass().getName(), d.getVersion());
		if (d.getVersion() != updates.size()+1) {
			throw new RuntimeException("Tried to register DB update to version " + d.getVersion() + ", but would need update to version " + (updates.size()+1));
		}
		updates.add(d);
	}
}

abstract class DatabaseUpdate {
	protected Connection conn;
	protected Statement stmt;
	protected Database db;
	protected static final Logger logger = LoggerFactory.getLogger(DatabaseUpdate.class);
	public DatabaseUpdate(Connection conn, Database db) {
		this.conn = conn;
		try {
			stmt = conn.createStatement();
		} catch (SQLException e) { throw new RuntimeException(e); }
		this.db = db;
	
	}
	public void doUpdate() throws SQLException {
		logger.debug("Applying update to version {}", getVersion());
		System.out.println("  Updating to version " + getVersion() + "...");
		_doUpdate();
		logger.debug("Saving current database version to the db");
		stmt.executeUpdate("INSERT INTO database_versions (version) VALUES (" + getVersion() + ")");
	}
	
	protected abstract void _doUpdate() throws SQLException;
	public abstract int getVersion();
	public boolean needsBackup() { return false; }
}

class DB_Update_1 extends DatabaseUpdate {
	public int getVersion() { return 1; }
	public DB_Update_1(Connection conn, Database db) { super(conn, db); }
	
	protected void _doUpdate() throws SQLException {
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
			+ "type TEXT)");
		stmt.executeUpdate("CREATE TABLE dialogs ("
			+ "id INTEGER PRIMARY KEY ASC, "
			+ "name TEXT, "
			+ "type TEXT)");
		stmt.executeUpdate("CREATE TABLE people ("
			+ "id INTEGER PRIMARY KEY ASC, "
			+ "first_name TEXT, "
			+ "last_name TEXT, "
			+ "username TEXT, "
			+ "type TEXT)");
		stmt.executeUpdate("CREATE TABLE database_versions ("
			+ "version INTEGER)");		
	}
}

class DB_Update_2 extends DatabaseUpdate {
	public int getVersion() { return 2; }
	public DB_Update_2(Connection conn, Database db) { super(conn, db); }
	
	protected void _doUpdate() throws SQLException {
		stmt.executeUpdate("ALTER TABLE people RENAME TO 'users'");
		stmt.executeUpdate("ALTER TABLE users ADD COLUMN phone TEXT");
	}
}

class DB_Update_3 extends DatabaseUpdate {
	public int getVersion() { return 3; }
	public DB_Update_3(Connection conn, Database db) { super(conn, db); }
	
	protected void _doUpdate() throws SQLException {
		stmt.executeUpdate("ALTER TABLE dialogs RENAME TO 'chats'");
	}
}

class DB_Update_4 extends DatabaseUpdate {
	public int getVersion() { return 4; }
	public DB_Update_4(Connection conn, Database db) { super(conn, db); }
	
	protected void _doUpdate() throws SQLException {
		stmt.executeUpdate("CREATE TABLE messages_new (id INTEGER PRIMARY KEY ASC, dialog_id INTEGER, to_id INTEGER, from_id INTEGER, from_type TEXT, text TEXT, time INTEGER, has_media BOOLEAN, sticker TEXT, data BLOB, type TEXT);");
		stmt.executeUpdate("INSERT INTO messages_new SELECT * FROM messages");
		stmt.executeUpdate("DROP TABLE messages");
		stmt.executeUpdate("ALTER TABLE messages_new RENAME TO 'messages'");
	}
}

class DB_Update_5 extends DatabaseUpdate {
	public int getVersion() { return 5; }
	public DB_Update_5(Connection conn, Database db) { super(conn, db); }

	protected void _doUpdate() throws SQLException {
		stmt.executeUpdate("CREATE TABLE runs (id INTEGER PRIMARY KEY ASC, time INTEGER, start_id INTEGER, end_id INTEGER, count_missing INTEGER)");
	}
}

class DB_Update_6 extends DatabaseUpdate {
	public int getVersion() { return 6; }
	public DB_Update_6(Connection conn, Database db) { super(conn, db); }
	public boolean needsBackup() { return true; }
	
	protected void _doUpdate() throws SQLException {
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
			"    data BLOB)");
		LinkedHashMap<String, String> mappings = new LinkedHashMap<String, String>();
		mappings.put("id", "id");
		mappings.put("message_type", "type");
		mappings.put("dialog_id", "CASE from_type WHEN 'user' THEN dialog_id ELSE NULL END");
		mappings.put("chat_id",   "CASE from_type WHEN 'chat' THEN dialog_id ELSE NULL END");
		mappings.put("sender_id", "from_id");
		mappings.put("text", "text");
		mappings.put("time", "time");
		mappings.put("has_media", "has_media");
		mappings.put("data", "data");
		StringBuilder query = new StringBuilder("INSERT INTO messages_new\n(");
		boolean first;
		first = true;
		for(String s : mappings.keySet()) {
			if (!first) query.append(", ");
			query.append(s);
			first = false;
		}
		query.append(")\nSELECT \n");
		first = true;
		for (String s : mappings.values()) {
			if (!first) query.append(", ");
			query.append(s);
			first = false;
		}
		query.append("\nFROM messages");
		stmt.executeUpdate(query.toString());
		
		System.out.println("    Updating the data (this might take some time)...");
		ResultSet rs = stmt.executeQuery("SELECT id, data FROM messages_new");
		PreparedStatement ps = conn.prepareStatement("UPDATE messages_new SET fwd_from_id=?, media_type=?, media_file=?, media_size=? WHERE id=?");
		while (rs.next()) {
			ps.setInt(5, rs.getInt(1));
			TLMessage msg = db.bytesToTLMessage(rs.getBytes(2));
			if (msg==null || msg.getFwdFrom()==null) {
				ps.setNull(1, Types.INTEGER);
			} else {
				ps.setInt(1, msg.getFwdFrom().getFromId());
			}
			AbstractMediaFileManager f = FileManagerFactory.getFileManager(msg, db.user_manager, db.client);
			if (f==null) {
				ps.setNull(2, Types.VARCHAR);
				ps.setNull(3, Types.VARCHAR);
				ps.setNull(4, Types.INTEGER);
			} else {
				ps.setString(2, f.getName());
				ps.setString(3, f.getTargetFilename());
				ps.setInt(4, f.getSize());
			}
			ps.addBatch();
		}
		rs.close();
		conn.setAutoCommit(false);
		ps.executeBatch();
		conn.commit();
		conn.setAutoCommit(true);
		stmt.executeUpdate("DROP TABLE messages");
		stmt.executeUpdate("ALTER TABLE messages_new RENAME TO messages");
	}
}

class DB_Update_7 extends DatabaseUpdate {
	public int getVersion() { return 7; }
	public boolean needsBackup() { return true; }
	public DB_Update_7(Connection conn, Database db) { super(conn, db); }
	
	protected void _doUpdate() throws SQLException {
		stmt.executeUpdate("ALTER TABLE messages ADD COLUMN api_layer INTEGER");
		
		stmt.executeUpdate("UPDATE messages SET api_layer=51");
	}
}
