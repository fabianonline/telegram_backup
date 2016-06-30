package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.api.auth.TLAbsSentCode;
import com.github.badoualy.telegram.tl.api.auth.TLAuthorization;
import com.github.badoualy.telegram.tl.api.TLUser;
import com.github.badoualy.telegram.tl.api.TLUserFull;
import com.github.badoualy.telegram.tl.api.TLInputUserSelf;
import com.github.badoualy.telegram.tl.api.account.TLPassword;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
import com.github.badoualy.telegram.tl.core.TLBytes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;

class UserManager {
	public TLUser user = null;
	public String phone = null;
	private String code = null;
	private TelegramClient client = null;
	private TLAbsSentCode sent_code = null;
	private TLAuthorization auth = null;
	private boolean password_needed = false;
	
	public UserManager(TelegramClient c) throws IOException {
		this.client = c;
		try {
			TLUserFull full_user = this.client.usersGetFullUser(new TLInputUserSelf());
			this.user = full_user.getUser().getAsUser();
		} catch (Exception e) {
			// This may happen. Ignoring it.
		}
	}
	
	public boolean isLoggedIn() { return user!=null; }
	
	public void sendCodeToPhoneNumber(String number) throws RpcErrorException, IOException {
		this.phone = number;
		this.sent_code = this.client.authSendCode(this.phone, 5);
	}
	
	public void verifyCode(String code) throws RpcErrorException, IOException {
		this.code = code;
		try {
			this.auth = client.authSignIn(phone, this.sent_code.getPhoneCodeHash(), this.code);
			this.user = auth.getUser().getAsUser();
		} catch (RpcErrorException e) {
			if (!e.getTag().equals("401: SESSION_PASSWORD_NEEDED")) throw e;
			this.password_needed = true;
		}
	}
	
	public boolean isPasswordNeeded() { return this.password_needed; }
	
	public void verifyPassword(String pw) throws RpcErrorException, IOException {
		byte[] password = pw.getBytes("UTF-8");
		byte[] salt = ((TLPassword)client.executeRpcQuery(new TLRequestAccountGetPasswordWithCurrentSalt())).getCurrentSalt().getData();
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return;
		}
		byte[] salted = new byte[2*salt.length + password.length];
		System.arraycopy(salt,     0, salted, 0,                           salt.length);
		System.arraycopy(password, 0, salted, salt.length,                 password.length);
		System.arraycopy(salt,     0, salted, salt.length+password.length, salt.length);
		byte[] hash = md.digest(salted);
		auth = client.authCheckPassword(new TLBytes(hash));
		this.user = auth.getUser().getAsUser();
	}
	
	public String getUserString() {
		if (this.user==null) return "Not logged in";
		StringBuilder sb = new StringBuilder();
		if (this.user.getFirstName()!=null) {
			sb.append(this.user.getFirstName());
		}
		if (this.user.getLastName()!=null) {
			sb.append(" ");
			sb.append(this.user.getLastName());
		}
		if (this.user.getUsername()!=null) {
			sb.append(" (@");
			sb.append(this.user.getUsername());
			sb.append(")");
		}
		return sb.toString();
	}
}
