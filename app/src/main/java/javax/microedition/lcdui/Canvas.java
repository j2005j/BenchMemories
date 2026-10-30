package javax.microedition.lcdui;

import dev.camsysinfo.jbenchmark.Host;

/**
 * MIDP Canvas backed by the emulator host. JBenchmark's b class extends
 * this and overrides paint(). repaint() is SYNCHRONOUS: it paints the
 * frame immediately on the calling thread (which is the UI thread, because
 * JBenchmark's run() loop arrives via Display.callSerially -> Handler).
 * That gives every repaint exactly one paint — no vsync frame-dropping —
 * which is what "Limit FPS: 99999999" wants: raw, uncapped throughput.
 */
public abstract class Canvas extends Displayable {

    protected Canvas() {
    }

    public abstract void paint(Graphics g);

    /** MIDP double buffering is handled by the host back buffer. */
    public boolean isDoubleBuffered() {
        return true;
    }

    public void repaint() {
        Host h = Host.get();
        if (h != null) {
            h.repaintCurrent();
        }
    }

    @Override
    public void renderTo(Graphics g) {
        paint(g);
    }

    protected void keyPressed(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }
}
