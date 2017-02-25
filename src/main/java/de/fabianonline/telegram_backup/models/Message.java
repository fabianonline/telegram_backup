package de.fabianonline.telegram_backup.models;

import de.fabianonline.telegram_backup.Database;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

public class Message {
	protected static String tableName = "messages";
	private JsonObject json;
	private JsonObject media;
	private String message = null;
	private Integer id = null;

	public Message(String json) {
		this.json = new JsonParser().parse(json).getAsJsonObject();
	}

	public static Message get(int id) {
		String json = Database.getInstance().queryString("SELECT json FROM " + tableName + " WHERE id=" + id);
		return new Message(json);
	}

	public String getMessage() {
		if (message==null) message=json.getAsJsonPrimitive("message").getAsString();
		return message;
	}

	public int getId() {
		if (id==null) id=json.getAsJsonPrimitive("id").getAsInt();
		return id;
	}

	public JsonObject getMedia() {
		if (media==null) media=json.getAsJsonObject("media");
		return media;
	}
}
