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
import java.text.SimpleDateFormat;

import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager;
import de.fabianonline.telegram_backup.mediafilemanager.FileManagerFactory;

public class Database {
	private Connection conn;
	private Statement stmt;
	public UserManager user_manager;
	public TelegramClient client;
	
	public Database(UserManager user_manager, TelegramClient client) {
		this(user_manager, client, true);
	}
	
	public Database(UserManager user_manager, TelegramClient client, boolean update_db) {
		this.user_manager = user_manager;
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
		
		this.init(update_db);
		System.out.println("Database is ready.");
	}
	
	private void init(boolean update_db) {
		if (!update_db) return;
		DatabaseUpdates updates = new DatabaseUpdates(conn, this);
		updates.doUpdates();
	}
	
	public void backupDatabase(int currentVersion) {
		String filename = String.format(Config.FILE_NAME_DB_BACKUP, currentVersion);
		System.out.println("  Creating a backup of your database as " + filename);
		try {
			String src = user_manager.getFileBase() + Config.FILE_NAME_DB;
			String dst = user_manager.getFileBase() + filename;
			Log.debug("Copying %s to %s", src, dst);
			Files.copy(
				new File(src).toPath(),
				new File(dst).toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Could not create backup.");
		}
	}
	
	public int getTopMessageID() {
		try {
			ResultSet rs = stmt.executeQuery("SELECT MAX(id) FROM messages");
			rs.next();
			return rs.getInt(1);
		} catch (SQLException e) {
			return 0;
		}
	}
	
	public void logRun(int start_id, int end_id, int count) {
		try {
			PreparedStatement ps = conn.prepareStatement("INSERT INTO runs "+
				"(time,        start_id, end_id, count_missing) "+
				"VALUES "+
				"(DateTime('now'),    ?,        ?,      ?            )");
			ps.setInt(1, start_id);
			ps.setInt(2, end_id);
			ps.setInt(3, count);
			ps.execute();
		} catch (SQLException e) {}
	}
	
	public int getMessageCount() {
		try {
			ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages");
			rs.next();
			return rs.getInt(1);
		} catch (SQLException e) {
			throw new RuntimeException("Could not get count of messages.");
		}
	}
	
	public LinkedList<Integer> getMissingIDs() {
		try {
			LinkedList<Integer> missing = new LinkedList<Integer>();
			int max = getTopMessageID();
			ResultSet rs = stmt.executeQuery("SELECT id FROM messages ORDER BY id");
			rs.next();
			int id=rs.getInt(1);
			for (int i=1; i<=max; i++) {
				if (i==id) {
					rs.next();
					if (rs.isClosed()) {
						id = Integer.MAX_VALUE;
					} else {
						id=rs.getInt(1);
					}
				} else if (i<id) {
					missing.add(i);
				}
			}
			return missing;
		} catch(SQLException e) {
			e.printStackTrace();
			throw new RuntimeException("Could not get list of ids.");
		}
	}
	
	public synchronized void saveMessages(TLVector<TLAbsMessage> all, Integer api_layer) {
		try {
				//"(id, dialog_id, from_id, from_type, text, time, has_media, data, sticker, type) " +
				//"VALUES " +
				//"(?,  ?,         ?,       ?,         ?,    ?,    ?,         ?,    ?,       ?)");
			String columns =
				"(id, message_type, dialog_id, chat_id, sender_id, fwd_from_id, text, time, has_media, media_type, media_file, media_size, data, api_layer) "+
				"VALUES " +
				"(?,  ?,            ?,         ?,       ?,         ?,           ?,    ?,    ?,         ?,          ?,          ?,          ?,    ?)";
				//1   2             3          4        5          6            7     8     9          10          11          12          13    14
			PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO messages " + columns);
			PreparedStatement ps_insert_or_ignore = conn.prepareStatement("INSERT OR IGNORE INTO messages " + columns);

			for (TLAbsMessage abs : all) {
				if (abs instanceof TLMessage) {
					TLMessage msg = (TLMessage) abs;
					ps.setInt(1, msg.getId());
					ps.setString(2, "message");
					TLAbsPeer peer = msg.getToId();
					if (peer instanceof TLPeerChat) {
						ps.setNull(3, Types.INTEGER);
						ps.setInt(4, ((TLPeerChat)peer).getChatId());
					} else if (peer instanceof TLPeerUser) {
						int id = ((TLPeerUser)peer).getUserId();
						if (id==this.user_manager.getUser().getId()) {
							id = msg.getFromId();
						}
						ps.setInt(3, id);
						ps.setNull(4, Types.INTEGER);
					} else {
						throw new RuntimeException("Unexpected Peer type: " + peer.getClass().getName());
					}
					ps.setInt(5, msg.getFromId());
					
					if (msg.getFwdFrom() != null) {
						ps.setInt(6, msg.getFwdFrom().getFromId());
					} else {
						ps.setNull(6, Types.INTEGER);
					}

					String text = msg.getMessage();
					if ((text==null || text.equals("")) && msg.getMedia()!=null) {
						if (msg.getMedia() instanceof TLMessageMediaDocument) {
							text = ((TLMessageMediaDocument)msg.getMedia()).getCaption();
						} else if (msg.getMedia() instanceof TLMessageMediaPhoto) {
							text = ((TLMessageMediaPhoto)msg.getMedia()).getCaption();
						}
					}
					ps.setString(7, text);
					ps.setString(8, ""+msg.getDate());
					AbstractMediaFileManager f = FileManagerFactory.getFileManager(msg, user_manager, client);
					if (f==null) {
						ps.setNull(9, Types.BOOLEAN);
						ps.setNull(10, Types.VARCHAR);
						ps.setNull(11, Types.VARCHAR);
						ps.setNull(12, Types.INTEGER);
					} else {
						ps.setBoolean(9, true);
						ps.setString(10, f.getName());
						ps.setString(11, f.getTargetFilename());
						ps.setInt(12, f.getSize());
					}
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					msg.serializeBody(stream);
					ps.setBytes(13, stream.toByteArray());
					ps.setInt(14, api_layer);
					ps.addBatch();
				} else if (abs instanceof TLMessageService) {
					ps_insert_or_ignore.setInt(1, abs.getId());
					ps_insert_or_ignore.setString(2, "service_message");
					ps_insert_or_ignore.setNull(3, Types.INTEGER);
					ps_insert_or_ignore.setNull(4, Types.INTEGER);
					ps_insert_or_ignore.setNull(5, Types.INTEGER);
					ps_insert_or_ignore.setNull(6, Types.INTEGER);
					ps_insert_or_ignore.setNull(7, Types.VARCHAR);
					ps_insert_or_ignore.setNull(8, Types.INTEGER);
					ps_insert_or_ignore.setNull(9, Types.BOOLEAN);
					ps_insert_or_ignore.setNull(10, Types.VARCHAR);
					ps_insert_or_ignore.setNull(11, Types.VARCHAR);
					ps_insert_or_ignore.setNull(12, Types.INTEGER);
					ps_insert_or_ignore.setNull(13, Types.BLOB);
					ps_insert_or_ignore.setInt(14, api_layer);
					ps_insert_or_ignore.addBatch();
				} else if (abs instanceof TLMessageEmpty) {
					ps_insert_or_ignore.setInt(1, abs.getId());
					ps_insert_or_ignore.setString(2, "empty_message");
					ps_insert_or_ignore.setNull(3, Types.INTEGER);
					ps_insert_or_ignore.setNull(4, Types.INTEGER);
					ps_insert_or_ignore.setNull(5, Types.INTEGER);
					ps_insert_or_ignore.setNull(6, Types.INTEGER);
					ps_insert_or_ignore.setNull(7, Types.VARCHAR);
					ps_insert_or_ignore.setNull(8, Types.INTEGER);
					ps_insert_or_ignore.setNull(9, Types.BOOLEAN);
					ps_insert_or_ignore.setNull(10, Types.VARCHAR);
					ps_insert_or_ignore.setNull(11, Types.VARCHAR);
					ps_insert_or_ignore.setNull(12, Types.INTEGER);
					ps_insert_or_ignore.setNull(13, Types.BLOB);
					ps_insert_or_ignore.setInt(14, api_layer);
					ps_insert_or_ignore.addBatch();
				} else {
					throw new RuntimeException("Unexpected Message type: " + abs.getClass().getName());
				}
			}
			conn.setAutoCommit(false);
			ps.executeBatch();
			ps.clearBatch();
			ps_insert_or_ignore.executeBatch();
			ps_insert_or_ignore.clearBatch();
			conn.commit();
			conn.setAutoCommit(true);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception shown above happened.");
		}
	}

	public synchronized void saveChats(TLVector<TLAbsChat> all) {
		try {
			PreparedStatement ps_insert_or_replace = conn.prepareStatement(
				"INSERT OR REPLACE INTO chats " +
					"(id, name, type) "+
					"VALUES " +
					"(?,  ?,    ?)");
			PreparedStatement ps_insert_or_ignore = conn.prepareStatement(
				"INSERT OR IGNORE INTO chats " +
					"(id, name, type) "+
					"VALUES " +
					"(?,  ?,    ?)");

			for(TLAbsChat abs : all) {
				ps_insert_or_replace.setInt(1, abs.getId());
				ps_insert_or_ignore.setInt(1, abs.getId());
				if (abs instanceof TLChatEmpty) {
					ps_insert_or_ignore.setNull(2, Types.VARCHAR);
					ps_insert_or_ignore.setString(3, "empty_chat");
					ps_insert_or_ignore.addBatch();
				} else if (abs instanceof TLChatForbidden) {
					ps_insert_or_replace.setString(2, ((TLChatForbidden)abs).getTitle());
					ps_insert_or_replace.setString(3, "chat");
					ps_insert_or_replace.addBatch();
				} else if (abs instanceof TLChannelForbidden) {
					ps_insert_or_replace.setString(2, ((TLChannelForbidden)abs).getTitle());
					ps_insert_or_replace.setString(3, "channel");
					ps_insert_or_replace.addBatch();
				} else if (abs instanceof TLChat) {
					ps_insert_or_replace.setString(2, ((TLChat) abs).getTitle());
					ps_insert_or_replace.setString(3, "chat");
					ps_insert_or_replace.addBatch();
				} else if (abs instanceof TLChannel) {
					ps_insert_or_replace.setString(2, ((TLChannel)abs).getTitle());
					ps_insert_or_replace.setString(3, "channel");
					ps_insert_or_replace.addBatch();
				} else {
					throw new RuntimeException("Unexpected " + abs.getClass().getName());
				}
			}
			conn.setAutoCommit(false);
			ps_insert_or_ignore.executeBatch();
			ps_insert_or_ignore.clearBatch();
			ps_insert_or_replace.executeBatch();
			ps_insert_or_replace.clearBatch();
			conn.commit();
			conn.setAutoCommit(true);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception shown above happened.");
		}
	}

	public synchronized void saveUsers(TLVector<TLAbsUser> all) {
		try {
			PreparedStatement ps_insert_or_replace = conn.prepareStatement(
				"INSERT OR REPLACE INTO users " +
					"(id, first_name, last_name, username, type, phone) " +
					"VALUES " +
					"(?,  ?,          ?,         ?,        ?,    ?)");
			PreparedStatement ps_insert_or_ignore = conn.prepareStatement(
				"INSERT OR IGNORE INTO users " +
					"(id, first_name, last_name, username, type, phone) " +
					"VALUES " +
					"(?,  ?,          ?,         ?,        ?,    ?)");
			for (TLAbsUser abs : all) {
				if (abs instanceof TLUser) {
					TLUser user = (TLUser)abs;
					ps_insert_or_replace.setInt(1, user.getId());
					ps_insert_or_replace.setString(2, user.getFirstName());
					ps_insert_or_replace.setString(3, user.getLastName());
					ps_insert_or_replace.setString(4, user.getUsername());
					ps_insert_or_replace.setString(5, "user");
					ps_insert_or_replace.setString(6, user.getPhone());
					ps_insert_or_replace.addBatch();
				} else if (abs instanceof TLUserEmpty) {
					ps_insert_or_ignore.setInt(1, abs.getId());
					ps_insert_or_ignore.setNull(2, Types.VARCHAR);
					ps_insert_or_ignore.setNull(3, Types.VARCHAR);
					ps_insert_or_ignore.setNull(4, Types.VARCHAR);
					ps_insert_or_ignore.setString(5, "empty_user");
					ps_insert_or_ignore.setNull(6, Types.VARCHAR);
					ps_insert_or_ignore.addBatch();
				} else {
					throw new RuntimeException("Unexpected " + abs.getClass().getName());
				}
			}
			conn.setAutoCommit(false);
			ps_insert_or_ignore.executeBatch();
			ps_insert_or_ignore.clearBatch();
			ps_insert_or_replace.executeBatch();
			ps_insert_or_replace.clearBatch();
			conn.commit();
			conn.setAutoCommit(true);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception shown above happened.");
		}
	}
	
	public LinkedList<TLMessage> getMessagesWithMedia() {
		try {
			LinkedList<TLMessage> list = new LinkedList<TLMessage>();
			ResultSet rs = stmt.executeQuery("SELECT data FROM messages WHERE has_media=1");
			while (rs.next()) {
				list.add(bytesToTLMessage(rs.getBytes(1)));
			}
			rs.close();
			return list;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception occured. See above.");
		}
	}
	
	public int getMessagesFromUserCount() {
		try {
			ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages WHERE sender_id=" + user_manager.getUser().getId());
			rs.next();
			return rs.getInt(1);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public LinkedList<Integer> getIdsFromQuery(String query) {
		try {
			LinkedList<Integer> list = new LinkedList<Integer>();
			ResultSet rs = stmt.executeQuery(query);
			while(rs.next()) { list.add(rs.getInt(1)); }
			rs.close();
			return list;
		} catch (SQLException e) { throw new RuntimeException(e); }
	}
			
	
	public HashMap<String, Integer> getMessageTypesWithCount() {
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		try {
			ResultSet rs = stmt.executeQuery("SELECT message_type, COUNT(id) FROM messages GROUP BY message_type");
			while (rs.next()) {
				map.put(rs.getString(1), rs.getInt(2));
			}
			return map;
		} catch (Exception e) { throw new RuntimeException(e); }
	}
	
	public HashMap<String, Integer> getMessageMediaTypesWithCount() {
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		try {
			ResultSet rs = stmt.executeQuery("SELECT media_type, COUNT(id) FROM messages GROUP BY media_type");
			while (rs.next()) {
				String s = rs.getString(1);
				if (s==null) s="null";
				map.put(s, rs.getInt(2));
			}
			return map;
		} catch (Exception e) { throw new RuntimeException(e); }
	}
			
	
	public LinkedList<Chat> getListOfChatsForExport() {
		LinkedList<Chat> list = new LinkedList<Chat>();
		try {
			ResultSet rs = stmt.executeQuery("SELECT chats.id, chats.name, COUNT(messages.id) as c "+
				"FROM chats, messages WHERE messages.chat_id IS NOT NULL AND messages.chat_id=chats.id "+
				"GROUP BY chats.id ORDER BY c DESC");
			while (rs.next()) {
				list.add(new Chat(rs.getInt(1), rs.getString(2), rs.getInt(3)));
			}
			rs.close();
			return list;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
		}
	}
	
	
	public LinkedList<Dialog> getListOfDialogsForExport() {
		LinkedList<Dialog> list = new LinkedList<Dialog>();
		try {
			ResultSet rs = stmt.executeQuery(
				"SELECT users.id, first_name, last_name, username, COUNT(messages.id) as c " +
				"FROM users, messages WHERE messages.dialog_id IS NOT NULL AND messages.dialog_id=users.id " +
				"GROUP BY users.id ORDER BY c DESC");
			while (rs.next()) {
				list.add(new Dialog(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
			}
			rs.close();
			return list;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
		}
	}
	
	public LinkedList<HashMap<String, Object>> getMessagesForExport(Dialog d) {
		return getMessagesForExport("dialog_id", d.id);
	}
	
	public LinkedList<HashMap<String, Object>> getMessagesForExport(Chat c) {
		return getMessagesForExport("chat_id", c.id);
	}
	
	private LinkedList<HashMap<String, Object>> getMessagesForExport(String type, Integer id) {
		try {
			ResultSet rs = stmt.executeQuery("SELECT messages.id as message_id, text, time*1000 as time, has_media, " +
				"media_type, media_file, media_size, users.first_name as user_first_name, users.last_name as user_last_name, " +
				"users.username as user_username, users.id as user_id, " +
				"users_fwd.first_name as user_fwd_first_name, users_fwd.last_name as user_fwd_last_name, users_fwd.username as user_fwd_username " +
				"FROM messages, users LEFT JOIN users AS users_fwd ON users_fwd.id=fwd_from_id WHERE " +
				"users.id=messages.sender_id AND " + type + "=" + id + " " +
				"ORDER BY messages.id");
			SimpleDateFormat format_time = new SimpleDateFormat("HH:mm:ss");
			SimpleDateFormat format_date = new SimpleDateFormat("d MMM yy");
			ResultSetMetaData meta = rs.getMetaData();
			int columns = meta.getColumnCount();
			LinkedList<HashMap<String, Object>> list = new LinkedList<HashMap<String, Object>>();
			String old_date = null;
			while (rs.next()) {
				HashMap<String, Object> h = new HashMap<String, Object>(columns);
				for (int i=1; i<=columns; i++) {
					h.put(meta.getColumnName(i), rs.getObject(i));
				}
				// Additional values to make up for Mustache's inability to format dates
				Date d = rs.getTime("time");
				String date = format_date.format(d);
				h.put("formatted_time", format_time.format(d));
				h.put("formatted_date", date);
				if (rs.getString("media_type")!=null) {
					h.put("media_" + rs.getString("media_type"), true);
				}
				h.put("from_me", rs.getInt("user_id")==user_manager.getUser().getId());
				h.put("is_new_date", !date.equals(old_date));
				old_date = date;
				
				list.add(h);
			}
			rs.close();
			return list;
		} catch(Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
		}
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
	
		
	
	
	
	
	
	public class Dialog {
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
	}
	
	public class Chat {
		public int id;
		public String name;
		public int count;
		
		public Chat(int id, String name, int count) {
			this.id = id;
			this.name = name;
			this.count = count;
		}
	}
}
