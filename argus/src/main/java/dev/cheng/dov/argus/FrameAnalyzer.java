package dev.cheng.dov.argus;

import dev.cheng.dov.protocol.Constants;
import dev.cheng.dov.protocol.codec.FrameCodec;
import dev.cheng.dov.protocol.frame.FrameDetector;
import dev.cheng.dov.protocol.frame.FrameHeader;
import dev.cheng.dov.protocol.frame.FrameType;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 帧分析器
 */
public class FrameAnalyzer {

    private final FrameDetector detector = new FrameDetector();
    private final FrameCodec codec = new FrameCodec();
    private BufferedImage scaledBuffer;
    private int lastOffsetX = 0;
    private int lastOffsetY = 0;
    private boolean hasLastOffset = false;

    public AnalyzedFrame analyze(BufferedImage image) {
        HeaderAnalysis analysis = analyzeHeader(image);
        if (analysis == null) {
            return null;
        }

        byte[] payload = decodePayload(analysis);
        if (payload == null) {
            return null;
        }
        return new AnalyzedFrame(analysis.header(), payload);
    }

    public static class AnalyzedFrame {
        private final FrameHeader header;
        private final byte[] data;

        public AnalyzedFrame(FrameHeader header, byte[] data) {
            this.header = header;
            this.data = data;
        }

        public FrameHeader getHeader() {
            return header;
        }

        public byte[] getData() {
            return data;
        }
    }

    public HeaderAnalysis analyzeHeader(BufferedImage image) {
        BufferedImage analysisImage = normalizeImage(image);
        FrameDetector.DetectionResult result = detector.detect(analysisImage);

        HeaderMatch match = null;
        if (result.isValid()) {
            match = decodeHeaderWithRetry(analysisImage, result.getOffsetX(), result.getOffsetY(), 2);
        }
        if (match == null && hasLastOffset) {
            match = decodeHeaderWithRetry(analysisImage, lastOffsetX, lastOffsetY, 4);
        }
        if (match == null) {
            match = decodeHeaderWithRetry(analysisImage, 0, 0, 8);
        }
        if (match == null) {
            return null;
        }

        lastOffsetX = match.offsetX();
        lastOffsetY = match.offsetY();
        hasLastOffset = true;

        return new HeaderAnalysis(analysisImage, match.header(), match.offsetX(), match.offsetY());
    }

    public byte[] decodePayload(HeaderAnalysis analysis) {
        FrameHeader header = analysis.header();
        if (header.getFrameType() == FrameType.DATA) {
            return decodeDataWithRetry(analysis.image(), header.getDataLength(),
                    analysis.offsetX(), analysis.offsetY());
        }
        if (header.getDataLength() > 0) {
            return codec.decodeData(analysis.image(), header.getDataLength(),
                    analysis.offsetX(), analysis.offsetY());
        }
        return new byte[0];
    }

    private byte[] decodeDataWithRetry(BufferedImage image, int dataLength, int offsetX, int offsetY) {
        byte[] payload = codec.decodeDataWithCrc(image, dataLength, offsetX, offsetY);
        if (payload != null || Constants.PAYLOAD_RETRY_RANGE <= 0) {
            return payload;
        }

        int range = Constants.PAYLOAD_RETRY_RANGE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                payload = codec.decodeDataWithCrc(image, dataLength, offsetX + dx, offsetY + dy);
                if (payload != null) {
                    lastOffsetX = offsetX + dx;
                    lastOffsetY = offsetY + dy;
                    hasLastOffset = true;
                    return payload;
                }
            }
        }
        return null;
    }

    public static record HeaderAnalysis(BufferedImage image, FrameHeader header, int offsetX, int offsetY) {
    }

    private HeaderMatch decodeHeaderWithRetry(BufferedImage image, int baseOffsetX, int baseOffsetY, int range) {
        HeaderMatch match = tryDecodeHeader(image, baseOffsetX, baseOffsetY);
        if (match != null) {
            return match;
        }

        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                match = tryDecodeHeader(image, baseOffsetX + dx, baseOffsetY + dy);
                if (match != null) {
                    return match;
                }
            }
        }

        return null;
    }

    private BufferedImage normalizeImage(BufferedImage source) {
        if (source.getWidth() == Constants.FRAME_WIDTH
                && source.getHeight() == Constants.FRAME_HEIGHT) {
            return source;
        }

        if (scaledBuffer == null
                || scaledBuffer.getWidth() != Constants.FRAME_WIDTH
                || scaledBuffer.getHeight() != Constants.FRAME_HEIGHT) {
            scaledBuffer = new BufferedImage(Constants.FRAME_WIDTH, Constants.FRAME_HEIGHT, BufferedImage.TYPE_INT_RGB);
        }

        Graphics2D g2d = scaledBuffer.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(source, 0, 0, Constants.FRAME_WIDTH, Constants.FRAME_HEIGHT, null);
        g2d.dispose();
        return scaledBuffer;
    }

    private HeaderMatch tryDecodeHeader(BufferedImage image, int offsetX, int offsetY) {
        FrameHeader header = codec.decodeHeader(image, offsetX, offsetY);
        if (header == null) {
            return null;
        }

        int dataLength = header.getDataLength();
        if (dataLength < 0 || dataLength > Constants.DATA_BYTES_PER_FRAME) {
            return null;
        }

        if (header.getFrameType() == FrameType.DATA
                && dataLength > codec.getPayloadCapacity()) {
            return null;
        }

        return new HeaderMatch(header, offsetX, offsetY);
    }

    private record HeaderMatch(FrameHeader header, int offsetX, int offsetY) {
    }
}
