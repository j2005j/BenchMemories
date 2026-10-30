package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

import dev.camsysinfo.jbenchmark.Host;

/**
 * Base of Canvas/Form. Logical screen is fixed at the emulated resolution
 * (240x320) — getWidth/getHeight report that, matching the required
 * "Resolution of emulation: 240x320" setting.
 */
public abstract class Displayable {

    private String title;
    private final List<Command> commands = new ArrayList<Command>();
    private CommandListener listener;

    protected Displayable() {
    }

    public void addCommand(Command cmd) {
        if (cmd != null && !commands.contains(cmd)) {
            commands.add(cmd);
        }
    }

    public void removeCommand(Command cmd) {
        commands.remove(cmd);
    }

    public void setCommandListener(CommandListener l) {
        this.listener = l;
    }

    public CommandListener getCommandListener() {
        return listener;
    }

    public List<Command> getCommands() {
        return commands;
    }

    public int getWidth() {
        return Host.WIDTH;
    }

    public int getHeight() {
        return Host.HEIGHT;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Called by the host on the UI thread to render this displayable into
     * the 240x320 back buffer. Canvas draws via paint(Graphics); Form uses
     * its own high-level layout.
     */
    public void renderTo(Graphics g) {
        // nothing by default
    }
}
