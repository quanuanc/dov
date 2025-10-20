package dev.cheng.dov.protocol;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single encoded frame in the custom video stream.
 */
public record VideoFrame(FrameType type, int index, int totalFrames, byte[] payload) {
    public VideoFrame(FrameType type, int index, int totalFrames, byte[] payload) {
        this.type = Objects.requireNonNull(type, "type");
        this.index = index;
        this.totalFrames = totalFrames;
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public VideoFrame copyWithIndex(int newIndex, int newTotal) {
        return new VideoFrame(type, newIndex, newTotal, payload);
    }

    @Override
    public String toString() {
        return "VideoFrame{" +
                "type=" + type +
                ", index=" + index +
                ", totalFrames=" + totalFrames +
                ", payloadLength=" + payload.length +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VideoFrame(FrameType type1, int index1, int frames, byte[] payload1))) {
            return false;
        }
        return index == index1
                && totalFrames == frames
                && type == type1
                && Arrays.equals(payload, payload1);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, index, totalFrames);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
