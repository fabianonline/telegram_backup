package de.fabianonline.telegram_backup;

import de.fabianonline.telegram_backup.MyStorage;
import org.telegram.api.engine.AppInfo;
import org.telegram.api.engine.ApiCallback;
import org.telegram.api.engine.TelegramApi;
import org.telegram.api.TLConfig;
import org.telegram.api.TLAbsUpdates;
import org.telegram.api.requests.*;
import java.io.IOException;

public class Main {
	public static void main(String[] args) {
		System.out.println("Hello World");
		
		MyStorage storage = new MyStorage();
		
		AppInfo appinfo = new AppInfo(32860, "Desktop", "0.0.1", "0.0.1", "EN");
		
		ApiCallback callback = new ApiCallback() {
			public void onApiDies(TelegramApi api) { System.out.println("onApiDies"); }
			public void onUpdatesInvalidated(TelegramApi api) { System.out.println("onUpdatesInvalidated"); }
			public void onUpdate(TLAbsUpdates updates) { System.out.println("onUpdate"); }
			public void onAuthCancelled(TelegramApi api) { System.out.println("onAuthCancelled"); }
			
		};
		
		TelegramApi api = new TelegramApi(storage, appinfo, callback);
		
		try {
			TLConfig config = api.doRpcCall(new TLRequestHelpGetConfig());
		} catch(IOException ex) {
			System.out.println("IOException caught.");
		}
		
	}
}
