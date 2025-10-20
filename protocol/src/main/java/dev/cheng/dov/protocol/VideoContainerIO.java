package dev.cheng.dov.protocol;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes MP4 containers that embed the custom DOV v1.0 frames.
 */
public final class VideoContainerIO {
    private VideoContainerIO() {
    }

    public static void write(Path output, VideoConfig config, List<VideoFrame> frames)
            throws IOException, VideoFormatException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        AWTSequenceEncoder encoder = null;
        boolean finished = false;
        try {
            encoder = AWTSequenceEncoder.createSequenceEncoder(
                    output.toFile(),
                    DovVideoConstants.FRAMES_PER_SECOND);
            int currentFrame = 0;
            for (VideoFrame frame : frames) {
                System.out.printf("\rWriting frame %d / %d", currentFrame++, frames.size());
                encoder.encodeImage(FramePixelCodec.encode(frame));
            }
            System.out.println();
            encoder.finish();
            finished = true;
        } finally {
            if (encoder != null && !finished) {
                try {
                    encoder.finish();
                } catch (IOException ignored) {
                    // Suppress secondary failures on close.
                }
            }
        }
    }

    public static VideoFile read(Path input) throws IOException, VideoFormatException {
        List<VideoFrame> frames = new ArrayList<>();
        try (SeekableByteChannel channel = NIOUtils.readableChannel(input.toFile())) {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            int currentFrame = 0;
            while (true) {
                System.out.printf("\rReading frame %d", currentFrame++);
                Picture picture;
                try {
                    picture = grab.getNativeFrame();
                } catch (IOException e) {
                    if (frames.isEmpty()) {
                        throw e;
                    }
                    String message = e.getMessage();
                    if (message != null && !message.toLowerCase().contains("eof")) {
                        throw e;
                    }
                    break;
                }
                if (picture == null) {
                    break;
                }
                VideoFrame frame = FramePixelCodec.decode(AWTUtil.toBufferedImage(picture));
                frames.add(frame);
            }
            System.out.println();
        } catch (JCodecException e) {
            throw new IOException("Failed to decode video using JCodec", e);
        }
        VideoConfig config = new VideoConfig(
                DovVideoConstants.FORMAT_VERSION,
                DovVideoConstants.FRAME_WIDTH,
                DovVideoConstants.FRAME_HEIGHT,
                DovVideoConstants.COLOR_CHANNELS
        );
        return new VideoFile(config, frames);
    }
}
