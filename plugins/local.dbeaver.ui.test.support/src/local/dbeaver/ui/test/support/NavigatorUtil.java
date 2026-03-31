package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

public class NavigatorUtil {

    /**
     * Returns the Database Navigator tree widget.
     */
    public static SWTBotTree getDatabaseNavigatorTree(SWTWorkbenchBot bot) {
        try {
            return bot.viewByTitle("Database Navigator").bot().tree();
        } catch (WidgetNotFoundException e) {
            return bot.viewById("org.jkiss.dbeaver.core.databaseNavigator").bot().tree();
        }
    }

    /**
     * Finds a connection node in the navigator tree by partial name match.
     */
    public static SWTBotTreeItem findConnectionNode(SWTBotTree tree, String connectionName) {
        for (SWTBotTreeItem item : tree.getAllItems()) {
            if (item.getText().contains(connectionName)) {
                return item;
            }
        }
        throw new WidgetNotFoundException("Connection '" + connectionName + "' not found in navigator");
    }

    /**
     * Expands a path in the navigator tree, waiting briefly for children to load.
     */
    public static SWTBotTreeItem expandPath(SWTBotTree tree, String... path) {
        SWTBotTreeItem current = null;
        for (int i = 0; i < path.length; i++) {
            SWTBotTreeItem[] items = (i == 0) ? tree.getAllItems() : current.getItems();
            current = findItemByPartialText(items, path[i]);
            current.expand();
            sleep(300);
        }
        return current;
    }

    /**
     * Refreshes a node via context menu.
     */
    public static void refreshNode(SWTWorkbenchBot bot, SWTBotTreeItem node) {
        node.select();
        node.contextMenu("Refresh").click();
        sleep(500);
    }

    private static SWTBotTreeItem findItemByPartialText(SWTBotTreeItem[] items, String text) {
        for (SWTBotTreeItem item : items) {
            if (item.getText().contains(text)) {
                return item;
            }
        }
        throw new WidgetNotFoundException("Tree item containing '" + text + "' not found");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
