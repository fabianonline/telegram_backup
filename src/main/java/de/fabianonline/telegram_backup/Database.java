package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.tl.api.TLMessage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.io.File;

import de.fabianonline.telegram_backup.UserManager;


class Database {
	private Connection conn;
	private Statement stmt;
	
	public Database(UserManager user) {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch(ClassNotFoundException e) {
			CommandLineController.show_error("Could not load jdbc-sqlite class.");
		}
		
		String path = "jdbc:sqlite:" +
			Config.FILE_BASE +
			File.separatorChar +
			user.getUser().getPhone() +
			File.separatorChar +
			Config.FILE_NAME_DB;
		
		try {
			conn = DriverManager.getConnection(path);
			stmt = conn.createStatement();
		} catch (SQLException e) {
			CommandLineController.show_error("Could not connect to SQLITE database.");
		}
		
		this.init();
	}
	
	private void init() {
		try {
			int version;
			ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='database_versions'");
			rs.next();
			if (rs.getInt(1)==0) {
				version = 0;
			} else {
				rs.close();
				rs = stmt.executeQuery("SELECT MAX(version) FROM database_versions");
				rs.next();
				version = rs.getInt(1);
				rs.close();
			}
			System.out.println("Database version: " + version);
			
			if (version==0) {
				stmt.executeUpdate("CREATE TABLE messages ("
						+ "id INTEGER PRIMARY KEY ASC, "
						+ "dialog_id INTEGER, "
						+ "to_id INTEGER, "
						+ "from_id INTEGER, "
						+ "type TEXT, "
						+ "text TEXT, "
						+ "time TEXT, "
						+ "has_media BOOLEAN, "
						+ "data BLOB)");
				stmt.executeUpdate("CREATE TABLE dialogs ("
						+ "id INTEGER PRIMARY KEY ASC, "
						+ "name TEXT, "
						+ "type TEXT)");
				stmt.executeUpdate("CREATE TABLE people ("
						+ "id INTEGER PRIMARY KEY ASC, "
						+ "name TEXT, "
						+ "username TEXT, "
						+ "type TEXT)");
				stmt.executeUpdate("CREATE TABLE database_versions ("
						+ "version INTEGER)");
				stmt.executeUpdate("INSERT INTO database_versions (version) VALUES (1)");
				version = 1;
			}
		} catch (SQLException e) {
			System.out.println(e.getSQLState());
			e.printStackTrace();
		}
	}
	
	public void save(TLMessage msg) {
		/*BufferArrayOutputStream stream = new BufferArrayOutputStream();
		msg.serializeBody(stream);
		bytes[] = stream.toByteArray();
		PreparedStatement ps = conn.prepareStatement(query);
		ps.setBytes(x, bytes[]);*/
	}
}
