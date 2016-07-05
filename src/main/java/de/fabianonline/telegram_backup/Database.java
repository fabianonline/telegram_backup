package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.core.TLVector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.sql.Time;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedList;

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.StickerConverter;

class Database {
	private Connection conn;
	private Statement stmt;
	private UserManager user_manager;
	
	public Database(UserManager user_manager) {
		this.user_manager = user_manager;
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
				System.out.println("  Updating to version 1...");
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
				stmt.executeUpdate("INSERT INTO database_versions (version) VALUES (1)");
				version = 1;
			}
			if (version==1) {
				System.out.println("  Updating to version 2...");
				stmt.executeUpdate("ALTER TABLE people RENAME TO 'users'");
				stmt.executeUpdate("ALTER TABLE users ADD COLUMN phone TEXT");
				stmt.executeUpdate("INSERT INTO database_versions (version) VALUES (2)");
				version = 2;
			}
			if (version==2) {
				System.out.println("  Updating to version 3...");
				stmt.executeUpdate("ALTER TABLE dialogs RENAME TO 'chats'");
				stmt.executeUpdate("INSERT INTO database_versions (version) VALUES (3)");
				version = 3;
			}
			if (version==3) {
				System.out.println("  Updating to version 4...");
				stmt.executeUpdate("CREATE TABLE messages_new (id INTEGER PRIMARY KEY ASC, dialog_id INTEGER, to_id INTEGER, from_id INTEGER, from_type TEXT, text TEXT, time INTEGER, has_media BOOLEAN, sticker TEXT, data BLOB,type TEXT);");
				stmt.executeUpdate("INSERT INTO messages_new SELECT * FROM messages");
				stmt.executeUpdate("DROP TABLE messages");
				stmt.executeUpdate("ALTER TABLE messages_new RENAME TO 'messages'");
				stmt.executeUpdate("INSERT INTO database_versions (version) VALUES (4)");
				version = 4;
			}
			
