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

import de.fabianonline.telegram_backup.CommandLineController;
import de.fabianonline.telegram_backup.Utils;
import de.fabianonline.telegram_backup.Version;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.classic.Level;

public class CommandLineRunner {
	public static void main(String[] args) {
		CommandLineOptions.parseOptions(args);

		// Set up logging
		Logger logger = (Logger)LoggerFactory.getLogger(CommandLineRunner.class);
		Logger rootLogger = (Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		LoggerContext rootContext = rootLogger.getLoggerContext();
		rootContext.reset();
		
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(rootContext);
		encoder.setPattern("%d{HH:mm:ss} %-5level %-35.-35(%logger{0}.%method): %message%n");
		encoder.start();
		
		ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<ILoggingEvent>();
		appender.setContext(rootContext);
		appender.setEncoder(encoder);
		appender.start();
		
		rootLogger.addAppender(appender);
		rootLogger.setLevel(Level.OFF);

		if (CommandLineOptions.cmd_trace) {
			((Logger)LoggerFactory.getLogger("de.fabianonline.telegram_backup")).setLevel(Level.TRACE);
		} else if (CommandLineOptions.cmd_debug) {
			((Logger)LoggerFactory.getLogger("de.fabianonline.telegram_backup")).setLevel(Level.DEBUG);
		}
		
		if (CommandLineOptions.cmd_trace_telegram) {
			((Logger)LoggerFactory.getLogger("com.github.badoualy")).setLevel(Level.TRACE);
		}
		
		Version v = Utils.getNewestVersion();
		if (v!=null && v.isNewer) {
			System.out.println("A newer version is vailable!");
			System.out.println("You are using: " + Config.APP_APPVER);
			System.out.println("Available:     " + v.version);
			System.out.println("Get it here:   " + v.url);
			System.out.println();
			System.out.println("Changes in this version:");
			System.out.println(v.body);
			System.out.println();
		}

		if (true || CommandLineOptions.cmd_console) {
			// Always use the console for now.
			new CommandLineController();
		} else {
			new GUIController();
		}
	}
}
