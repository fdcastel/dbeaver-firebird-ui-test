package local.dbeaver.firebird.ui.test;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import local.dbeaver.ui.test.support.ConnectionUtil;
import local.dbeaver.ui.test.support.NavigatorUtil;
import local.dbeaver.ui.test.support.ScreenshotUtil;

/**
 * L1.1: Firebird Connection Wizard Test
 *
 * Tests:
 *  01 - Verify the wizard opens and the Firebird driver can be selected
 *  02 - Create a Firebird connection via API (fast) for use by subsequent tests
 *  03 - Verify the connection appears in the Database Navigator
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FirebirdConnectionWizardTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = FirebirdTestContext.getBot();
    }

    @Test
    public void test01_wizardOpensAndShowsFirebirdDriver() {
        SWTBotShell wizardShell = ConnectionUtil.openNewConnectionWizard(bot);
        assertNotNull("Connection wizard should open", wizardShell);
        assertTrue("Wizard shell should be active", wizardShell.isActive());

        SWTBot wizard = wizardShell.bot();
        ConnectionUtil.selectDriver(wizard, "Firebird");

        // Verify Next is enabled (driver was selected)
        assertTrue("Next button should be enabled after selecting Firebird driver",
                wizard.button("Next >").isEnabled());

        wizard.button("Cancel").click();
    }

    @Test
    public void test02_createFirebirdConnection() {
        // Create the connection via DBeaver API — fast, no wizard UI needed
        String connName = ConnectionUtil.createFirebirdConnectionViaAPI(
                FirebirdTestContext.getHost(),
                FirebirdTestContext.getPort(),
                FirebirdTestContext.getDatabase(),
                FirebirdTestContext.getUser(),
                FirebirdTestContext.getPassword());

        assertNotNull("Connection name should be returned", connName);
        FirebirdTestContext.setConnectionName(connName);
    }

    @Test
    public void test03_connectionAppearsInNavigator() {
        String connName = FirebirdTestContext.getConnectionName();
        assertNotNull("Connection should have been created in test02", connName);

        try {
            var tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
            var connNode = NavigatorUtil.findConnectionNode(tree, connName);
            assertNotNull("Connection should be visible in navigator", connNode);
        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("FirebirdConnectionWizardTest", "test03");
            fail("Connection '" + connName + "' not found in navigator: " + e.getMessage());
        }
    }
}
