package dev.camsysinfo.jbenchmark;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;

/**
 * Emulator host: owns the 240x320 back buffer, the UI-thread pump that MIDP
 * Display.callSerially maps to, keyboard/command dispatch, the baked jar
 * bytes + manifest, and the run-loop throttle that keeps the camera's CPU
 * sane after the benchmark finishes.
 *
 * Render path: Canvas.repaint() -> repaintCurrent() paints synchronously
 * into the buffer (one paint per repaint, no vsync frame dropping = the
 * "Limit FPS: 99999999" requirement), then postInvalidate() presents it
 * nearest-neighbor scaled ("Fast (no filter)").
 *
 * Loop model (verified against JBenchmark bytecode): b.run() does
 * repaint() + Display.callSerially(this) unconditionally, then checks the
 * per-test elapsed time (>10000ms advances the test). The chain is the
 * benchmark: each iteration paints exactly once, as fast as the device
 * allows. After test 5 (public field b:I on class b reaches 5) the chain
 * would hot-loop forever, so postSerially throttles it to 100ms ticks —
 * Restart/Back set b:I back to 0 and immediately restore full speed.
 */
public final class Host {

    public static final int WIDTH = 240;
    public static final int HEIGHT = 320;
    private static final int POST_FINISH_DELAY_MS = 100;

    private static volatile Host instance;

    public static Host get() {
        return instance;
    }

    public static void install(Host h) {
        instance = h;
    }

    private final Activity activity;
    private final EmuView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Bitmap buffer;
    private final Graphics graphics;

    private volatile Displayable current;
    private volatile int selectedCommand;
    private volatile int lastKey;
    private volatile long lastKeyTime;
    private volatile boolean destroyed;
    private volatile Throwable renderError;

    private JBenchmark.JBenchmark midlet;
    private byte[] jarBytes;      // guarded by this
    private Map<String, String> manifest; // guarded by this

    // reflection cache: int field "b" (test index) on class "b"
    private static Field testIndexField;

    Host(Activity activity, EmuView view) {
        this.activity = activity;
        this.view = view;
        this.buffer = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        this.graphics = Graphics.create(buffer);
    }

    public Bitmap getBuffer() {
        return buffer;
    }

