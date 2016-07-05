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

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.Database;

import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

class HTMLExporter {
	public void export(UserManager user) {
		try {
			Database db = new Database(user);
			
			// Create base dir
			String base = user.getFileBase() + "export" + File.separatorChar + "html" + File.separatorChar;
			new File(base).mkdirs();
			new File(base + "dialogs").mkdirs();
			
			PrintWriter index = new PrintWriter(new BufferedWriter(new FileWriter(base + "index.html")));
			index.println("<!DOCTYPE html>");
			index.println("<html>");
			index.println("<head>");
			index.println("<title>Telegram Backup for " + user.getUserString() + "</title>");
			index.println("</head>");
			
			index.println("<body>");
			index.println("<h1>Telegram Backup</h1>");
			index.println("<h2>" + user.getUserString() + "</h2>");
			LinkedList<Database.Dialog> dialogs = db.getListOfDialogsForExport();
			index.println("<h3>Dialogs</h3>");
			index.println("<ul>");
			for (Database.Dialog dialog : dialogs) {
				index.println("<li><a href='dialogs/user_" + dialog.id + ".html'>" + dialog.first_name + " " + (dialog.last_name!=null ? dialog.last_name : "") + "</a> <span class='count'>(" + dialog.count + ")</span></li>");
				String filename = base + "dialogs" + File.separatorChar + "user_" + dialog.id + ".html";
				final PrintWriter chatfile = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
				db.getMessagesForExport(dialog, new ChatLineWriter(chatfile));
				chatfile.close();
			}
			LinkedList<Database.Chat> chats = db.getListOfChatsForExport();
			index.println("<h3>Group chats</h3>");
			index.println("<ul>");
			for (Database.Chat chat : chats) {
				index.println("<li><a href='dialogs/chat_" + chat.id + ".html'>" + chat.name + "</a> <span class='count'>(" + chat.count + ")</span></li>");
				String filename = base + "dialogs" + File.separatorChar + "chat_" + chat.id + ".html";
				final PrintWriter chatfile = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
				db.getMessagesForExport(chat, new ChatLineWriter(chatfile));
				chatfile.close();
			}
			index.println("</ul>");
			index.println("</body>");
			index.println("</html>");
			index.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Exception above!");
		}
	}
	
	class ChatLineWriter implements Database.ChatMessageProcessor {
		PrintWriter w;
		
		public ChatLineWriter(PrintWriter w) {
			this.w = w;
		}
		
		public void process(Database.Message msg) {
			w.println("" + String.format("%1$tF %1$tT", msg.time) + " - " + msg.text + "<br>");
		}
	}
		
}
