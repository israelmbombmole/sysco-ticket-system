package com.app.service.myshift;

import com.app.util.FxImageUtil;
import com.github.sarxos.webcam.Webcam;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Live webcam preview in an {@link ImageView} and JPEG frame capture for {@link FaceAuthClient}.
 * Single preview thread; {@link #getImageJpegBytes()} is synchronized with camera access.
 */
public final class MyShiftWebcamSession {

    private static final Logger LOG = LogManager.getLogger(MyShiftWebcamSession.class);
    private static final int UI_MIN_INTERVAL_MS = 45;

    private final Object camLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean();
    private final ImageView target;

    private Webcam webcam;
    private Thread thread;
    private long lastUiPostMs;
    /** Filled when {@link #start()} returns false or throws, for UI diagnostics. */
    private String lastErrorMessage;

    public MyShiftWebcamSession(ImageView target) {
        this.target = target;
    }

    /** @return last failure reason, if {@link #start()} failed; may be {@code null} */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public boolean isRunning() {
        return running.get() && webcam != null && webcam.isOpen();
    }

    /**
     * Opens the default camera and starts the preview loop.
     *
     * @return true if the camera is open
     */
    public boolean start() {
        lastErrorMessage = null;
        if (running.get()) {
            return isRunning();
        }
        try {
            webcam = Webcam.getDefault();
        } catch (Exception e) {
            lastErrorMessage = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "");
            LOG.debug("No webcam: {}", e.getMessage());
            return false;
        }
        if (webcam == null) {
            lastErrorMessage = "no default webcam device";
            return false;
        }
        try {
            Dimension[] sizes = webcam.getViewSizes();
            if (sizes != null && sizes.length > 0) {
                try {
                    Dimension vga = new Dimension(640, 480);
                    if (Arrays.asList(sizes).contains(vga)) {
                        webcam.setViewSize(vga);
                    } else {
                        webcam.setViewSize(sizes[0]);
                    }
                } catch (Exception e) {
                    LOG.debug("setViewSize: {}", e.getMessage());
                }
            }
            webcam.open();
        } catch (Exception e) {
            lastErrorMessage = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "");
            LOG.warn("Webcam open failed: {}", e.getMessage());
            if (webcam != null) {
                try {
                    if (webcam.isOpen()) {
                        webcam.close();
                    }
                } catch (Exception ignored) {
                }
            }
            webcam = null;
            return false;
        }
        running.set(true);
        thread = new Thread(this::runPreview, "myshift-webcam-preview");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            try {
                thread.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        synchronized (camLock) {
            if (webcam != null) {
                if (webcam.isOpen()) {
                    try {
                        webcam.close();
                    } catch (Exception e) {
                        LOG.debug("Webcam close: {}", e.getMessage());
                    }
                }
                webcam = null;
            }
        }
    }

    /** Current frame as JPEG, or {@code null} if the camera is unavailable. */
    public byte[] getImageJpegBytes() {
        BufferedImage image;
        synchronized (camLock) {
            if (webcam == null || !webcam.isOpen() || !running.get()) {
                return null;
            }
            try {
                image = webcam.getImage();
            } catch (Exception e) {
                LOG.debug("getImage: {}", e.getMessage());
                return null;
            }
        }
        if (image == null) {
            return null;
        }
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(95_000)) {
            if (!ImageIO.write(image, "jpg", baos)) {
                return null;
            }
            return baos.toByteArray();
        } catch (Exception e) {
            LOG.debug("encode jpeg: {}", e.getMessage());
            return null;
        }
    }

    private void runPreview() {
        while (running.get()) {
            try {
                BufferedImage raw;
                synchronized (camLock) {
                    if (webcam == null || !webcam.isOpen()) {
                        break;
                    }
                    raw = webcam.getImage();
                }
                if (raw == null) {
                    Thread.sleep(40);
                    continue;
                }
                long now = System.currentTimeMillis();
                if (now - lastUiPostMs < UI_MIN_INTERVAL_MS) {
                    Thread.sleep(15);
                    continue;
                }
                lastUiPostMs = now;
                if (!running.get() || target == null) {
                    break;
                }
                javafx.scene.image.Image fx = FxImageUtil.fromBufferedImage(raw);
                if (fx == null) {
                    continue;
                }
                Platform.runLater(() -> {
                    if (running.get() && target != null) {
                        target.setImage(fx);
                    }
                });
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    LOG.debug("webcam loop: {}", e.getMessage());
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
