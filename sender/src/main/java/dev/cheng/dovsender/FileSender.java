package dev.cheng.dovsender;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主控：读文件 -> 分块 -> 交给 encoder -> renderer 绘制
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

    public void startAndSend() throws Exception {
        byte[] all = Files.readAllBytes(file);
        System.out.printf("File size: %d bytes%n", all.length);

        // split into frames of payloadBytesPerFrame
        int bytesPerFrame = cfg.payloadBytesPerFrame;
        int totalFrames = (all.length + bytesPerFrame - 1) / bytesPerFrame;
        System.out.printf("Will send %d frames (payload %d bytes/frame)%n", totalFrames, bytesPerFrame);

        AtomicBoolean running = new AtomicBoolean(true);

        renderer.init(); // create window + GL context

        // run render loop on main thread
        int frameIndex = 0;
        while (!renderer.shouldClose() && frameIndex < totalFrames) {
            int offset = frameIndex * bytesPerFrame;
            int remain = Math.min(bytesPerFrame, all.length - offset);
            byte[] payload = new byte[bytesPerFrame];
            System.arraycopy(all, offset, payload, 0, remain); // pad trailing with 0s

            // TODO: use FEC encoder across a group of frames (stub)
            byte[] toSend = fec.encodeFrame(payload); // for now identity

            ByteBuffer pixelBuffer = encoder.encodeFrame(frameIndex, toSend);
            renderer.renderFrame(pixelBuffer);
            frc.sync();

            frameIndex++;
        }

        // finalization: optionally send an EOF frame (all-zero with flag)
        renderer.destroy();
        System.out.println("Send finished/renderer closed.");
    }
}
