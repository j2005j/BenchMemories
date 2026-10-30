package javax.microedition.lcdui;

import dev.camsysinfo.jbenchmark.Host;

/**
 * MIDP Display singleton. callSerially posts to the host's UI Handler —
 * that is the pump for JBenchmark's self-rescheduling run() loop.
 */
public final class Display {

    public static final int COLOR_1BIT = 1;
    public static final int COLOR_4BIT = 4;
    public static final int COLOR_8BIT = 8;
    public static final int COLOR_16BIT = 16;

    public static final int COLOR_STATIC = 0;
    public static final int COLOR_USER = 1;

    private static Display instance;

    private javax.microedition.midlet.MIDlet midlet;
    private Displayable current;

    private Display(javax.microedition.midlet.MIDlet m) {
        this.midlet = m;
    }

    public static Display getDisplay(javax.microedition.midlet.MIDlet m) {
        if (instance == null) {
            instance = new Display(m);
        } else {
            // app relaunched: rebind to the fresh MIDlet
            instance.midlet = m;
        }
        return instance;
    }

    public void setCurrent(Displayable d) {
        current = d;
        Host h = Host.get();
        if (h != null) {
            h.onDisplayChanged(d);
        }
    }

    public Displayable getCurrent() {
        return current;
    }

    public javax.microedition.midlet.MIDlet getCurrentMidlet() {
        return midlet;
    }

    public void callSerially(Runnable r) {
        Host h = Host.get();
        if (h != null) {
            h.postSerially(r);
        }
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 65536;
    }

    public int numAlphaLevels() {
        return 256;
    }

    public int getColor(int colorComponent) {
        switch (colorComponent) {
            case COLOR_RED:   return 0xFF;
            case COLOR_GREEN: return 0xFF;
            case COLOR_BLUE:  return 0xFF;
            default: throw new IllegalArgumentException();
        }
    }

    public static final int COLOR_RED = 0;
    public static final int COLOR_GREEN = 1;
    public static final int COLOR_BLUE = 2;

    public boolean isVibratable() {
        return false;
    }

    public void vibrate(int duration) {
        // no vibrator path on this camera; no-op
    }

    public void beep(int count) {
        // no audio path; no-op
    }

    /** Flashing not supported; JBenchmark checks it only for display info. */
    public int getFlashIntensity() {
        return -1;
    }
}
