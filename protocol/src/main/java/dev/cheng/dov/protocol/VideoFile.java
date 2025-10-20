package dev.cheng.dov.protocol;

import java.util.List;
import java.util.Objects;

/**
 * In-memory representation of a decoded video container.
 */
public record VideoFile(VideoConfig config, List<VideoFrame> frames) {
    public VideoFile(VideoConfig config, List<VideoFrame> frames) {
        this.config = Objects.requireNonNull(config, "config");
        this.frames = List.copyOf(frames);
    }
}
