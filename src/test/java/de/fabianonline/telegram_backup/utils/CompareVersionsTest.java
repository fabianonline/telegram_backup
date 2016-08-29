import static org.junit.Assert.*;
import org.junit.Test;
import de.fabianonline.telegram_backup.Utils;

public class CompareVersionsTest {
	@Test
	public void tests() {
		assertEquals(Utils.compareVersions("1.0.3", "1.0.4"), Utils.VERSION_2_NEWER);
		assertEquals(Utils.compareVersions("1.0.4", "1.0.3"), Utils.VERSION_1_NEWER);
		assertEquals(Utils.compareVersions("1.0.4", "1.0.4.1"), Utils.VERSION_2_NEWER);
		assertEquals(Utils.compareVersions("1.0.4", "1.0.4-pre.1"), Utils.VERSION_1_NEWER);
		assertEquals(Utils.compareVersions("1.0.4", "1.0.4"), Utils.VERSIONS_EQUAL);
		assertEquals(Utils.compareVersions("1.0.4-pre.2", "1.0.4-pre.1"), Utils.VERSIONS_EQUAL);
	}
}
