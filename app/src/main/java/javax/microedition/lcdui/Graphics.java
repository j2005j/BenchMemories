package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * MIDP Graphics over the 240x320 back-buffer bitmap. Mirrors j2me-loader's
 * implementation semantics exactly (verified against its source):
 *  - anchors: HCENTER=1 VCENTER=2 LEFT=4 RIGHT=8 TOP=16 BOTTOM=32
 *  - fillArc draws a PIE with negated start/arc angles
 *  - drawString anchor math via Paint.Align + ascent/descent offsets
 *  - setClip REPLACES the clip (MIDP semantics) via save/restore
 *  - NO anti-aliasing anywhere (AA OFF requirement), no bitmap filtering
 *    (Fast shader / "no filter" requirement)
 */
public final class Graphics {

    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;

    private final android.graphics.Canvas canvas;
    private final Paint paint = new Paint();

    private Font font = Font.getDefaultFont();
    private int clipLeft, clipTop, clipRight, clipBottom;
    private int baseSave;

    private Graphics(Bitmap buffer) {
        this.canvas = new android.graphics.Canvas(buffer);
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);   // Fast shader: no filter
        paint.setDither(false);
        paint.setStyle(Paint.Style.FILL);
        baseSave = canvas.save();
        clipRight = buffer.getWidth();
        clipBottom = buffer.getHeight();
        canvas.clipRect(0, 0, clipRight, clipBottom);
    }

    /** Factory for the host (its own package cannot see a private ctor). */
    public static Graphics create(Bitmap buffer) {
        return new Graphics(buffer);
    }

    // ---- state ----

    public void setColor(int r, int g, int b) {
        paint.setColor(0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }

    public void setColor(int rgb) {
        paint.setColor(0xFF000000 | (rgb & 0xFFFFFF));
    }

    public void setFont(Font f) {
        if (f == null) return;
        font = f;
        paint.setTypeface(f.typeface);
        paint.setTextSize(f.paint.getTextSize());
        paint.setUnderlineText(f.isUnderlined());
    }

    public Font getFont() {
        return font;
    }

    /** MIDP setClip REPLACES the clip region. */
    public void setClip(int x, int y, int w, int h) {
        if (w < 0) w = 0;
        if (h < 0) h = 0;
        clipLeft = x;
        clipTop = y;
        clipRight = x + w;
        clipBottom = y + h;
        canvas.restoreToCount(baseSave);
        canvas.save();
        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);
    }

    /** MIDP clipRect INTERSECTS with the current clip. */
    public void clipRect(int x, int y, int w, int h) {
        int l = Math.max(clipLeft, x);
        int t = Math.max(clipTop, y);
        int r = Math.min(clipRight, x + w);
        int b = Math.min(clipBottom, y + h);
        setClip(l, t, Math.max(0, r - l), Math.max(0, b - t));
    }

    public int getClipX() { return clipLeft; }
    public int getClipY() { return clipTop; }
    public int getClipWidth() { return clipRight - clipLeft; }
    public int getClipHeight() { return clipBottom - clipTop; }

    public int getColor() { return paint.getColor() & 0xFFFFFF; }
    public int getRedComponent() { return (paint.getColor() >> 16) & 0xFF; }
    public int getGreenComponent() { return (paint.getColor() >> 8) & 0xFF; }
    public int getBlueComponent() { return paint.getColor() & 0xFF; }

    /** Called by the host before every frame render (buffer identity never changes). */
    public void resetState(Bitmap buffer) {
        canvas.restoreToCount(baseSave);
        canvas.save();
        clipLeft = 0;
        clipTop = 0;
        clipRight = buffer.getWidth();
        clipBottom = buffer.getHeight();
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setDither(false);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setTextAlign(Paint.Align.LEFT);
        font = Font.getDefaultFont();
        paint.setTypeface(font.typeface);
        paint.setTextSize(font.paint.getTextSize());
        paint.setUnderlineText(false);
        canvas.clipRect(0, 0, clipRight, clipBottom);
    }

    // ---- primitives ----

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        canvas.drawRect(x, y, x + w, y + h, paint);
    }

    public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) return;
        Paint p = cloneStroke();
        p.setStyle(Paint.Style.STROKE);
        canvas.drawRect(x, y, x + w, y + h, p);
    }

    public void fillRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {
        if (w <= 0 || h <= 0) return;
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h),
                arcWidth / 2.0f, arcHeight / 2.0f, paint);
    }

    public void drawRoundRect(int x, int y, int w, int h, int arcWidth, int arcHeight) {
        if (w < 0 || h < 0) return;
        Paint p = cloneStroke();
        p.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h),
                arcWidth / 2.0f, arcHeight / 2.0f, p);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        Paint p = cloneStroke();
        p.setStyle(Paint.Style.STROKE);
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0) {
            // include the end pixel (MIDP line coverage)
            dx /= len;
            dy /= len;
            canvas.drawLine(x1, y1, x2 + dx, y2 + dy, p);
        } else {
            canvas.drawPoint(x1, y1, p);
        }
    }

    /** Pie (MIDP fillArc semantics; angles negated for Android, as j2me-loader does). */
    public void fillArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        if (w <= 0 || h <= 0) return;
        canvas.drawArc(new RectF(x, y, x + w, y + h), -startAngle, -arcAngle, true, paint);
    }

    public void drawArc(int x, int y, int w, int h, int startAngle, int arcAngle) {
        if (w <= 0 || h <= 0) return;
        Paint p = cloneStroke();
        p.setStyle(Paint.Style.STROKE);
        canvas.drawArc(new RectF(x, y, x + w, y + h), -startAngle, -arcAngle, true, p);
    }

    public void drawString(String text, int x, int y, int anchor) {
        if (text == null || text.length() == 0) return;
        Font f = font;
        if ((anchor & RIGHT) != 0) {
            paint.setTextAlign(Paint.Align.RIGHT);
        } else if ((anchor & HCENTER) != 0) {
            paint.setTextAlign(Paint.Align.CENTER);
        } else {
            paint.setTextAlign(Paint.Align.LEFT);
        }
        paint.setTypeface(f.typeface);
        paint.setTextSize(f.paint.getTextSize());
        paint.setUnderlineText(f.isUnderlined());

        float ly;
        if ((anchor & BOTTOM) != 0) {
            ly = y - f.descent;
        } else if ((anchor & VCENTER) != 0) {
            ly = y - (f.descent + f.ascent) / 2.0f;
        } else if ((anchor & BASELINE) != 0) {
            ly = y;
        } else {
            ly = y - f.ascent;
        }
        canvas.drawText(text, x, ly, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    public void drawSubstring(String str, int offset, int len, int x, int y, int anchor) {
        if (str == null || len <= 0 || offset + len > str.length()) return;
        drawString(str.substring(offset, offset + len), x, y, anchor);
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) return;
        Bitmap bmp = img.getBitmap();
        if (bmp == null) return;
        float dx = x, dy = y;
        if ((anchor & RIGHT) != 0) dx -= bmp.getWidth();
        else if ((anchor & HCENTER) != 0) dx -= bmp.getWidth() / 2.0f;
        if ((anchor & BOTTOM) != 0) dy -= bmp.getHeight();
        else if ((anchor & VCENTER) != 0) dy -= bmp.getHeight() / 2.0f;
        Paint p = cloneStroke();
        p.setFilterBitmap(false); // nearest neighbor: no filter
        canvas.drawBitmap(bmp, dx, dy, p);
    }

    public void drawRegion(Image img, int x, int y, int w, int h,
                           int transform, int deltaX, int deltaY, int anchor) {
        drawImage(img, x + deltaX, y + deltaY, anchor);
    }

    public void drawRGB(int[] pixels, int offset, int stride,
                        int x, int y, int w, int h, boolean alpha) {
        if (pixels == null || w <= 0 || h <= 0) return;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] row = new int[w];
        for (int j = 0; j < h; j++) {
            System.arraycopy(pixels, offset + j * stride, row, 0, w);
            if (!alpha) {
                for (int i = 0; i < w; i++) row[i] |= 0xFF000000;
            }
            bmp.setPixels(row, 0, w, 0, j, w, 1);
        }
        Paint p = cloneStroke();
        p.setFilterBitmap(false);
        canvas.drawBitmap(bmp, x, y, p);
        bmp.recycle();
    }

    public void fillPaint() {
        canvas.drawPaint(paint);
    }

    public void drawPaint() {
        canvas.drawPaint(paint);
    }

    // ---- helpers ----

    private Paint cloneStroke() {
        Paint p = new Paint(paint);
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
        p.setDither(false);
        return p;
    }
}
