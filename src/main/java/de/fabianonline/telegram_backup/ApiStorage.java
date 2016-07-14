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

import com.github.badoualy.telegram.api.TelegramApiStorage;
import com.github.badoualy.telegram.mtproto.model.DataCenter;
import com.github.badoualy.telegram.mtproto.auth.AuthKey;
import com.github.badoualy.telegram.mtproto.model.MTSession;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

class ApiStorage implements TelegramApiStorage {
	private String prefix = null;
	private boolean do_save = false;
	private AuthKey auth_key = null;
	private DataCenter dc = null;
	private File file_auth_key = null;
	private File file_dc = null;
	
	public ApiStorage(String prefix) {
		this.setPrefix(prefix);
	}
	
	public void setPrefix(String prefix) {
		this.prefix = prefix;
		this.do_save = (this.prefix!=null);
		if (this.do_save) {
			String base = Config.FILE_BASE +
				File.separatorChar +
				this.prefix +
				File.separatorChar;
			this.file_auth_key = new File(base + Config.FILE_NAME_AUTH_KEY);
			this.file_dc = new File(base + Config.FILE_NAME_DC);
			this._saveAuthKey();
			this._saveDc();
		} else {
			this.file_auth_key = null;
			this.file_dc = null;
		}
	}
	
	public void saveAuthKey(AuthKey authKey) {
		this.auth_key = authKey;
		this._saveAuthKey();
	}
	
	private void _saveAuthKey() {
		if (this.do_save && this.auth_key!=null) {
			try {
				FileUtils.writeByteArrayToFile(this.file_auth_key, this.auth_key.getKey());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public AuthKey loadAuthKey() {
		if (this.auth_key != null) return this.auth_key;
		if (this.file_auth_key != null) {
			try {
				return new AuthKey(FileUtils.readFileToByteArray(this.file_auth_key));
			} catch (IOException e) {
				if (!(e instanceof FileNotFoundException)) e.printStackTrace();
			}
		}
		
		return null;
	}
	
	public void saveDc(DataCenter dc) {
		this.dc = dc;
		this._saveDc();
	}
	
	private void _saveDc() {
		if (this.do_save && this.dc != null) {
			try {
				FileUtils.write(this.file_dc, this.dc.toString());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public DataCenter loadDc() {
		if (this.dc != null) return this.dc;
		if (this.file_dc != null) {
			try {
				String[] infos = FileUtils.readFileToString(this.file_dc).split(":");
				return new DataCenter(infos[0], Integer.parseInt(infos[1]));
			} catch (IOException e) {
				if (!(e instanceof FileNotFoundException)) e.printStackTrace();
			}
		}
		return null;
	}
	
	public void deleteAuthKey() {
		if (this.do_save) {
			try {
				FileUtils.forceDelete(this.file_auth_key);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void deleteDc() {
		if (this.do_save) {
			try {
				FileUtils.forceDelete(this.file_dc);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void saveSession(MTSession session) {
	}
	
	public MTSession loadSession() { return null; }
}
