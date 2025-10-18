package dev.cheng.dovsender;

public class ProtocolConfig {
    public final int width;
    public final int height;
    public final int fps;
    public final int payloadBytesPerFrame; // how many bytes we pack per frame

    private ProtocolConfig(int width, int height, int fps) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        // using 3 bytes per pixel (RGB), but we map 1 payload byte -> 3 channel grayscale
        this.payloadBytesPerFrame = width * height; // 1 byte per pixel mapping
    }

    public static ProtocolConfig defaults() {
        return new ProtocolConfig(1920, 1080, 60);
    }
}
