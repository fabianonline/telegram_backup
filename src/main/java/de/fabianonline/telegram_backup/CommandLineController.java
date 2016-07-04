package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.api.Kotlogram;
import com.github.badoualy.telegram.api.TelegramApp;
import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;

public class CommandLineController {
	private ApiStorage storage;
	public TelegramApp app;
	public UserManager user = null;
	
	public CommandLineController(CommandLineOptions options) {
		if (options.target != null) {
			Config.FILE_BASE = options.target;
		}
		
		System.out.println("Target directory for files: " + Config.FILE_BASE);

		if (options.cmd_help) this.show_help();
		if (options.cmd_list_accounts) this.list_accounts();
		
		app = new TelegramApp(Config.APP_ID, Config.APP_HASH, Config.APP_MODEL, Config.APP_SYSVER, Config.APP_APPVER, Config.APP_LANG);
		if (options.cmd_debug) Kotlogram.setDebugLogEnabled(true);

		String account = null;
		Vector<String> accounts = Utils.getAccounts();
		if (options.cmd_login) {
			// do nothing
		} else if (options.account!=null) {
			boolean found = false;
			for (String acc : accounts) if (acc.equals(options.account)) found=true;
			if (!found) {
				show_error("Couldn't find account '" + options.account + "'. Maybe you want to use '--login' first?");
			}
			account = options.account;
		} else if (accounts.size()==0) {
			System.out.println("No accounts found. Starting login process...");
			options.cmd_login = true;
		} else if (accounts.size()==1) {
			account = accounts.firstElement();
			System.out.println("Using only available account: " + account);
		} else {
			show_error("You didn't specify which account to use.\n" +
				"Use '--account <x>' to use account <x>.\n" +
				"Use '--list-accounts' to see all available accounts.");
		}
		
		storage = new ApiStorage(account);
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
		System.out.println("  -h, --help                   Shows this help.");
		System.out.println("  -a, --account <x>            Use account <x>.");
		System.out.println("  -l, --login                  Login to an existing telegram account.");
		System.out.println("      --debug                  Show (lots of) debug information.");
		System.out.println("  -A, --list-accounts          List all existing accounts ");
		System.out.println("      --limit-messages <x>     Downloads at most the most recent <x> messages.");
		System.out.println("  -t, --target <x>             Target directory for the files.");
		
		System.exit(0);
	}
	
	private void list_accounts() {
		System.out.println("List of available accounts:");
		List<String> accounts = Utils.getAccounts();
		if (accounts.size()>0) {
			for (String str : accounts) {
				System.out.println("    " + str);
			}
			System.out.println("Use '--account <x>' to use one of those accounts.");
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
	
}
