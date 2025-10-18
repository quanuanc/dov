package dev.cheng.dovsender;

public class FrameRateController {
    private final long frameIntervalNanos;
    private long lastTime;

    public FrameRateController(int fps) {
        this.frameIntervalNanos = 1_000_000_000L / fps;
        this.lastTime = System.nanoTime();
    }

    public void sync() {
        long now = System.nanoTime();
        long elapsed = now - lastTime;
        long sleep = frameIntervalNanos - elapsed;
        if (sleep > 2_000_000) { // more than 2ms
            try {
                Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
            } catch (InterruptedException ignored) {
            }
        } else if (sleep > 0) {
            // busy-wait for small remainder
            long end = now + sleep;
            while (System.nanoTime() < end) {
            }
        }
        lastTime = System.nanoTime();
    }
}
