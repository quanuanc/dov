package dev.cheng.dovsender;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 把 payload bytes (<= width*height) 映射到像素 ByteBuffer (RGB)
 * 输出格式：GL_UNSIGNED_BYTE, GL_RGB
 * <p>
 * Layout:
 * - we will map payload[0] -> pixel (0,0), payload[1] -> (1,0), ... row-major
 * - each payload byte turned into RGB = (b,b,b)
 * <p>
 * 返回的 ByteBuffer position=0 limit=buffer.capacity()，可直接上传到 glTexSubImage2D
 */
public class FrameEncoder {
    private final ProtocolConfig cfg;
    private final int pixelCount;
    private final ByteBuffer buffer; // reused buffer

    public FrameEncoder(ProtocolConfig cfg) {
        this.cfg = cfg;
        this.pixelCount = cfg.width * cfg.height;
        // 3 bytes per pixel (R,G,B)
        this.buffer = ByteBuffer.allocateDirect(pixelCount * 3).order(ByteOrder.nativeOrder());
    }

    /**
     * Encode one frame payload into the reusable ByteBuffer and return it (position=0).
     * payload length should be <= pixelCount; trailing pixels left as zero.
     */
    public ByteBuffer encodeFrame(int frameIndex, byte[] payload) {
        buffer.clear();
        int toCopy = Math.min(payload.length, pixelCount);
        // write pixels
        for (int i = 0; i < toCopy; i++) {
            byte v = payload[i];
            buffer.put(v).put(v).put(v);
        }
        // remaining pixels, write mid-gray 0x80 to avoid extreme blacks (optional)
        for (int i = toCopy; i < pixelCount; i++) {
            byte v = (byte) 0x80;
            buffer.put(v).put(v).put(v);
        }
        buffer.flip();
        return buffer;
    }
}
