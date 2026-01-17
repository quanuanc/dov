package dev.cheng.dov.protocol.frame;

import dev.cheng.dov.protocol.Constants;

import java.awt.image.BufferedImage;

/**
 * 帧检测器
 * <p>
 * 通过四角定位标检测有效帧并计算偏移量。
 */
public class FrameDetector {

    /**
     * 检测帧并返回检测结果
     */
    public DetectionResult detect(BufferedImage image) {
        DetectionResult strict = detectWithThresholds(image,
                Constants.CORNER_SEARCH_RANGE,
                Constants.BLACK_THRESHOLD,
                Constants.WHITE_THRESHOLD);
        if (strict.isValid()) {
            return strict;
        }

        int relaxedBlack = Math.min(Constants.BLACK_THRESHOLD + 32, 120);
        int relaxedWhite = Math.max(Constants.WHITE_THRESHOLD - 32, 140);
        int expandedRange = Constants.CORNER_SEARCH_RANGE * 2;
        return detectWithThresholds(image, expandedRange, relaxedBlack, relaxedWhite);
    }

    private DetectionResult detectWithThresholds(BufferedImage image, int range, int blackThreshold, int whiteThreshold) {
        int[] topLeft = FrameLayout.getTopLeftCorner();
        int[] topRight = FrameLayout.getTopRightCorner();
        int[] bottomLeft = FrameLayout.getBottomLeftCorner();
        int[] bottomRight = FrameLayout.getBottomRightCorner();

        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                if (checkCorner(image, topLeft[0] + dx, topLeft[1] + dy, true, blackThreshold, whiteThreshold)
                        && checkCorner(image, topRight[0] + dx, topRight[1] + dy, false, blackThreshold, whiteThreshold)
                        && checkCorner(image, bottomLeft[0] + dx, bottomLeft[1] + dy, false, blackThreshold, whiteThreshold)
                        && checkCorner(image, bottomRight[0] + dx, bottomRight[1] + dy, true, blackThreshold, whiteThreshold)) {
                    return new DetectionResult(true, dx, dy);
                }
            }
        }

        return new DetectionResult(false, 0, 0);
    }

    private boolean checkCorner(BufferedImage image, int cornerX, int cornerY, boolean expectBlack,
                                int blackThreshold, int whiteThreshold) {
        int sampleSize = Constants.CORNER_SIZE / 2;
        int startX = cornerX + (Constants.CORNER_SIZE - sampleSize) / 2;
        int startY = cornerY + (Constants.CORNER_SIZE - sampleSize) / 2;
        int endX = startX + sampleSize;
        int endY = startY + sampleSize;

        if (startX < 0 || startY < 0 || endX > image.getWidth() || endY > image.getHeight()) {
            return false;
        }

        long sum = 0;
        int count = 0;
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int rgb = image.getRGB(x, y);
                int brightness = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                sum += brightness / 3;
                count++;
            }
        }

        int avg = (int) (sum / Math.max(count, 1));
        if (expectBlack) {
            return avg < blackThreshold;
        }
        return avg > whiteThreshold;
    }

    /**
     * 检测结果
     */
    public static class DetectionResult {
        private final boolean valid;
        private final int offsetX;
        private final int offsetY;

        public DetectionResult(boolean valid, int offsetX, int offsetY) {
            this.valid = valid;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public boolean isValid() {
            return valid;
        }

        public int getOffsetX() {
            return offsetX;
        }

        public int getOffsetY() {
            return offsetY;
        }
    }
}
