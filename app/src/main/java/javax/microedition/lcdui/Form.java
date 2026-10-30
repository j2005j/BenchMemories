package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

import dev.camsysinfo.jbenchmark.Host;

/**
 * High-level list screen with our own minimal layout (240x320 buffer):
 * optional title line, item list (scrollable), command soft bar at the
 * bottom. Rendering is done here so the original MIDlet code never needs
 * to know about Android.
 *
 * Key mapping (camera has no touch screen):
 *   DPAD_UP/DOWN  = select command   (host managed)
 *   DPAD_LEFT/RIGHT = scroll items   (scrollBy)
 *   DPAD_CENTER   = fire selected command (host managed)
 *   MENU          = exit app (host managed, Android level)
 */
public class Form extends Displayable {

    static final int BAR_H = 22;
    private static final int PAD = 4;

    private final List<Object> items = new ArrayList<Object>();
    private int scrollY;
    private int lastContentHeight; // measured during render, for scroll clamp

    public Form(String title) {
        setTitle(title);
    }

    public synchronized int append(String str) {
        items.add(str == null ? "" : str);
        return items.size() - 1;
    }

    public synchronized int append(Item item) {
        items.add(item);
        return items.size() - 1;
    }

    public synchronized void delete(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized Object get(int index) {
        return items.get(index);
    }

    public synchronized void scrollBy(int delta) {
        int max = Math.max(0, lastContentHeight - (Host.HEIGHT - BAR_H - 8));
        scrollY = Math.max(0, Math.min(max, scrollY + delta));
    }

    private static final class TextRow {
        final String line;
        final boolean last;

        TextRow(String line, boolean last) {
            this.line = line;
            this.last = last;
        }
    }

    @Override
    public synchronized void renderTo(Graphics g) {
        Font font = Font.getDefaultFont();
        int w = Host.WIDTH;
        int h = Host.HEIGHT;

        // background
        g.setColor(255, 255, 255);
        g.fillRect(0, 0, w, h);

        int top = 4;
        String title = getTitle();
        if (title != null && title.length() > 0) {
            g.setColor(0, 0, 0);
            g.setFont(font);
            g.drawString(title, PAD, 3, Graphics.TOP | Graphics.LEFT);
            g.setColor(160, 160, 160);
            g.fillRect(0, font.getHeight() + 4, w, 1);
            top = font.getHeight() + 8;
        }

        // items, translated by scroll
        int contentTop = top;
        int y = top - scrollY;
        g.setClip(0, contentTop, w, h - BAR_H - contentTop);
        int maxLineWidth = w - 2 * PAD;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof TextField) {
                TextField tf = (TextField) item;
                g.setColor(90, 90, 90);
                g.setFont(font);
                g.drawString(tf.getLabel(), PAD, y, Graphics.TOP | Graphics.LEFT);
                int labelW = font.stringWidth(tf.getLabel());
                g.setColor(0, 0, 0);
                String value = tf.getString();
                // truncate value to the remaining width
                while (value.length() > 0
                        && font.stringWidth(value) > maxLineWidth - labelW - font.stringWidth(": ")) {
                    value = value.substring(0, value.length() - 1);
                }
                g.drawString(": " + value, PAD + labelW, y, Graphics.TOP | Graphics.LEFT);
                y += font.getHeight() + 4;
            } else {
                String text = String.valueOf(item);
                g.setColor(0, 0, 0);
                g.setFont(font);
                String[] lines = wrap(text, font, maxLineWidth);
                for (int li = 0; li < lines.length; li++) {
                    g.drawString(lines[li], PAD, y, Graphics.TOP | Graphics.LEFT);
                    y += font.getHeight() + 1;
                }
                y += 3;
            }
        }
        lastContentHeight = (y + scrollY) - top;
        // restore full clip for the chrome
        g.setClip(0, 0, w, h);

        // command soft bar
        g.setColor(25, 25, 25);
        g.fillRect(0, h - BAR_H, w, BAR_H);
        List<Command> cmds = getCommands();
        if (!cmds.isEmpty()) {
            Host host = Host.get();
            int sel = host == null ? 0 : host.getSelectedCommand();
            if (sel < 0 || sel >= cmds.size()) {
                sel = 0;
            }
            Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(small);
            g.setColor(255, 255, 255);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cmds.size(); i++) {
                String label = cmds.get(i).getLabel();
                if (i == sel) {
                    sb.append('[').append(label).append(']');
                } else {
                    sb.append(label);
                }
                if (i < cmds.size() - 1) {
                    sb.append("  ");
                }
            }
            String bar = sb.toString();
            while (bar.length() > 0 && small.stringWidth(bar) > w - 2 * PAD) {
                // fall back to showing only the selected command
                bar = "[" + cmds.get(sel).getLabel() + "] (" + (sel + 1) + "/" + cmds.size() + ")";
                break;
            }
            g.drawString(bar, PAD, h - BAR_H + 3, Graphics.TOP | Graphics.LEFT);
        }

        // hint line above the bar
        Font tiny = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(tiny);
        g.setColor(150, 150, 150);
        g.drawString("UPDN=cmd LRT=scroll CTR=OK MENU=quit", PAD, h - BAR_H - tiny.getHeight() - 2,
                Graphics.TOP | Graphics.LEFT);

        // transient key debug (helps map the camera's physical buttons)
        Host host = Host.get();
        if (host != null && host.lastKeyWithin(3000)) {
            g.setColor(200, 40, 40);
            g.setFont(tiny);
            g.drawString("key:" + host.getLastKey(), w - PAD, top, Graphics.TOP | Graphics.RIGHT);
        }
    }

    private static String[] wrap(String text, Font font, int maxWidth) {
        List<String> out = new ArrayList<String>();
        String[] paragraphs = text.split("\n", -1);
        for (int p = 0; p < paragraphs.length; p++) {
            String para = paragraphs[p];
            if (para.length() == 0 || font.stringWidth(para) <= maxWidth) {
                out.add(para);
                continue;
            }
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (font.stringWidth(candidate) <= maxWidth) {
                    line.append(line.length() == 0 ? word : " " + word);
                } else {
                    if (line.length() > 0) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    // hard-break an overlong word
                    while (font.stringWidth(word) > maxWidth && word.length() > 1) {
                        int cut = word.length() - 1;
                        while (cut > 1 && font.stringWidth(word.substring(0, cut)) > maxWidth) {
                            cut--;
                        }
                        out.add(word.substring(0, cut));
                        word = word.substring(cut);
                    }
                    line.append(word);
                }
            }
            if (line.length() > 0) {
                out.add(line.toString());
            }
        }
        return out.toArray(new String[out.size()]);
    }
}