			System.out.println("Database is ready.");
		} catch (SQLException e) {
			System.out.println(e.getSQLState());
			e.printStackTrace();
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
	
	public void saveMessages(TLVector<TLAbsMessage> all) {
		try {
			PreparedStatement ps = conn.prepareStatement(
				"INSERT OR REPLACE INTO messages " +
				"(id, dialog_id, from_id, from_type, text, time, has_media, data, sticker, type) " +
				"VALUES " +
				"(?,  ?,         ?,       ?,         ?,    ?,    ?,         ?,    ?,       ?)");
			PreparedStatement ps_insert_or_ignore = conn.prepareStatement(
				"INSERT OR IGNORE INTO messages " +
				"(id, dialog_id, from_id, from_type, text, time, has_media, data, sticker, type) " +
				"VALUES " +
				"(?,  ?,         ?,       ?,         ?,    ?,    ?,         ?,    ?,       ?)");
			for (TLAbsMessage abs : all) {
				if (abs instanceof TLMessage) {
					TLMessage msg = (TLMessage) abs;
					ps.setInt(1, msg.getId());
					TLAbsPeer peer = msg.getToId();
					if (peer instanceof TLPeerChat) {
						ps.setInt(2, ((TLPeerChat)peer).getChatId());
						ps.setString(4, "chat");
					} else if (peer instanceof TLPeerChannel) {
						ps.setInt(2, ((TLPeerChannel)peer).getChannelId());
						ps.setString(4, "channel");
					} else if (peer instanceof TLPeerUser) {
						int id = ((TLPeerUser)peer).getUserId();
						if (id==this.user_manager.getUser().getId()) {
							id = msg.getFromId();
						}
						ps.setInt(2, id);
						ps.setString(4, "user");
					} else {
						throw new RuntimeException("Unexpected Peer type: " + peer.getClass().getName());
					}
					ps.setInt(3, msg.getFromId());
					String text = msg.getMessage();
					if ((text==null || text.equals("")) && msg.getMedia()!=null) {
						if (msg.getMedia() instanceof TLMessageMediaDocument) {
							text = ((TLMessageMediaDocument)msg.getMedia()).getCaption();
						} else if (msg.getMedia() instanceof TLMessageMediaPhoto) {
							text = ((TLMessageMediaPhoto)msg.getMedia()).getCaption();
						}
					}
					ps.setString(5, text);
					ps.setString(6, ""+msg.getDate());
					ps.setBoolean(7, msg.getMedia() != null);
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					msg.serializeBody(stream);
					ps.setBytes(8, stream.toByteArray());
					String sticker = null;
					if (msg.getMedia()!=null && msg.getMedia() instanceof TLMessageMediaDocument) {
						TLMessageMediaDocument md = (TLMessageMediaDocument)msg.getMedia();
						if (md.getDocument() instanceof TLDocument) {
							for (TLAbsDocumentAttribute attr : ((TLDocument)md.getDocument()).getAttributes()) {
								if (attr instanceof TLDocumentAttributeSticker) {
									sticker = StickerConverter.makeFilename((TLDocumentAttributeSticker)attr);
									break;
								}
							}
						}
					}
					if (sticker != null) {
						ps.setString(9, sticker);
					} else {
						ps.setNull(9, Types.VARCHAR);
					}
					ps.setString(10, "message");
					ps.addBatch();
				} else if (abs instanceof TLMessageService) {
					ps_insert_or_ignore.setInt(1, abs.getId());
					ps_insert_or_ignore.setNull(2, Types.INTEGER);
					ps_insert_or_ignore.setNull(3, Types.INTEGER);
					ps_insert_or_ignore.setNull(4, Types.VARCHAR);
					ps_insert_or_ignore.setNull(5, Types.VARCHAR);
					ps_insert_or_ignore.setNull(6, Types.INTEGER);
					ps_insert_or_ignore.setNull(7, Types.BOOLEAN);
					ps_insert_or_ignore.setNull(8, Types.BLOB);
					ps_insert_or_ignore.setNull(9, Types.VARCHAR);
					ps_insert_or_ignore.setString(10, "service_message");
					ps_insert_or_ignore.addBatch();
				} else if (abs instanceof TLMessageEmpty) {
					TLMessageEmpty msg = (TLMessageEmpty) abs;
					ps_insert_or_ignore.setInt(1, msg.getId());
					ps_insert_or_ignore.setNull(2, Types.INTEGER);
					ps_insert_or_ignore.setNull(3, Types.INTEGER);
					ps_insert_or_ignore.setNull(4, Types.VARCHAR);
					ps_insert_or_ignore.setNull(5, Types.VARCHAR);
					ps_insert_or_ignore.setNull(6, Types.INTEGER);
					ps_insert_or_ignore.setNull(7, Types.BOOLEAN);
					ps_insert_or_ignore.setNull(8, Types.BLOB);
					ps_insert_or_ignore.setNull(9, Types.VARCHAR);
					ps_insert_or_ignore.setString(10, "empty_message");
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

	public void saveChats(TLVector<TLAbsChat> all) {
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

	public void saveUsers(TLVector<TLAbsUser> all) {
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
				ByteArrayInputStream stream = new ByteArrayInputStream(rs.getBytes(1));
				TLMessage msg = new TLMessage();
				msg.deserializeBody(stream, TLApiContext.getInstance());
				list.add(msg);
			}
			rs.close();
			return list;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception occured. See above.");
		}
	}
	
	public LinkedList<Chat> getListOfChatsForExport() {
		LinkedList<Chat> list = new LinkedList<Chat>();
		try {
			ResultSet rs = stmt.executeQuery("SELECT chats.id, chats.name, COUNT(messages.id) as c "+
				"FROM chats, messages WHERE messages.from_type='chat' AND messages.dialog_id=chats.id "+
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
				"FROM users, messages WHERE messages.from_type='user' AND messages.dialog_id=users.id " +
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
	
	public void getMessagesForExport(Dialog d, ChatMessageProcessor p) {
		getMessagesForExport("user", d.id, p);
	}
	
	public void getMessagesForExport(Chat c, ChatMessageProcessor p) {
		getMessagesForExport("chat", c.id, p);
	}
	
	private void getMessagesForExport(String type, Integer id, ChatMessageProcessor p) {
		try {
			ResultSet rs = stmt.executeQuery("SELECT messages.id, text, time*1000, has_media, " +
				"sticker, first_name, last_name, username FROM messages, users WHERE " +
				"users.id=messages.from_id AND dialog_id=" + id + " AND from_type='" + type + "' " +
				"ORDER BY messages.id");
			while (rs.next()) {
				Message m = new Message(
					rs.getInt(1),
					rs.getString(2),
					rs.getTime(3),
					rs.getBoolean(4),
					rs.getString(5),
					rs.getString(6),
					rs.getString(7),
					rs.getString(8));
				p.process(m);
			}
			rs.close();
		} catch(Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
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
	
	public class Message {
		public int id;
		public String text;
		public Time time;
		public boolean has_media;
		public String sticker;
		public String first_name;
		public String last_name;
		public String username;
		
		public Message(int i, String t, Time t2, boolean m, String st, String n1, String n2, String n3) {
			this.id = i;
			this.text = t;
			this.time = t2;
			this.has_media = m;
			this.sticker = st;
			this.first_name = n1;
			this.last_name = n2;
			this.username = n3;
		}
	}
	
	public interface ChatMessageProcessor {
		public void process(Message msg);
	}
}
