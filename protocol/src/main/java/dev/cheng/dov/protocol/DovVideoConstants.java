package dev.cheng.dov.protocol;

import java.nio.charset.StandardCharsets;

/**
 * Shared constants that describe the v1.0 DOV video container layout.
 */
public final class DovVideoConstants {
    public static final byte[] MAGIC = "DOVVID1".getBytes(StandardCharsets.US_ASCII);
    public static final byte FORMAT_VERSION = 1;
    public static final int FRAME_WIDTH = 640;
    public static final int FRAME_HEIGHT = 480;
    public static final int COLOR_CHANNELS = 3;
    public static final int FRAMES_PER_SECOND = 30;
    public static final int MODULE_SIZE = 10;
    public static final int GRID_COLS = FRAME_WIDTH / MODULE_SIZE;
    public static final int GRID_ROWS = FRAME_HEIGHT / MODULE_SIZE;
    public static final int ALIGNMENT_ROWS = 1;
    public static final int ALIGNMENT_COLS = 1;
    public static final int DATA_COLS = GRID_COLS - ALIGNMENT_COLS;
    public static final int DATA_ROWS = GRID_ROWS - ALIGNMENT_ROWS;
    public static final int SYMBOLS_PER_FRAME = DATA_COLS * DATA_ROWS;
    public static final int SYMBOL_BITS = 2;
    public static final int LENGTH_PREFIX_BYTES = Short.BYTES;
    public static final int MESSAGE_CAPACITY_BITS = SYMBOLS_PER_FRAME * SYMBOL_BITS;
    public static final int MESSAGE_CAPACITY_BYTES = MESSAGE_CAPACITY_BITS / 8;
    public static final int MAX_MESSAGE_BYTES = MESSAGE_CAPACITY_BYTES - LENGTH_PREFIX_BYTES;
    public static final int FRAME_HEADER_BYTES = 1 + Integer.BYTES + Integer.BYTES + Short.BYTES;
    public static final int MAX_CHUNK_BYTES = MAX_MESSAGE_BYTES - FRAME_HEADER_BYTES;
    private static final int MIN_MESSAGE_BYTES = FRAME_HEADER_BYTES;

    static {
        if (FRAME_WIDTH % MODULE_SIZE != 0 || FRAME_HEIGHT % MODULE_SIZE != 0) {
            throw new IllegalStateException("Frame dimensions must be divisible by module size");
        }
        if (MAX_CHUNK_BYTES <= 0) {
            throw new IllegalStateException("Chunk capacity is not positive");
        }
        if (MESSAGE_CAPACITY_BYTES <= MIN_MESSAGE_BYTES) {
            throw new IllegalStateException("Message capacity must exceed header size");
        }
    }

    private DovVideoConstants() {
    }
}
