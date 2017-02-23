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

import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.core.TLVector;
import com.github.badoualy.telegram.api.TelegramClient;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.google.gson.Gson;
import com.github.badoualy.telegram.api.Kotlogram;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.sql.Time;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Date;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.text.SimpleDateFormat;

public class Database {
	private Connection conn;
	private Statement stmt;
	public UserManager user_manager;
	public TelegramClient client;
	private final static Logger logger = LoggerFactory.getLogger(Database.class);
	private static Database instance = null;
	
	private Database(TelegramClient client) {
		this.user_manager = UserManager.getInstance();
		this.client = client;
		System.out.println("Opening database...");
		try {
			Class.forName("org.sqlite.JDBC");
		} catch(ClassNotFoundException e) {
			CommandLineController.show_error("Could not load jdbc-sqlite class.");
		}
		
		String path = "jdbc:sqlite:" +
			user_manager.getFileBase() +
			Config.FILE_NAME_DB;
		
		try {
			conn = DriverManager.getConnection(path);
			stmt = conn.createStatement();
		} catch (SQLException e) {
			CommandLineController.show_error("Could not connect to SQLITE database.");
		}
		
		System.out.println("Database is ready.");
	}
	
	public static void init(TelegramClient c) {
		instance = new Database(c);
	}
	
	public static Database getInstance() {
		if (instance == null) throw new RuntimeException("Database is not initialized but getInstance() was called.");
		return instance;
	}
	
	public static TLMessage bytesToTLMessage(byte[] b) {
		try {
			if (b==null) return null;
			ByteArrayInputStream stream = new ByteArrayInputStream(b);
			TLMessage msg = new TLMessage();
			msg.deserializeBody(stream, TLApiContext.getInstance());
			return msg;
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Could not deserialize message.");
		}
	}
	
	public void jsonify() {
		try {
			ResultSet rs = stmt.executeQuery("SELECT max(version) FROM database_versions");
			rs.next();
			if (rs.getInt(1) != 8) {
				System.out.println("This tool will only run on a database version 8. Found: " + rs.getInt(1));
				System.exit(1);
			}
			rs.close();
			rs = stmt.executeQuery("SELECT id, data FROM messages WHERE api_layer=51");
			PreparedStatement ps = conn.prepareStatement("UPDATE messages SET json=? WHERE id=?");
			Gson gson = Utils.getGson();
			while(rs.next()) {
				TLMessage msg = bytesToTLMessage(rs.getBytes(2));
				ps.setInt(2, rs.getInt(1));
				ps.setString(1, gson.toJson(msg));
				ps.addBatch();
			}
			rs.close();
			conn.setAutoCommit(false);
			ps.executeBatch();
			conn.commit();
			conn.setAutoCommit(true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}																					  
	
		
	
	
	
	public abstract class AbstractChat {
		public abstract String getQuery();
	}
	
	public class Dialog extends AbstractChat{
		public int id;
		public String first_name;
		public String last_name;
		public String username;
		public int count;
		
		public Dialog (int id, String first_name, String last_name, String username, int count) {
			this.id = id;
			this.first_name = first_name;
			this.last_name = last_name;
			this.username = username;
			this.count = count;
		}
		
		public String getQuery() { return "dialog_id=" + id; }
	}
	
	public class Chat extends AbstractChat {
		public int id;
		public String name;
		public int count;
		
		public Chat(int id, String name, int count) {
			this.id = id;
			this.name = name;
			this.count = count;
		}
		
		public String getQuery() {return "chat_id=" + id; }
	}
	
	public class User {
		public String name;
		public boolean isMe;
		
		public User(int id, String first_name, String last_name, String username) {
			isMe = id==user_manager.getUser().getId();
			StringBuilder s = new StringBuilder();
			if (first_name!=null) s.append(first_name + " ");
			if (last_name!=null) s.append(last_name);
			name = s.toString().trim();
		}
	}
	
	public class GlobalChat extends AbstractChat {
		public String getQuery() { return "1=1"; }
	}
}
