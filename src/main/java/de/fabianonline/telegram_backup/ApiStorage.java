package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.api.TelegramApiStorage;
import com.github.badoualy.telegram.mtproto.DataCenter;
import com.github.badoualy.telegram.mtproto.auth.AuthKey;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

class ApiStorage implements TelegramApiStorage {
	public void saveAuthKey(AuthKey authKey) { 
		try {
			FileUtils.writeByteArrayToFile(Config.FILE_AUTH_KEY, authKey.getKey());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public AuthKey loadAuthKey() {
		try {
			return new AuthKey(FileUtils.readFileToByteArray(Config.FILE_AUTH_KEY));
		} catch (IOException e) {
			if (!(e instanceof FileNotFoundException)) e.printStackTrace();
		}
		
		return null;
	}
	
	public void saveDc(DataCenter dc) {
		try {
			FileUtils.write(Config.FILE_DC, dc.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public DataCenter loadDc() {
		try {
			String[] infos = FileUtils.readFileToString(Config.FILE_DC).split(":");
			return new DataCenter(infos[0], Integer.parseInt(infos[1]));
		} catch (IOException e) {
			if (!(e instanceof FileNotFoundException)) e.printStackTrace();
		}
		return null;
	}
	
	public void deleteAuthKey() {
		try {
			FileUtils.forceDelete(Config.FILE_AUTH_KEY);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void deleteDc() {
		try {
			FileUtils.forceDelete(Config.FILE_DC);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void saveServerSalt(long salt) {}
	
	public Long loadServerSalt() { return null; }
}
