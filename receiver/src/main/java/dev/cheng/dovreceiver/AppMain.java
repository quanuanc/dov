package dev.cheng.dovreceiver;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Receiver: read frames from a recorded video file, decode protocol frames, reconstruct the output file.
 *
 * Usage:
 *   mvn -q -pl receiver org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *     -Dexec.mainClass=dev.cheng.dovreceiver.AppMain \
 *     -Dexec.args="<video-file> <output-file>"
 */
public class AppMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: mvn -q -pl receiver org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=dev.cheng.dovreceiver.AppMain -Dexec.args=\"<video-file> <out-file>\"");
            System.exit(1);
        }

        String videoFile = args[0];
        Path outFile = Path.of(args[1]);

        ProtocolConfig cfg = ProtocolConfig.defaults();
        FrameDecoder decoder = new FrameDecoder(cfg);

        avutil.av_log_set_level(avutil.AV_LOG_WARNING);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
             FileAssembler assembler = new FileAssembler(cfg, outFile)) {
            grabber.start();

            OpenCVFrameConverter.ToMat toMat = new OpenCVFrameConverter.ToMat();

            Frame frame;
            while ((frame = grabber.grabImage()) != null) {
                ByteBuffer rgb = toGrayReplicatedRGB(frame, toMat, cfg);
                if (rgb == null) continue;

                FrameDecoder.Header h = decoder.decodeHeader(rgb);
                if (h == null) continue;

                byte[] payload = decoder.decodePayload(rgb, h);
                if (payload == null) continue;

                assembler.ingest(h, payload);
                if (assembler.isComplete()) break;
            }

            grabber.stop();
        }
        System.out.println("Receiver finished.");
    }

    /**
     * Convert any incoming frame to a packed RGB24 ByteBuffer where each pixel has R=G=B=gray.
     * This stabilizes decoding against varying pixel formats by driving the decoder with luma samples.
     */
    private static ByteBuffer toGrayReplicatedRGB(Frame frame, OpenCVFrameConverter.ToMat toMat, ProtocolConfig cfg) {
        Mat src = toMat.convert(frame);
        if (src == null || src.empty()) return null;
        if (src.cols() != cfg.width || src.rows() != cfg.height) return null; // require exact geometry

        Mat gray = new Mat();
        if (src.channels() == 4) {
            Mat bgr = new Mat();
            opencv_imgproc.cvtColor(src, bgr, opencv_imgproc.COLOR_BGRA2BGR);
            opencv_imgproc.cvtColor(bgr, gray, opencv_imgproc.COLOR_BGR2GRAY);
            bgr.release();
        } else if (src.channels() == 3) {
            opencv_imgproc.cvtColor(src, gray, opencv_imgproc.COLOR_BGR2GRAY);
        } else if (src.channels() == 1) {
            gray = src.clone();
        } else {
            return null;
        }

        int w = cfg.width, h = cfg.height;
        byte[] g = new byte[w * h];
        gray.data().get(g);
        ByteBuffer out = ByteBuffer.allocate(w * h * 3);
        for (byte b : g) {
            out.put(b).put(b).put(b);
        }
        out.flip();
        gray.release();
        return out;
    }
}
