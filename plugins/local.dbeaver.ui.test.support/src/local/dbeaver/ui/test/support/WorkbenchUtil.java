package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Workbench utilities for SWTBot tests.
 * Optimized for fast startup — the test workspace is pre-configured
 * with preferences that suppress all startup dialogs.
 */
public class WorkbenchUtil {

    /** Configure SWTBot defaults. Call once before any test interaction. */
    public static void configureDefaults() {
        SWTBotPreferences.TIMEOUT = 10000;
        SWTBotPreferences.PLAYBACK_DELAY = 10;
        SWTBotPreferences.KEYBOARD_LAYOUT = "EN_US";
    }

    /**
     * Sets DBeaver preferences programmatically to suppress dialogs and
     * configure the drivers home to the pre-copied location.
     * Must be called after the workbench is ready but before any driver operations.
     */
    public static void configureDBeaver() {
        Display.getDefault().syncExec(() -> {
            try {
                var platform = org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform();
                var prefs = platform.getPreferenceStore();

                // Point drivers home to pre-copied JARs (sibling of test-workspace)
                String driversDir = System.getProperty("uitest.artifacts.dir");
                if (driversDir == null) {
                    // Fallback: look for drivers next to workspace
                    var wsPath = platform.getWorkspace().getAbsolutePath();
                    var driversPath = wsPath.getParent().resolve("drivers");
                    if (java.nio.file.Files.isDirectory(driversPath)) {
                        driversDir = driversPath.toAbsolutePath().toString();
                    }
                }
                if (driversDir != null) {
                    // Check for drivers subdirectory in artifacts dir
                    var artifactDrivers = java.nio.file.Path.of(driversDir);
                    if (!java.nio.file.Files.isDirectory(artifactDrivers.resolve("maven"))) {
                        // The drivers are at target/drivers, sibling of test-workspace
                        var wsPath = platform.getWorkspace().getAbsolutePath();
                        artifactDrivers = wsPath.getParent().resolve("drivers");
                    }
                    if (java.nio.file.Files.isDirectory(artifactDrivers)) {
                        prefs.setValue("ui.drivers.home", artifactDrivers.toAbsolutePath().toString());
                    }
                }

                // Suppress dialogs
                prefs.setValue("sample.database.canceled", true);
                prefs.setValue("tipOfTheDayInitializer.notFirstRun", true);
                prefs.setValue("ui.auto.update.check", false);
                prefs.setValue("ui.show.tip.of.the.day.on.startup", false);
                prefs.setValue("org.jkiss.dbeaver.core.confirm.driver_download", "always");
                prefs.setValue("navigator.expand.on.connect", true);
            } catch (Exception e) {
                System.err.println("WorkbenchUtil.configureDBeaver failed: " + e.getMessage());
            }
        });
    }

    /**
     * Waits for the DBeaver workbench window to be visible and ready.
     * DBeaver starts in a few seconds on modern hardware — we poll every 200ms
     * and give up after 30 seconds.
     */
    public static void waitForWorkbench(SWTWorkbenchBot bot) {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                final boolean[] ready = { false };
                Display.getDefault().syncExec(() -> {
                    try {
                        IWorkbench wb = PlatformUI.getWorkbench();
                        IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
                        if (window != null && window.getShell() != null
                                && !window.getShell().isDisposed()
                                && window.getShell().isVisible()) {
                            ready[0] = true;
                        }
                    } catch (Exception e) {
                        // not ready yet
                    }
                });
                return ready[0];
            }

            @Override
            public String getFailureMessage() {
                return "DBeaver workbench did not start within 30 seconds";
            }
        }, 30000, 200);

        // Brief settle time for the window to finish layout
        sleep(300);
    }

    /**
     * Dismisses any startup dialogs that may appear.
     * With a properly pre-seeded workspace, this should be a no-op.
     */
    public static void dismissInitialDialogs(SWTWorkbenchBot bot) {
        for (int attempt = 0; attempt < 3; attempt++) {
            boolean dismissed = false;
            try { bot.viewByTitle("Welcome").close(); dismissed = true; }
            catch (WidgetNotFoundException e) { /* no welcome */ }

            for (SWTBotShell shell : bot.shells()) {
                String title = shell.getText().toLowerCase();
                if (title.contains("tip of the day") || title.contains("license")
                        || title.contains("error") || title.contains("warning")
                        || title.contains("welcome") || title.contains("can't connect")) {
                    try {
                        try { shell.bot().button("OK").click(); }
                        catch (WidgetNotFoundException e) { shell.close(); }
                        dismissed = true;
                    } catch (Exception e) { /* ignore */ }
                }
            }
            if (!dismissed) break;
            sleep(200);
        }
    }

    public static void closeAllEditors(SWTWorkbenchBot bot) {
        Display.getDefault().syncExec(() -> {
            IWorkbench wb = PlatformUI.getWorkbench();
            IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
            if (window != null && window.getActivePage() != null) {
                window.getActivePage().closeAllEditors(false);
            }
        });
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
