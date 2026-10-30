package dev.camsysinfo.jbenchmark;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/**
 * Presents the 240x320 back buffer, nearest-neighbor scaled ("Fast (no
 * filter)"), letterboxed for the camera's 640x480 screen. If anything ever
 * goes wrong it draws an on-screen error report on black — never a white
 * screen (camera safety requirement).
 */
public class EmuView extends View {

    private volatile Host host;
    private volatile String error;

    public EmuView(Context context) {
        super(context);
        setKeepScreenOn(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setHost(Host h) {
        this.host = h;
    }

    /** Any thread. */
    public void showError(String msg) {
        error = msg == null ? "Unknown error" : msg;
        postInvalidate();
    }

    public void clearError() {
        error = null;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);

        String err = error;
        if (err != null) {
            drawError(canvas, err);
            return;
        }

        Host h = host;
        if (h == null) return;
        Bitmap buf = h.getBuffer();
        if (buf == null) return;

        float vw = getWidth();
        float vh = getHeight();
        if (vw <= 0 || vh <= 0) return;

        // letterbox 3:4 emulated screen into the view
        float scale = Math.min(vw / Host.WIDTH, vh / Host.HEIGHT);
        float dw = Host.WIDTH * scale;
        float dh = Host.HEIGHT * scale;
        float left = (vw - dw) / 2f;
        float top = (vh - dh) / 2f;

        Rect src = new Rect(0, 0, Host.WIDTH, Host.HEIGHT);
        RectF dst = new RectF(left, top, left + dw, top + dh);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setFilterBitmap(false); // NEAREST neighbor — Fast (no filter)
        synchronized (h) {
            canvas.drawBitmap(buf, src, dst, p);
        }
    }

    private void drawError(Canvas canvas, String msg) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float size = Math.max(11f, Math.min(w, h) / 16f);
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setColor(Color.rgb(255, 120, 120));
        float pad = Math.max(8f, w / 30f);

        // shrink until the wrapped text fits
        String[] lines = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            p.setTextSize(size);
            lines = wrap(msg, p, w - 2 * pad);
            float lineH = p.getTextSize() * 1.25f;
            if (lines.length * lineH <= h - 2 * pad || size <= 6f) break;
            size *= 0.85f;
        }

        p.setColor(Color.WHITE);
        p.setTextSize(size);
        float lineH = p.getTextSize() * 1.25f;
        float y = pad + p.getTextSize();
        canvas.drawText("JBenchmark error", pad, y, p);
        y += lineH * 1.5f;
        p.setColor(Color.rgb(255, 120, 120));
        for (int i = 0; i < lines.length && y < h - pad; i++) {
            canvas.drawText(lines[i], pad, y, p);
            y += lineH;
        }
    }

    private static String[] wrap(String text, Paint p, float maxWidth) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        String[] paragraphs = text.split("\n", -1);
        for (int pi = 0; pi < paragraphs.length; pi++) {
            String para = paragraphs[pi];
            if (p.measureText(para) <= maxWidth || para.length() == 0) {
                out.add(para);
                continue;
            }
            StringBuilder line = new StringBuilder();
            int start = 0;
            while (start < para.length()) {
                // greedy: take the longest prefix that fits
                int end = para.length();
                while (end > start + 1 && p.measureText(para, start, end) > maxWidth) {
                    end--;
                }
                // prefer breaking at a space
                String chunk = para.substring(start, end);
                int sp = chunk.lastIndexOf(' ');
                String next;
                if (sp > 0 && end < para.length()) {
                    next = chunk.substring(0, sp);
                    start = start + sp + 1;
                } else {
                    next = chunk;
                    start = end;
                }
                if (line.length() > 0 && p.measureText(line + " " + next) <= maxWidth) {
                    line.append(' ').append(next);
                } else {
                    if (line.length() > 0) out.add(line.toString());
                    line.setLength(0);
                    line.append(next);
                }
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out.toArray(new String[out.size()]);
    }
}
