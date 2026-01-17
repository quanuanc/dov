package dev.cheng.dov.protocol.codec;

import dev.cheng.dov.protocol.Constants;

import java.awt.image.BufferedImage;

/**
 * 8x8 像素块编解码器
 * <p>
 * 编码：1 bit -> 8x8 像素块（黑/白）
 * 解码：8x8 像素块 -> 1 bit（根据平均亮度判定）
 */
public class BlockCodec {

    private BlockCodec() {
    }

    /**
     * 在图像上编码一个块
     *
     * @param image  目标图像
     * @param blockX 块的 X 坐标（以块为单位）
     * @param blockY 块的 Y 坐标（以块为单位）
     * @param bit    要编码的位（0 或 1）
     */
    public static void encodeBlock(BufferedImage image, int blockX, int blockY, int bit) {
        int pixelX = blockX * Constants.BLOCK_SIZE;
        int pixelY = blockY * Constants.BLOCK_SIZE;
        int color = (bit == 0) ? Constants.COLOR_BLACK : Constants.COLOR_WHITE;

        for (int dy = 0; dy < Constants.BLOCK_SIZE; dy++) {
            for (int dx = 0; dx < Constants.BLOCK_SIZE; dx++) {
                image.setRGB(pixelX + dx, pixelY + dy, color);
            }
        }
    }

    /**
     * 从图像解码一个块
     *
     * @param image  源图像
     * @param blockX 块的 X 坐标（以块为单位）
     * @param blockY 块的 Y 坐标（以块为单位）
     * @return 解码的位（0 或 1）
     */
    public static int decodeBlock(BufferedImage image, int blockX, int blockY) {
        int pixelX = blockX * Constants.BLOCK_SIZE;
        int pixelY = blockY * Constants.BLOCK_SIZE;
        return decodeBlockAt(image, pixelX, pixelY);
    }

    /**
     * 计算 RGB 值的亮度
     */
    private static int getBrightness(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r + g + b) / 3;
    }

    /**
     * 从图像解码一个块（像素坐标）
     *
     * @param image  源图像
     * @param pixelX 块的像素 X 坐标
     * @param pixelY 块的像素 Y 坐标
     * @return 解码的位（0 或 1）
     */
    public static int decodeBlockAt(BufferedImage image, int pixelX, int pixelY) {
        int startX = Math.max(0, pixelX);
        int startY = Math.max(0, pixelY);
        int endX = Math.min(image.getWidth(), pixelX + Constants.BLOCK_SIZE);
        int endY = Math.min(image.getHeight(), pixelY + Constants.BLOCK_SIZE);

        if (startX >= endX || startY >= endY) {
            return 0;
        }

        long sum = 0;
        int count = 0;
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int rgb = image.getRGB(x, y);
                int brightness = getBrightness(rgb);
                sum += brightness;
                count++;
            }
        }

        int avg = (int) (sum / Math.max(count, 1));
        return (avg >= 128) ? 1 : 0;
    }

    /**
     * 将字节数组编码为位数组
     */
    public static int[] bytesToBits(byte[] bytes) {
        int[] bits = new int[bytes.length * 8];
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                bits[i * 8 + j] = (bytes[i] >> (7 - j)) & 1;
            }
        }
        return bits;
    }

    /**
     * 将位数组解码为字节数组
     */
    public static byte[] bitsToBytes(int[] bits) {
        if (bits.length % 8 != 0) {
            throw new IllegalArgumentException("Bits length must be multiple of 8");
        }
        byte[] bytes = new byte[bits.length / 8];
        for (int i = 0; i < bytes.length; i++) {
            int value = 0;
            for (int j = 0; j < 8; j++) {
                value = (value << 1) | (bits[i * 8 + j] & 1);
            }
            bytes[i] = (byte) value;
        }
        return bytes;
    }
}
