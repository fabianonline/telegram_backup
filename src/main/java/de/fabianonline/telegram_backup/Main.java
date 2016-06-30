package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.api.Kotlogram;
import com.github.badoualy.telegram.api.TelegramApp;
import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;

import de.fabianonline.telegram_backup.Config;
import de.fabianonline.telegram_backup.ApiStorage;
import de.fabianonline.telegram_backup.UserManager;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Main {
	public static TelegramApp app = new TelegramApp(Config.APP_ID, Config.APP_HASH, Config.APP_MODEL, Config.APP_SYSVER, Config.APP_APPVER, Config.APP_LANG);
	public static UserManager user = null;
	
	public static void main(String[] args) {
		System.out.println("Hello World");
		
		Kotlogram.setDebugLogEnabled(true);
		
		TelegramClient client = Kotlogram.getDefaultClient(app, new ApiStorage());
		
		try {
			user = new UserManager(client);
			
			if (!user.isLoggedIn()) {
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
			}
			
			System.out.println("You are now signed in as " + user.getUserString());
		} catch (RpcErrorException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			client.close();
		}
		System.out.println("----- EXIT -----");
	}
}
