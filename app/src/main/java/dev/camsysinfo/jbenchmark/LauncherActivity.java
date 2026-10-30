package dev.camsysinfo.jbenchmark;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/**
 * Launches the baked-in JBenchmark 1.1.1 MIDlet directly — no file picker,
 * no settings screen. The original JBenchmark.JBenchmark class (dexed
 * unmodified from the shipped jar) is instantiated and startApp() is
 * called; from then on the emulator host pumps it.
 *
 * Safety: black window background (no white screen possible at any stage),
 * hardware acceleration off (no GL), all failures rendered as on-screen
 * error text, uncaught exceptions on ANY thread shown on screen.
 */
public class LauncherActivity extends Activity {

    private EmuView view;
    private Host host;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        view = new EmuView(this);
        setContentView(view);

        host = new Host(this, view);
        Host.install(host);
        view.setHost(host);

        // any thread's crash becomes on-screen text, never a silent white screen
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    final String msg = Host.describe(e);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (view != null) view.showError(msg);
                        }
                    });
                } catch (Throwable ignored) {
                    // last resort: nothing more we can do
                }
            }
        });

        initSystemProperties();

        try {
            // The MIDlet class comes straight from the baked-in jar:
            // JBenchmark/JBenchmark.class, dexed unmodified by d8.
            JBenchmark.JBenchmark midlet = new JBenchmark.JBenchmark();
            host.setMidlet(midlet);
            midlet.startApp();
        } catch (Throwable t) {
            view.showError(Host.describe(t));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) view.requestFocus();
    }

    @Override
    protected void onDestroy() {
        if (host != null) host.destroy();
        Host.install(null);
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (host != null && host.handleKey(keyCode)) {
            return true;
        }
        // never let stray BACK keys finish the activity behind our back
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** CLDC system properties for the About/results screens. */
    private static void initSystemProperties() {
        try {
            System.setProperty("microedition.configuration", "CLDC-1.0");
            System.setProperty("microedition.profile", "MIDP-1.0");
            System.setProperty("microedition.locale", "en");
            System.setProperty("microedition.encoding", "UTF-8");
            System.setProperty("microedition.platform", "Android-16");
            System.setProperty("microedition.profiles", "MIDP-1.0");
        } catch (Throwable ignored) {
            // security manager (never on device) — About shows nulls
        }
    }
}
