package dev.cheng.dov.protocol;

/**
 * Immutable metadata that describes a video stream configuration.
 */
public record VideoConfig(
        byte version,
        int frameWidth,
        int frameHeight,
        int channels
) {
    public VideoConfig {
        if (frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("Frame dimensions must be positive");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("Channel count must be positive");
        }
    }

    public static VideoConfig defaultConfig() {
        return new VideoConfig(
                DovVideoConstants.FORMAT_VERSION,
                DovVideoConstants.FRAME_WIDTH,
                DovVideoConstants.FRAME_HEIGHT,
                DovVideoConstants.COLOR_CHANNELS
        );
    }
}
