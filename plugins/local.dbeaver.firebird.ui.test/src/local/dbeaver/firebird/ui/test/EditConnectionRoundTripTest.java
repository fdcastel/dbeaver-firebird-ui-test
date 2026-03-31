package local.dbeaver.firebird.ui.test;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swt.widgets.Display;
import org.junit.BeforeClass;
import org.junit.Test;

import local.dbeaver.ui.test.support.ScreenshotUtil;

/**
 * L1.2: Edit Connection Round-Trip Test
 *
 * Verifies that the connection settings persisted by the API can be read back
 * via the DBeaver API (without opening the Edit Connection dialog, which has
 * complex multi-page navigation). This validates the data-source persistence.
 */
public class EditConnectionRoundTripTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = FirebirdTestContext.getBot();
        assertNotNull("Connection must exist from previous test",
                FirebirdTestContext.getConnectionName());
    }

    @Test
    public void testConnectionSettingsRoundTrip() {
        final String[] values = new String[5];
        Display.getDefault().syncExec(() -> {
            try {
                var project = org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform()
                        .getWorkspace().getActiveProject();
                var registry = project.getDataSourceRegistry();
                for (var ds : registry.getDataSources()) {
                    if (ds.getName().contains(FirebirdTestContext.getConnectionName())) {
                        var config = ds.getConnectionConfiguration();
                        values[0] = config.getHostName();
                        values[1] = config.getHostPort();
                        values[2] = config.getDatabaseName();
                        values[3] = config.getUserName();
                        values[4] = ds.getDriver().getName();
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        });

        assertEquals("Host should match", FirebirdTestContext.getHost(), values[0]);
        assertEquals("Port should match", FirebirdTestContext.getPort(), values[1]);
        assertEquals("Database should match", FirebirdTestContext.getDatabase(), values[2]);
        assertEquals("User should match", FirebirdTestContext.getUser(), values[3]);
        assertTrue("Driver should be Firebird", values[4].toLowerCase().contains("firebird"));
    }
}
