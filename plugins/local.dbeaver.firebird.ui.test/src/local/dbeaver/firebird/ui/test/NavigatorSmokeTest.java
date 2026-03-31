package local.dbeaver.firebird.ui.test;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

import local.dbeaver.ui.test.support.ConnectionUtil;
import local.dbeaver.ui.test.support.NavigatorUtil;
import local.dbeaver.ui.test.support.ScreenshotUtil;

/**
 * L1.5: Navigator Smoke Test
 *
 * Verifies that Firebird database objects created by fixtures are visible
 * in the Database Navigator and can be opened.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NavigatorSmokeTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = FirebirdTestContext.getBot();
        assertNotNull("Connection must exist from previous test",
                FirebirdTestContext.getConnectionName());

        // Connect to the database (handles driver download dialog)
        ConnectionUtil.connectTo(bot, FirebirdTestContext.getConnectionName());
    }

    @Test
    public void test01_tablesVisibleInNavigator() {
        String connName = FirebirdTestContext.getConnectionName();
        try {
            SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
            SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connName);
            connNode.expand();
            sleep(1000); // wait for lazy-loaded children

            SWTBotTreeItem tablesNode = findChildContaining(connNode, "Tables");
            assertNotNull("Tables node should exist under connection", tablesNode);
            tablesNode.expand();
            waitForChildren(tablesNode, 10000);

            SWTBotTreeItem[] tables = tablesNode.getItems();
            assertTrue("At least one table should be visible (got " + tables.length + ")", tables.length > 0);

            boolean found = false;
            for (SWTBotTreeItem table : tables) {
                if (table.getText().toUpperCase().contains("TEST_CUSTOMER")) {
                    found = true;
                    break;
                }
            }
            assertTrue("TEST_CUSTOMER table should be visible in navigator", found);

        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("NavigatorSmokeTest", "test01");
            throw e;
        }
    }

    @Test
    public void test02_viewsVisibleInNavigator() {
        // Use DBeaver navigator model API to check views exist
        // (SWT virtual tree getText returns empty for lazy-loaded items)
        final boolean[] found = { false };
        final String[] diag = { "" };
        org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
            try {
                var project = org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform()
                        .getWorkspace().getActiveProject();
                var registry = project.getDataSourceRegistry();
                for (var ds : registry.getDataSources()) {
                    if (!ds.isConnected()) continue;
                    // Navigate the navigator model tree to find views
                    var navModel = org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform()
                            .getNavigatorModel();
                    var dsNode = navModel.getNodeByObject(ds);
                    if (dsNode == null) continue;
                    findViewsRecursive(dsNode, found, diag);
                }
            } catch (Exception e) {
                diag[0] += "Error: " + e.getMessage();
            }
        });
        assertTrue("V_TEST_CUSTOMER_ORDERS view should exist via navigator API", found[0]);
    }

    private static void findViewsRecursive(
            org.jkiss.dbeaver.model.navigator.DBNNode node,
            boolean[] found, String[] diag) {
        try {
            var children = node.getChildren(
                    new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor());
            if (children == null) return;
            for (var child : children) {
                String name = child.getName();
                if (name != null && name.toUpperCase().contains("V_TEST_CUSTOMER_ORDERS")) {
                    found[0] = true;
                    return;
                }
                // Recurse into Views folder or schema nodes
                if (name != null && (name.equalsIgnoreCase("Views")
                        || child instanceof org.jkiss.dbeaver.model.navigator.DBNDatabaseFolder)) {
                    findViewsRecursive(child, found, diag);
                    if (found[0]) return;
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void test03_proceduresVisibleInNavigator() {
        String connName = FirebirdTestContext.getConnectionName();
        try {
            SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
            SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connName);
            connNode.expand();
            sleep(500);

            SWTBotTreeItem procsNode = findChildContaining(connNode, "Procedures");
            if (procsNode != null) {
                procsNode.expand();
                waitForChildren(procsNode, 10000);
                boolean found = false;
                for (SWTBotTreeItem proc : procsNode.getItems()) {
                    if (proc.getText().toUpperCase().contains("SP_UITEST_SETTERM")) {
                        found = true;
                        break;
                    }
                }
                assertTrue("SP_UITEST_SETTERM procedure should be visible", found);
            }
        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("NavigatorSmokeTest", "test03");
            throw e;
        }
    }

    @Test
    public void test04_doubleClickOpensTable() {
        String connName = FirebirdTestContext.getConnectionName();
        try {
            SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
            SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connName);
            connNode.expand();
            sleep(500);

            SWTBotTreeItem tablesNode = findChildContaining(connNode, "Tables");
            assertNotNull("Tables node should exist", tablesNode);
            tablesNode.expand();
            waitForChildren(tablesNode, 10000);

            SWTBotTreeItem customerTable = null;
            for (SWTBotTreeItem table : tablesNode.getItems()) {
                if (table.getText().toUpperCase().contains("TEST_CUSTOMER")) {
                    customerTable = table;
                    break;
                }
            }
            assertNotNull("TEST_CUSTOMER table should exist", customerTable);
            customerTable.doubleClick();
            sleep(1000);

            assertNotNull("An editor should open when double-clicking a table",
                    bot.activeEditor());

        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("NavigatorSmokeTest", "test04");
            throw e;
        }
    }

    private static SWTBotTreeItem findChildContaining(SWTBotTreeItem parent, String text) {
        for (SWTBotTreeItem child : parent.getItems()) {
            if (child.getText().contains(text)) {
                return child;
            }
        }
        return null;
    }

    /** Waits for a tree item to have at least one child with non-empty text. */
    private static void waitForChildren(SWTBotTreeItem item, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                SWTBotTreeItem[] children = item.getItems();
                if (children.length > 0 && !children[0].getText().isEmpty()) {
                    return;
                }
            } catch (Exception e) { /* ignore */ }
            sleep(300);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
