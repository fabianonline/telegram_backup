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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

public class HTMLExporter {
	public void export(UserManager user) {
		try {
			Database db = new Database(user, null);
			
			// Create base dir
			String base = user.getFileBase() + "export" + File.separatorChar + "html" + File.separatorChar;
			new File(base).mkdirs();
			new File(base + "dialogs").mkdirs();
			
			LinkedList<Database.Dialog> dialogs = db.getListOfDialogsForExport();
			LinkedList<Database.Chat> chats = db.getListOfChatsForExport();
			
			
			HashMap<String, Object> scope = new HashMap<String, Object>();
			scope.put("user", user.getUser());
			scope.put("dialogs", dialogs);
			scope.put("chats", chats);
			
			MustacheFactory mf = new DefaultMustacheFactory();
			Mustache mustache = mf.compile("templates/html/index.mustache");
			FileWriter w = new FileWriter(base + "index.html");
			mustache.execute(w, scope);
			w.close();
			
			for (Database.Dialog d : dialogs) {
				//LinkedList<HashMap<String, Object>> messages = db.getMessagesForTemplate(dialog);
			}
			System.exit(0);
			/*
				String filename = base + "dialogs" + File.separatorChar + "user_" + dialog.id + ".html";
				final PrintWriter chatfile = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
				db.getMessagesForExport(dialog, new ChatLineWriter(chatfile));
				chatfile.close();

				String filename = base + "dialogs" + File.separatorChar + "chat_" + chat.id + ".html";
				final PrintWriter chatfile = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
				db.getMessagesForExport(chat, new ChatLineWriter(chatfile));
				chatfile.close();
			*/
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
		}
	}
//			w.println("" + String.format("%1$tF %1$tT", msg.time) + " - " + msg.text + "<br>");
}
