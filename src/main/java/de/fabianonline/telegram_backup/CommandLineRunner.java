package de.fabianonline.telegram_backup;

import de.fabianonline.telegram_backup.CommandLineController;

public class CommandLineRunner {
	public static void main(String[] args) {
		CommandLineOptions options = new CommandLineOptions(args);
		if (true || options.cmd_console) {
			// Always use the console for now.
			new CommandLineController(options);
		} else {
			new GUIController(options);
		}
	}
}
