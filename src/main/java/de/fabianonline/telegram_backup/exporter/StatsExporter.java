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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

public class StatsExporter {
	public void export(UserManager user) {
		try {
			Database db = new Database(user, null);
			
			// Create base dir
			String base = user.getFileBase() + "export" + File.separatorChar + "stats" + File.separatorChar;
			new File(base).mkdirs();
			
			HashMap<String, Object> scope = new HashMap<String, Object>();
			
			// Collect data
			LinkedList<Database.Dialog> dialogs = db.getListOfDialogsForExport();
			LinkedList<Database.Chat> chats = db.getListOfChatsForExport();
			
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
			
			HashMap<String, Integer> types;
			types = db.getMessageTypesWithCount();
			for (Map.Entry<String, Integer> entry : types.entrySet()) {
				scope.put("count.messages.type." + entry.getKey(), entry.getValue());
			}
			
			types = db.getMessageMediaTypesWithCount();
			for (Map.Entry<String, Integer> entry : types.entrySet()) {
				scope.put("count.messages.media_type." + entry.getKey(), entry.getValue());
			}
			
			MustacheFactory mf = new DefaultMustacheFactory();
			Mustache mustache = mf.compile("templates/stats/index.mustache");
			FileWriter w = new FileWriter(base + "index.html");
			mustache.execute(w, scope);
			w.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
		}
	}
}
