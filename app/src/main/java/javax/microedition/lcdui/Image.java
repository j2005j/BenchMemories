package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.camsysinfo.jbenchmark.Host;

/**
 * MIDP Image. Only createImage(String) is used by JBenchmark: it loads
 * "/images/earth.png" etc. straight out of the baked-in JBenchmark.jar
 * asset (no file system, no file picker). Bitmaps are ARGB_8888 with
 * scaling disabled so pixels stay 1:1 (Fast / no filter requirement).
 */
public final class Image {

    private final Bitmap bitmap;
    private final Map<Integer, Bitmap> scaledCache = new HashMap<Integer, Bitmap>();

    private Image(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    /**
     * MIDP: loads an image from a resource path — here the entry is read
     * straight out of the baked-in JBenchmark.jar asset (no file system,
     * no file picker). Declared IOException like real MIDP so JBenchmark's
     * original constructor try/catch (IOException) handles a missing image
     * gracefully instead of the app dying. Bitmaps are ARGB_8888 with
     * scaling disabled so pixels stay 1:1 (Fast / no filter requirement).
     */
    public static Image createImage(String name) throws java.io.IOException {
        if (name == null) throw new NullPointerException("name");
        byte[] data = readAssetEntry(name);
        if (data == null) {
            throw new java.io.IOException("image not found: " + name);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        opts.inDither = false;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (bmp == null) {
            throw new java.io.IOException("decode failed: " + name);
        }
        // MIDP images are immutable
        bmp = bmp.copy(Bitmap.Config.ARGB_8888, false);
        return new Image(bmp);
    }

    public int getWidth() {
        return bitmap.getWidth();
    }

    public int getHeight() {
        return bitmap.getHeight();
    }

    Bitmap getBitmap() {
        return bitmap;
    }

    /** Bitmap scaled to (w,h) with NEAREST-neighbor (Fast: no filter). */
    Bitmap getBitmapScaled(int w, int h) {
        if (w == bitmap.getWidth() && h == bitmap.getHeight()) return bitmap;
        Integer key = Integer.valueOf((w << 16) | h);
        Bitmap cached = scaledCache.get(key);
        if (cached == null || cached.isRecycled()) {
            // filter=false -> nearest neighbor (Fast / no filter)
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, false);
            scaledCache.put(key, scaled);
            return scaled;
        }
        return cached;
    }

    /**
     * Read a single entry out of the baked-in assets/JBenchmark.jar.
     * The APK itself is a zip; the jar entry inside it is read via ZipFile
     * semantics through the Host's cached jar bytes.
     */
    private static byte[] readAssetEntry(String name) {
        Host h = Host.get();
        if (h == null) return null;
        String key = name.startsWith("/") ? name.substring(1) : name;
        try {
            byte[] jar = h.getJarBytes();
            ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar));
            try {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.getName().equals(key)) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = zin.read(buf)) > 0) bos.write(buf, 0, n);
                        return bos.toByteArray();
                    }
                }
            } finally {
                zin.close();
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }
}
