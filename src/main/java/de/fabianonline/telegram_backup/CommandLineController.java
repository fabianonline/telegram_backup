package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.api.Kotlogram;
import com.github.badoualy.telegram.api.TelegramApp;
import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;

import de.fabianonline.telegram_backup.Config;
import de.fabianonline.telegram_backup.ApiStorage;
import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.DownloadManager;
import de.fabianonline.telegram_backup.CommandLineDownloadProgress;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class CommandLineController {
	private ApiStorage storage;
	private CommandLineOptions options;
	public TelegramApp app;
	public UserManager user = null;
	
	public CommandLineController(String[] args) {
		options = new CommandLineOptions(args);
		if (options.cmd_help) this.show_help();
		if (options.cmd_list_accounts) this.list_accounts();
		if (options.account==null && !options.cmd_login) {
			show_error("You neither used --login nor did you give an account using --account. You need to use one of those two.\nPlease have a look at --help.");
		}
		
		app = new TelegramApp(Config.APP_ID, Config.APP_HASH, Config.APP_MODEL, Config.APP_SYSVER, Config.APP_APPVER, Config.APP_LANG);
		if (options.cmd_debug) Kotlogram.setDebugLogEnabled(true);
		
		if (options.account != null && !options.cmd_login) {
			storage = new ApiStorage(options.account);
		} else {
			storage = new ApiStorage(null);
		}
		TelegramClient client = Kotlogram.getDefaultClient(app, storage);
		
		try {
			user = new UserManager(client);
			
			if (options.cmd_login) {
				System.out.println("Please enter your phone number in international format.");
				System.out.println("Example: +4917077651234");
				System.out.print("> ");
				String phone = new Scanner(System.in).nextLine();
				user.sendCodeToPhoneNumber(phone);
				
				System.out.println("Telegram sent you a code. Please enter it here.");
				System.out.print("> ");
				String code = new Scanner(System.in).nextLine();
				user.verifyCode(code);
				
				if (user.isPasswordNeeded()) {
					System.out.println("We also need your account password.");
					System.out.print("> ");
					String pw = new Scanner(System.in).nextLine();
					user.verifyPassword(pw);
				}
				storage.setPrefix("+" + user.getUser().getPhone());
				
				System.out.println("Please run this tool with '--account +" + user.getUser().getPhone() + " to use this account.");
				System.exit(0);
			}
			
			System.out.println("You are logged in as " + user.getUserString());
			
			DownloadManager d = new DownloadManager(user, client, new CommandLineDownloadProgress());
			d.downloadMessages(options.limit_messages);
			d.downloadMedia();
		} catch (RpcErrorException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			client.close();
		}
		System.out.println("----- EXIT -----");
	}
	
	private void show_help() {
		System.out.println("Valid options are:");
		System.out.println("  --help                          Shows this help.");
		System.out.println("  --account <x>                   Use account <x>.");
		System.out.println("  --login                         Login to an existing telegram account.");
		System.out.println("  --debug                         Show (lots of) debug information.");
		System.out.println("  --list-accounts                 List all existing accounts ");
		System.out.println("  --limit-messages <x>            Downloads at most the most recent <x> messages.");
		
		System.exit(0);
	}
	
	private void list_accounts() {
		System.out.println("List of available accounts:");
		int count = 0;
		File folder = new File(Config.FILE_BASE);
		File[] files = folder.listFiles();
		for (File f : files) {
			if (f.isDirectory()) {
				count++;
				System.out.println("  " + f.getName());
			}
		}
		if (count>0) {
			System.out.println("Use '--acount <x>' to use one of those accounts.");
		} else {
			System.out.println("NO ACCOUNTS FOUND");
			System.out.println("Use '--login' to login to a telegram account.");
		}
		System.exit(0);
	}
	
	public static void show_error(String error) {
		System.out.println("ERROR: " + error);
		System.exit(1);
	}
	
	private class CommandLineOptions {
		public String account = null;
		public boolean cmd_help = false;
		public boolean cmd_login = false;
		public boolean cmd_debug = false;
		public boolean cmd_list_accounts = false;
		public Integer limit_messages = null;
		
		public CommandLineOptions(String[] args) {
			String last_cmd = null;
			
			for (String arg : args) {
				if (last_cmd != null) {
					switch(last_cmd) {
						case "--account": this.account=arg; break;
						case "--limit-messages": this.limit_messages=Integer.parseInt(arg); break;
					}
					last_cmd = null;
					continue;
				}
				
				switch(arg) {
					case "--account": last_cmd=arg; continue;
					case "--help": this.cmd_help=true; break;
					case "--login": this.cmd_login=true; break;
					case "--debug": this.cmd_debug=true; break;
					case "--list-accounts": this.cmd_list_accounts=true; break;
					case "--limit-messages": last_cmd=arg; continue;
					default: throw new RuntimeException("Unknown command " + arg);
				}
			}
			if (last_cmd != null) {
				CommandLineController.show_error("Command " + last_cmd + " had no parameter set.");
			}
		}
	}
}
