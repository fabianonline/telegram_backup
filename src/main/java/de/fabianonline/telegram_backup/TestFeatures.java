package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.api.TelegramClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.io.IOException;
import java.nio.charset.Charset;

class TestFeatures {
	public static void test1() {
		// Tests entries in a cache4.db in the current working directory for compatibility
		try {
			Class.forName("org.sqlite.JDBC");
		} catch(ClassNotFoundException e) {
			CommandLineController.show_error("Could not load jdbc-sqlite class.");
		}

		String path = "jdbc:sqlite:cache4.db";

		Connection conn = null;
		Statement stmt = null;
		
		try {
			conn = DriverManager.getConnection(path);
			stmt = conn.createStatement();
		} catch (SQLException e) {
			CommandLineController.show_error("Could not connect to SQLITE database.");
		}
		
		int unsupported_constructor = 0;
		int success = 0;
		
		try {
			ResultSet rs = stmt.executeQuery("SELECT data FROM messages");
			while (rs.next()) {
				try {
					TLApiContext.getInstance().deserializeMessage(rs.getBytes(1));
				} catch (com.github.badoualy.telegram.tl.exception.UnsupportedConstructorException e) {
					unsupported_constructor++;
				} catch (IOException e) {
					System.out.println("IOException: " + e);
				}
				success++;
			}
		} catch (SQLException e) {
			System.out.println("SQL exception: " + e);
		}
		
		System.out.println("Success:                 " + success);
		System.out.println("Unsupported constructor: " + unsupported_constructor);
	}
	
	public static void test2(UserManager user, TelegramClient client) {
		// Prints system.encoding and default charset
		System.out.println("Default Charset:   " + Charset.defaultCharset());
		System.out.println("file.encoding:     " + System.getProperty("file.encoding"));
		Database db = new Database(user, client, false);
		System.out.println("Database encoding: " + db.getEncoding());
	}
}
