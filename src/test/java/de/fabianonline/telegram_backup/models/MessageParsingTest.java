import static org.junit.Assert.*;
import org.junit.Test;
import de.fabianonline.telegram_backup.models.Message;
import de.fabianonline.telegram_backup.test.models.TestJSON;

public class MessageParsingTest {
	@Test
	public void testGetMessage() {
		assertEquals(
			"Hey cool, i will test it at weekend",
			new Message(TestJSON.layer53message).getMessage());
		assertEquals(
			"Nur mal so als Info, was wir bisher haben.",
			new Message(TestJSON.layer51message).getMessage());
	}

	@Test
	public void testGetId() {
		assertEquals(15200, new Message(TestJSON.layer51message).getId());
		assertEquals(39402, new Message(TestJSON.layer53message).getId());
	}
}
