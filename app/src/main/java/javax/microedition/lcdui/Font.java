package javax.microedition.lcdui;

import android.graphics.Typeface;

/**
 * MIDP-1.0 Font. Sizes map to pixel heights the same way j2me-loader does
 * (index = size/8 -> {medium, small, large}); JBenchmark randomizes over
 * the standard face/style/size constants (3 faces, 4 styles, 3 sizes).
 * No anti-aliasing toggles anywhere (AA off is a project requirement).
 */
public final class Font {

    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;

    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    // pixel sizes: index = size/8 -> {medium, small, large}
    private static final int[] PX = { 16, 12, 20 };

    private static final Font DEFAULT = getFont(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);

    private final int face;
    private final int style;
    private final int size;
    private final int px;
    final Typeface typeface;

    // shared paint, mutated by Graphics (single UI thread only)
    final android.graphics.Paint paint = new android.graphics.Paint();
    final float ascent;
    final float descent;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;
        this.px = PX[(size / 8) & 3];

        int tfStyle = Typeface.NORMAL;
        if ((style & STYLE_BOLD) != 0) tfStyle |= Typeface.BOLD;
        if ((style & STYLE_ITALIC) != 0) tfStyle |= Typeface.ITALIC;
        Typeface base;
        if (face == FACE_MONOSPACE) {
            base = Typeface.MONOSPACE;
        } else if (face == FACE_PROPORTIONAL) {
            base = Typeface.SANS_SERIF;
        } else {
            base = Typeface.DEFAULT;
        }
        this.typeface = Typeface.create(base, tfStyle);

        paint.setTypeface(typeface);
        paint.setTextSize(px);
        paint.setAntiAlias(false);
        paint.setSubpixelText(false);
        paint.setUnderlineText((style & STYLE_UNDERLINED) != 0);

        android.graphics.Paint.FontMetrics fm = paint.getFontMetrics();
        this.ascent = fm.ascent;
        this.descent = fm.descent;
    }

    public static Font getFont(int face, int style, int size) {
        return new Font(face, style, size);
    }

    public static Font getDefaultFont() {
        return DEFAULT;
    }

    public int getFace() { return face; }
    public int getStyle() { return style; }
    public int getSize() { return size; }

    public boolean isBold() { return (style & STYLE_BOLD) != 0; }
    public boolean isItalic() { return (style & STYLE_ITALIC) != 0; }
    public boolean isUnderlined() { return (style & STYLE_UNDERLINED) != 0; }

    /** Line height in pixels (MIDP: height of a line of text). */
    public int getHeight() {
        return (int) Math.ceil(paint.getFontSpacing());
    }

    public int getBaselinePosition() {
        return (int) Math.ceil(-ascent);
    }

    public int stringWidth(String str) {
        if (str == null || str.length() == 0) return 0;
        return (int) Math.ceil(paint.measureText(str));
    }

    public int charWidth(char ch) {
        return (int) Math.ceil(paint.measureText(String.valueOf(ch)));
    }

    public int substringWidth(String str, int offset, int len) {
        if (str == null || len <= 0) return 0;
        return (int) Math.ceil(paint.measureText(str, offset, offset + len));
    }
}
