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
 * L1.4: EXECUTE BLOCK Parameter Binding Test
 *
 * Verifies that DBeaver correctly handles parameter binding for Firebird's
 * EXECUTE BLOCK statements.
 *
 * Expected behavior:
 * - FAIL on 'devel' branch (text substitution breaks Firebird DSQL parser)
 * - PASS on 'fix/firebird-execute-block-params' branch (native binding)
 */
public class ExecuteBlockParameterBindingTest {

    private static SWTWorkbenchBot bot;

    private static final String EXECUTE_BLOCK_SQL =
            "EXECUTE BLOCK (x DOUBLE PRECISION = ?, y DOUBLE PRECISION = ?)\n" +
            "RETURNS (gmean DOUBLE PRECISION)\n" +
            "AS\n" +
            "BEGIN\n" +
            "  gmean = SQRT(x * y);\n" +
            "  SUSPEND;\n" +
            "END";

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
    public void testExecuteBlockWithParameters() {
        try {
            SWTBotEditor editor = SQLEditorUtil.openSQLConsole(bot,
                    FirebirdTestContext.getConnectionName());
            assertNotNull("SQL editor should open", editor);

            SQLEditorUtil.typeSQL(bot, EXECUTE_BLOCK_SQL);
            SQLEditorUtil.executeStatement(bot);

            // Wait for parameter dialog
            sleep(500);
            assertTrue("Parameter binding dialog should appear",
                    SQLEditorUtil.isParameterDialogOpen(bot));

            // Fill parameter values: x=3, y=4 → gmean ≈ 3.4641
            SQLEditorUtil.fillParameterDialog(bot, "3", "4");

            SQLEditorUtil.waitForExecution(bot);

            // On 'devel' branch this FAILS because Firebird's DSQL parser rejects text-substituted query.
            // On 'fix/firebird-execute-block-params' branch, native binding succeeds.
            String error = SQLEditorUtil.checkForErrorDialog(bot);
            assertNull("EXECUTE BLOCK should execute without errors " +
                    "(fails on 'devel', passes on 'fix/firebird-execute-block-params'). " +
                    "Error: " + error, error);

            editor.close();
        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("ExecuteBlockParameterBindingTest",
                    "testExecuteBlockWithParameters");
            throw e;
        }
    }

    @AfterClass
    public static void tearDown() {
        SQLEditorUtil.dismissErrorDialogs(bot);
        WorkbenchUtil.closeAllEditors(bot);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
