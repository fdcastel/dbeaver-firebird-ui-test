package local.dbeaver.firebird.ui.test;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import local.dbeaver.ui.test.support.ConnectionUtil;
import local.dbeaver.ui.test.support.SQLEditorUtil;
import local.dbeaver.ui.test.support.ScreenshotUtil;
import local.dbeaver.ui.test.support.WorkbenchUtil;

/**
 * L1.3: SQL Editor SET TERM Test
 *
 * Verifies that the Firebird SQL editor properly handles SET TERM for
 * stored procedure / trigger blocks that use alternate terminators.
 */
public class SQLEditorSetTermTest {

    private static SWTWorkbenchBot bot;
    private static final String SET_TERM_SCRIPT =
            "SET TERM !! ;\n" +
            "CREATE OR ALTER PROCEDURE SP_UITEST_SETTERM\n" +
            "RETURNS (RESULT VARCHAR(50))\n" +
            "AS\n" +
            "BEGIN\n" +
            "  RESULT = 'SET_TERM_OK';\n" +
            "  SUSPEND;\n" +
            "END !!\n" +
            "SET TERM ; !!\n";

    @BeforeClass
    public static void setUp() {
        bot = FirebirdTestContext.getBot();
        assertNotNull("Connection must exist from previous test",
                FirebirdTestContext.getConnectionName());

        try {
            ConnectionUtil.connectTo(bot, FirebirdTestContext.getConnectionName());
        } catch (Exception e) {
            // might already be connected
        }
    }

    @Test
    public void testSetTermScriptExecutes() {
        try {
            SWTBotEditor editor = SQLEditorUtil.openSQLConsole(bot,
                    FirebirdTestContext.getConnectionName());
            assertNotNull("SQL editor should open", editor);

            SQLEditorUtil.typeSQL(bot, SET_TERM_SCRIPT);
            SQLEditorUtil.executeScript(bot);
            SQLEditorUtil.waitForExecution(bot);

            String error = SQLEditorUtil.checkForErrorDialog(bot);
            assertNull("SET TERM script should execute without errors. Got: " + error, error);

            editor.close();
        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("SQLEditorSetTermTest", "testSetTermScriptExecutes");
            throw e;
        }
    }

    @AfterClass
    public static void tearDown() {
        WorkbenchUtil.closeAllEditors(bot);
    }
}
