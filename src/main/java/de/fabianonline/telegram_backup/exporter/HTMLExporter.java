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

package de.fabianonline.telegram_backup.exporter;

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.Database;

import java.io.File;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.URL;
import org.apache.commons.io.FileUtils;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTMLExporter {
	private static Logger logger = LoggerFactory.getLogger(HTMLExporter.class);
	
	public void export(UserManager user) {
		try {
			Database db = new Database(user, null);
			
			// Create base dir
			logger.debug("Creating base dir");
			String base = user.getFileBase() + "files" + File.separatorChar;
			new File(base).mkdirs();
			new File(base + "dialogs").mkdirs();
			
			logger.debug("Fetching dialogs");
			LinkedList<Database.Dialog> dialogs = db.getListOfDialogsForExport();
			logger.trace("Got {} dialogs", dialogs.size());
			logger.debug("Fetching chats");
			LinkedList<Database.Chat> chats = db.getListOfChatsForExport();
			logger.trace("Got {} chats", chats.size());
			
			logger.debug("Generating index.html");
			HashMap<String, Object> scope = new HashMap<String, Object>();
			scope.put("user", user);
			scope.put("dialogs", dialogs);
			scope.put("chats", chats);
			
			// Collect stats data
			scope.put("count.chats", chats.size());
			scope.put("count.dialogs", dialogs.size());
			
			int count_messages_chats = 0;
			int count_messages_dialogs = 0;
			for (Database.Chat   c : chats)   count_messages_chats   += c.count;
			for (Database.Dialog d : dialogs) count_messages_dialogs += d.count;
			
			scope.put("count.messages", count_messages_chats + count_messages_dialogs);
			scope.put("count.messages.chats", count_messages_chats);
			scope.put("count.messages.dialogs", count_messages_dialogs);
			
			scope.put("count.messages.from_me", db.getMessagesFromUserCount());

			scope.put("heatmap_data", intArrayToString(db.getMessageTimesMatrix()));
			
			scope.putAll(db.getMessageAuthorsWithCount());
			scope.putAll(db.getMessageTypesWithCount());
			scope.putAll(db.getMessageMediaTypesWithCount());
			
			MustacheFactory mf = new DefaultMustacheFactory();
			Mustache mustache = mf.compile("templates/html/index.mustache");
			OutputStreamWriter w = getWriter(base + "index.html");
			mustache.execute(w, scope);
			w.close();
			
			mustache = mf.compile("templates/html/chat.mustache");
			
			logger.debug("Generating dialog pages");
			for (Database.Dialog d : dialogs) {
				LinkedList<HashMap<String, Object>> messages = db.getMessagesForExport(d);
				scope.clear();
				scope.put("user", user);
				scope.put("dialog", d);
				scope.put("messages", messages);
				
				scope.putAll(db.getMessageAuthorsWithCount(d));
				scope.put("heatmap_data", intArrayToString(db.getMessageTimesMatrix(d)));
				scope.putAll(db.getMessageTypesWithCount(d));
				scope.putAll(db.getMessageMediaTypesWithCount(d));
				
				w = getWriter(base + "dialogs" + File.separatorChar + "user_" + d.id + ".html");
				mustache.execute(w, scope);
				w.close();
			}
			
			logger.debug("Generating chat pages");
			for (Database.Chat c : chats) {
				LinkedList<HashMap<String, Object>> messages = db.getMessagesForExport(c);
				scope.clear();
				scope.put("user", user);
				scope.put("chat", c);
				scope.put("messages", messages);
				
				scope.putAll(db.getMessageAuthorsWithCount(c));
				scope.put("heatmap_data", intArrayToString(db.getMessageTimesMatrix(c)));
				scope.putAll(db.getMessageTypesWithCount(c));
				scope.putAll(db.getMessageMediaTypesWithCount(c));
				
				w = getWriter(base + "dialogs" + File.separatorChar + "chat_" + c.id + ".html");
				mustache.execute(w, scope);
				w.close();
			}
			
			logger.debug("Generating additional files");
			// Copy CSS
			URL cssFile = getClass().getResource("/templates/html/style.css");
			File dest = new File(base + "style.css");
			FileUtils.copyURLToFile(cssFile, dest);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
		}
	}
	
	private OutputStreamWriter getWriter(String filename) throws FileNotFoundException {
		logger.trace("Creating writer for file {}", filename);
		return new OutputStreamWriter(new FileOutputStream(filename), Charset.forName("UTF-8").newEncoder());
	}

	private String intArrayToString(int[][] data) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int x=0; x<data.length; x++) {
			for (int y=0; y<data[x].length; y++) {
				if (x>0 || y>0) sb.append(",");
				sb.append("[" + x + "," + y + "," + data[x][y] + "]");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	private String mapToString(Map<String, Integer> map) {
		StringBuilder sb = new StringBuilder("[");
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			sb.append("['" + entry.getKey() + "', " + entry.getValue() + "],");
		}
		sb.append("]");
		return sb.toString();
	}
}
