package local.dbeaver.firebird.ui.test;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import local.dbeaver.ui.test.support.WorkbenchUtil;

/**
 * Shared state across all Firebird UI tests.
 * Initialized once by the suite runner.
 */
public class FirebirdTestContext {
    private static SWTWorkbenchBot bot;
    private static String connectionName;
    private static boolean workbenchReady;

    public static synchronized SWTWorkbenchBot getBot() {
        if (bot == null) {
            bot = new SWTWorkbenchBot();
            WorkbenchUtil.configureDefaults();
        }
        if (!workbenchReady) {
            WorkbenchUtil.waitForWorkbench(bot);
            WorkbenchUtil.configureDBeaver();
            WorkbenchUtil.dismissInitialDialogs(bot);
            workbenchReady = true;
        }
        return bot;
    }

    public static String getHost() {
        return System.getProperty("firebird.host", "localhost");
    }

    public static String getPort() {
        return System.getProperty("firebird.port", "3050");
    }

    public static String getDatabase() {
        return System.getProperty("firebird.database", "C:/temp/dbeaver/tmp/ui-test/firebird/UI_TEST_FB5.FDB");
    }

    public static String getUser() {
        return System.getProperty("firebird.user", "SYSDBA");
    }

    public static String getPassword() {
        return System.getProperty("firebird.password", "masterkey");
    }

    public static String getConnectionName() {
        return connectionName;
    }

    public static void setConnectionName(String name) {
        connectionName = name;
    }

    public static boolean hasConnection() {
        return connectionName != null;
    }
}
