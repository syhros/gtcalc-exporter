package com.syhros.packextract.export;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

/** Encodes and writes PNG files on background threads so rendering does not wait for the disk. */
public final class PngWriter {

    private final ExecutorService pool;
    private final Problems problems;
    private final AtomicInteger written = new AtomicInteger();

    public PngWriter(Problems problems) {
        this.problems = problems;
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        // A full queue makes the render thread encode the image itself, which bounds memory use.
        this.pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(2048), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void write(final File file, final int[] argb, final int size) {
        pool.execute(() -> {
            try {
                BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, size, size, argb, 0, size);
                file.getParentFile().mkdirs();
                ImageIO.write(img, "png", file);
                written.incrementAndGet();
            } catch (Throwable t) {
                problems.add("writing " + file.getName(), t);
            }
        });
    }

    public int written() {
        return written.get();
    }

    /** Waits for all queued images to be written. */
    public void finish() throws InterruptedException {
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
    }
}
