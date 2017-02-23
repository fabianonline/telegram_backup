package de.fabianonline.telegram_backup.models;

import de.fabianonline.telegram_backup.Database;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

public class Message {
	protected static String tableName = "messages";
	private JsonObject json;
	private String message = null;
	
	public Message(String json) {
		this.json = new JsonParser().parse(json).getAsJsonObject();
	}
	
	public static Message get(int id) {
		String json = Database.getInstance().queryString("SELECT json FROM " + tableName + " WHERE id=" + id);
		return new Message(json);
	}
	
	public String getMessage() {
		if (message != null) return message;
		return message = json.getAsJsonPrimitive("message").getAsString();
	}
}
