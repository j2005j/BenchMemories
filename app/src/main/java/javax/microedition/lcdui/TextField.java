package javax.microedition.lcdui;

/**
 * MIDP-1.0 TextField: label + editable value. JBenchmark only reads the
 * string (device name field) and changes constraints, so no input method
 * is wired up — values are whatever the MIDlet set as defaults, which on
 * this camera (no touch, no keyboard) is exactly what we want.
 */
public class TextField extends Item {

    private final String label;
    private String text;
    private int constraints;
    private final int maxSize;

    public TextField(String label, String text, int constraints, int maxSize) {
        this.label = label;
        this.text = text;
        this.constraints = constraints;
        this.maxSize = maxSize;
    }

    public String getString() {
        return text == null ? "" : text;
    }

    public void setString(String s) {
        text = s;
    }

    public void setConstraints(int c) {
        constraints = c;
    }

    public int getConstraints() {
        return constraints;
    }

    public String getLabel() {
        return label == null ? "" : label;
    }

    public int getMaxSize() {
        return maxSize;
    }
}
