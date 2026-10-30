package javax.microedition.midlet;

import dev.camsysinfo.jbenchmark.Host;

/**
 * Minimal MIDP-1.0 MIDlet. Only the members JBenchmark 1.1.1 actually
 * references are implemented (verified via bytecode disassembly).
 */
public abstract class MIDlet {

    protected MIDlet() {
    }

    protected abstract void startApp();

    protected abstract void pauseApp();

    protected abstract void destroyApp(boolean unconditional);

    /** JBenchmark calls this when the user chooses Exit. */
    public void notifyDestroyed() {
        Host h = Host.get();
        if (h != null) {
            h.notifyDestroyed();
        }
    }

    /** JBenchmark requests "MIDlet-Version" for its About screen. */
    public String getAppProperty(String key) {
        Host h = Host.get();
        return h == null ? null : h.getAppProperty(key);
    }
}
