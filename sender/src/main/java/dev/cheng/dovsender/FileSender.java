package dev.cheng.dovsender;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 主控：读文件 -> 分块 -> 头部 + 负载编码 -> 渲染
 */
public class FileSender {
    private final Path file;
    private final ProtocolConfig cfg;
    private final FrameEncoder encoder;
    private final FECEncoder fec;
    private final GLRenderer renderer;
    private final FrameRateController frc;

    public FileSender(Path file, ProtocolConfig cfg, FrameEncoder encoder, FECEncoder fec, GLRenderer renderer) {
        this.file = file;
        this.cfg = cfg;
        this.encoder = encoder;
        this.fec = fec;
        this.renderer = renderer;
        this.frc = new FrameRateController(cfg.fps);
    }

    private static long first8BytesToLong(byte[] b) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[i] & 0xFFL);
        }
        return v;
    }

    private static long hashFileName64(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] name = file.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] d = md.digest(name);
        return first8BytesToLong(d);
    }

    public void startAndSend(int warmupSeconds) throws Exception {
        byte[] all = Files.readAllBytes(file);
        long fileSize = all.length;
        System.out.printf("File size: %d bytes%n", fileSize);

        // v0.1: compute payload capacity per frame using header rows and payload repetition
        int bytesPerFrame = cfg.bytesPerFramePayload;
        int totalFrames = (all.length + bytesPerFrame - 1) / bytesPerFrame;
        System.out.printf("Will send %d frames (payload %d bytes/frame, headerRows=%d, payloadHRep=%d)\n",
                totalFrames, bytesPerFrame, cfg.headerRows, cfg.payloadHRep);

        // session & file metadata
        long sessionId = new SecureRandom().nextLong();
        long fileNameHash = hashFileName64(file);

        renderer.init(); // create window + GL context

        // Warm-up / countdown: keep a mid-gray screen for 3 seconds before sending
        // so user can finish placing the window on the capture output if needed.
        ByteBuffer warmup = encoder.encodeFrame(new FrameEncoder.HeaderFields() {{
            ver = 0x01; flags = 0; sessionId = 0L; frameIdx = 0; totalFrames = 0; payloadLen = 0;
            fileSize = 0L; fileNameHash = 0L; payloadCrc32c = 0; }}, new byte[0]);
        long warmupEnd = System.nanoTime() + Math.max(0, warmupSeconds) * 1_000_000_000L;
        while (!renderer.shouldClose() && System.nanoTime() < warmupEnd) {
            renderer.renderFrame(warmup);
            frc.sync();
        }

        // render frames
        for (int frameIndex = 0; frameIndex < totalFrames && !renderer.shouldClose(); frameIndex++) {
            int offset = frameIndex * bytesPerFrame;
            int remain = Math.min(bytesPerFrame, all.length - offset);

            // exact sized payload slice
            byte[] payload = new byte[remain];
            System.arraycopy(all, offset, payload, 0, remain);

            // FEC (stub)
            byte[] toSend = fec.encodeFrame(payload);

            // header fields
            FrameEncoder.HeaderFields h = new FrameEncoder.HeaderFields();
            h.ver = 0x01;
            h.flags = 0;
            if (frameIndex == 0) h.flags |= 0b10; // FIRST
            if (frameIndex == totalFrames - 1) h.flags |= 0b01; // EOF on last frame
            h.sessionId = sessionId;
            h.frameIdx = frameIndex;
            h.totalFrames = totalFrames;
            h.payloadLen = toSend.length;
            h.fileSize = fileSize;
            h.fileNameHash = fileNameHash;
            h.payloadCrc32c = FrameEncoder.crc32c(toSend, 0, toSend.length);

            ByteBuffer pixelBuffer = encoder.encodeFrame(h, toSend);
            renderer.renderFrame(pixelBuffer);
            frc.sync();
        }

        renderer.destroy();
        System.out.println("Send finished/renderer closed.");
    }
}
