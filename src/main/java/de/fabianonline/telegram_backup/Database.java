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
						+ "type TEXT, "
						+ "text TEXT, "
						+ "time TEXT, "
						+ "has_media BOOLEAN, "
						+ "sticker TEXT, "
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
	
	public void save(TLVector<TLAbsMessage> all) {
		try {
			PreparedStatement ps = conn.prepareStatement(
				"INSERT INTO messages " +
				"(id, dialog_id, from_id, type, text, time, has_media, data, sticker) " + 
				"VALUES " +
				"(?,  ?,         ?,       ?,    ?,    ?,    ?,         ?,    ?)");
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
						System.out.println("" + msg.getId() + ": >" + text + "<");
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
						System.out.println("" + msg.getId() + ": >" + msg.getMessage() + "< " + sticker);
						ps.setString(9, sticker);
					} else {
						ps.setNull(9, Types.VARCHAR);
					}
					ps.addBatch();
				} else if (abs instanceof TLMessageService) {
					// Ignore service messages.
				} else if (abs instanceof TLMessageEmpty) {
					TLMessageEmpty msg = (TLMessageEmpty) abs;
					ps.setInt(1, msg.getId());
					ps.setNull(2, Types.INTEGER);
					ps.setNull(3, Types.INTEGER);
					ps.setNull(4, Types.VARCHAR);
					ps.setNull(5, Types.VARCHAR);
					ps.setNull(6, Types.INTEGER);
					ps.setNull(7, Types.BOOLEAN);
					ps.setNull(8, Types.BLOB);
					ps.addBatch();
				} else {
					throw new RuntimeException("Unexpected Message type: " + abs.getClass().getName());
				}
			}
			conn.setAutoCommit(false);
			ps.executeBatch();
			ps.clearBatch();
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
}
