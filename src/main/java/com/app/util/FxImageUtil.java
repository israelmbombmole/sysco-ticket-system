package com.app.util;

import java.awt.image.BufferedImage;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/** BufferedImage to JavaFX {@link Image} without {@code javafx.swing} (jpackage profile stays free of javafx.swing). */
public final class FxImageUtil {

    private FxImageUtil() {}

    public static Image fromBufferedImage(BufferedImage bi) {
        if (bi == null) {
            return null;
        }
        int w = bi.getWidth();
        int h = bi.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int[] buf = new int[w * h];
        bi.getRGB(0, 0, w, h, buf, 0, w);
        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter()
                .setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), buf, 0, w);
        return out;
    }
}