    public void setMidlet(JBenchmark.JBenchmark m) {
        this.midlet = m;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Synchronous repaint: paints the current displayable into the buffer. */
    public void repaintCurrent() {
        if (destroyed) return;
        Displayable d = current;
        synchronized (this) {
            graphics.resetState(buffer);
            if (d != null) {
                try {
                    d.renderTo(graphics);
                } catch (Throwable t) {
                    renderError = t;
                }
            } else {
                graphics.setColor(0, 0, 0);
                graphics.fillRect(0, 0, WIDTH, HEIGHT);
            }
        }
        Throwable err = renderError;
        if (err != null) {
            view.showError(describe(err));
            return;
        }
        view.postInvalidate();
    }

    /** Called by Display.setCurrent. */
    public void onDisplayChanged(Displayable d) {
        current = d;
        selectedCommand = 0;
        repaintCurrent();
    }

    public Displayable getCurrentDisplayable() {
        return current;
    }

    // ------------------------------------------------------------------
    // Pump (MIDP Display.callSerially)
    // ------------------------------------------------------------------

    public void postSerially(Runnable r) {
        if (destroyed || r == null) return;
        if (isBenchmarkFinished(r)) {
            handler.postDelayed(r, POST_FINISH_DELAY_MS);
        } else {
            handler.post(r);
        }
    }

    /**
     * The benchmark loop must keep the UI thread hot while a test is
     * running, but must not peg the camera after test 5. Class b's public
     * int field "b" is the test index: >=5 means finished/idle.
     */
    private static boolean isBenchmarkFinished(Runnable r) {
        Class<?> c = r.getClass();
        if (!"b".equals(c.getName())) return false;
        try {
            Field f = testIndexField;
            if (f == null) {
                Field[] publics = c.getFields();
                for (int i = 0; i < publics.length; i++) {
                    if (publics[i].getType() == int.class && "b".equals(publics[i].getName())) {
                        f = publics[i];
                        break;
                    }
                }
                testIndexField = f;
            }
            return f != null && f.getInt(r) >= 5;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Keys / commands
    // ------------------------------------------------------------------

    public void noteKey(int keyCode) {
        lastKey = keyCode;
        lastKeyTime = android.os.SystemClock.uptimeMillis();
    }

    public int getLastKey() {
        return lastKey;
    }

    public boolean lastKeyWithin(long ms) {
        return lastKeyTime != 0 && android.os.SystemClock.uptimeMillis() - lastKeyTime < ms;
    }

    public int getSelectedCommand() {
        return selectedCommand;
    }

    /**
     * Camera has no touch screen. Mapping (also shown in the form's hint
     * line and the transient key debug overlay):
     *   MENU        = exit app          (project requirement)
     *   DPAD UP/DN  = select command
     *   DPAD L/R    = scroll form items
     *   CENTER/OK   = fire selected command
     *   BACK        = fire BACK-type command if present
     * Returns true when the key was consumed.
     */
    public boolean handleKey(int keyCode) {
        noteKey(keyCode);
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            exitApp();
            return true;
        }
        if (destroyed) return true;
        Displayable d = current;
        if (d == null) return false;
        boolean isForm = d instanceof Form;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (isForm) { cycleCommand(-1); return true; }
                return false;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (isForm) { cycleCommand(1); return true; }
                return false;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (isForm) { scrollCurrent(-24); return true; }
                return false;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isForm) { scrollCurrent(24); return true; }
                return false;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (isForm && !d.getCommands().isEmpty()) {
                    fireSelectedCommand();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_BACK: {
                Command back = findByType(d, javax.microedition.lcdui.Command.BACK);
                if (back != null) {
                    selectCommand(back);
                    fireSelectedCommand();
                    return true;
                }
                return false;
            }
            default:
                return false;
        }
    }

    private void cycleCommand(int delta) {
        Displayable d = current;
        if (d == null) return;
        List<Command> cmds = d.getCommands();
        if (cmds.isEmpty()) return;
        int n = cmds.size();
        int sel = ((selectedCommand + delta) % n + n) % n;
        selectedCommand = sel;
        repaintCurrent();
    }

    private void selectCommand(Command c) {
        Displayable d = current;
        if (d == null) return;
        List<Command> cmds = d.getCommands();
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i) == c) {
                selectedCommand = i;
                return;
            }
        }
    }

    /** Fires the selected command through the MIDP listener (reference
     *  identity preserved — JBenchmark matches commands with ==). */
    public void fireSelectedCommand() {
        Displayable d = current;
        if (d == null) return;
        CommandListener l = d.getCommandListener();
        if (l == null) return;
        List<Command> cmds = d.getCommands();
        if (cmds.isEmpty()) return;
        int sel = selectedCommand;
        if (sel < 0 || sel >= cmds.size()) sel = 0;
        try {
            l.commandAction(cmds.get(sel), d);
        } catch (Throwable t) {
            view.showError(describe(t));
        }
    }

    private void scrollCurrent(int delta) {
        Displayable d = current;
        if (d instanceof Form) {
            ((Form) d).scrollBy(delta);
            repaintCurrent();
        }
    }

    private static Command findByType(Displayable d, int type) {
        List<Command> cmds = d.getCommands();
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i).getCommandType() == type) return cmds.get(i);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void exitApp() {
        if (destroyed) return;
        JBenchmark.JBenchmark m = midlet;
        if (m != null) {
            try {
                m.exitMIDlet(); // destroyApp(true) + notifyDestroyed()
                return;
            } catch (Throwable t) {
                // fall through
            }
        }
        notifyDestroyed();
    }

    /** MIDlet.notifyDestroyed() lands here. */
    public void notifyDestroyed() {
        if (destroyed) return;
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        activity.runOnUiThread(new Runnable() {
            public void run() {
                activity.finish();
            }
        });
    }

    /** Belt and braces from the activity. */
    public void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    // ------------------------------------------------------------------
    // Baked jar + manifest
    // ------------------------------------------------------------------

    public synchronized byte[] getJarBytes() {
        if (jarBytes == null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(32 * 1024);
            try {
                InputStream in = activity.getAssets().open("JBenchmark.jar");
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                } finally {
                    in.close();
                }
                jarBytes = bos.toByteArray();
            } catch (Throwable t) {
                jarBytes = new byte[0];
            }
        }
        return jarBytes;
    }

    public synchronized String getAppProperty(String key) {
        if (manifest == null) {
            manifest = new HashMap<String, String>();
            try {
                ZipInputStream zin = new ZipInputStream(
                        new java.io.ByteArrayInputStream(getJarBytes()));
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if ("META-INF/MANIFEST.MF".equals(e.getName())) {
                        parseManifest(zin, manifest);
                        break;
                    }
                }
                zin.close();
            } catch (Throwable t) {
                // leave empty
            }
        }
        return manifest.get(key);
    }

    private static void parseManifest(InputStream in, Map<String, String> out)
            throws java.io.IOException {
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, "UTF-8"));
        String line;
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        while ((line = r.readLine()) != null) {
            if (line.startsWith(" ") && currentKey != null) {
                // continuation line (72-byte wrap)
                currentValue.append(line.substring(1));
                continue;
            }
            if (currentKey != null) {
                out.put(currentKey, currentValue.toString());
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                currentKey = null;
                continue;
            }
            currentKey = line.substring(0, colon);
            currentValue.setLength(0);
            if (colon + 1 < line.length() && line.charAt(colon + 1) == ' ') {
                currentValue.append(line.substring(colon + 2));
            }
        }
        if (currentKey != null) {
            out.put(currentKey, currentValue.toString());
        }
    }

    // ------------------------------------------------------------------

    static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName());
        String m = t.getMessage();
        if (m != null && m.length() > 0) sb.append(": ").append(m);
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < st.length && i < 6; i++) {
            sb.append('\n').append("  at ").append(st[i].toString());
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            sb.append("\nCaused by: ").append(cause.getClass().getName());
            String cm = cause.getMessage();
            if (cm != null) sb.append(": ").append(cm);
        }
        return sb.toString();
    }
}
