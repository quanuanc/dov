package dev.cheng.dov.protocol.codec;

import dev.cheng.dov.protocol.Constants;
import dev.cheng.dov.protocol.frame.FrameHeader;
import dev.cheng.dov.protocol.frame.FrameLayout;
import dev.cheng.dov.protocol.frame.FrameType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * 帧编解码器
 * <p>
 * 负责将数据编码为视频帧图像，以及从图像解码数据
 */
public class FrameCodec {

    /**
     * 创建基础帧图像（包含安全边距和四角定位标）
     */
    public BufferedImage createBaseFrame() {
        BufferedImage image = new BufferedImage(
                Constants.FRAME_WIDTH,
                Constants.FRAME_HEIGHT,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = image.createGraphics();

        // 填充安全边距为灰色
        g.setColor(new Color(Constants.COLOR_GRAY));
        g.fillRect(0, 0, Constants.FRAME_WIDTH, Constants.FRAME_HEIGHT);

        // 内容区域填充为中灰
        g.setColor(new Color(0x606060));
        g.fillRect(
                Constants.SAFE_MARGIN,
                Constants.SAFE_MARGIN,
                Constants.CONTENT_WIDTH,
                Constants.CONTENT_HEIGHT
        );

        // 绘制四角定位标
        drawCorners(g);

        g.dispose();
        return image;
    }

    /**
     * 绘制四角定位标
     */
    private void drawCorners(Graphics2D g) {
        // 左上角 - 黑色
        int[] topLeft = FrameLayout.getTopLeftCorner();
        g.setColor(Color.BLACK);
        g.fillRect(topLeft[0], topLeft[1], Constants.CORNER_SIZE, Constants.CORNER_SIZE);

        // 右上角 - 白色
        int[] topRight = FrameLayout.getTopRightCorner();
        g.setColor(Color.WHITE);
        g.fillRect(topRight[0], topRight[1], Constants.CORNER_SIZE, Constants.CORNER_SIZE);

        // 左下角 - 白色
        int[] bottomLeft = FrameLayout.getBottomLeftCorner();
        g.setColor(Color.WHITE);
        g.fillRect(bottomLeft[0], bottomLeft[1], Constants.CORNER_SIZE, Constants.CORNER_SIZE);

        // 右下角 - 黑色
        int[] bottomRight = FrameLayout.getBottomRightCorner();
        g.setColor(Color.BLACK);
        g.fillRect(bottomRight[0], bottomRight[1], Constants.CORNER_SIZE, Constants.CORNER_SIZE);
    }

    /**
     * 编码 IDLE 帧
     */
    public BufferedImage encodeIdleFrame() {
        BufferedImage image = createBaseFrame();
        FrameHeader header = new FrameHeader(FrameType.IDLE, 0, 0);
        encodeHeader(image, header);
        return image;
    }

    /**
     * 编码 START 帧
     *
     * @param fileName    文件名
     * @param fileSize    文件大小
     * @param totalFrames 总帧数
     * @param sha256      文件 SHA-256 校验和
     * @param flags       传输标记
     */
    public BufferedImage encodeStartFrame(String fileName, long fileSize, int totalFrames, byte[] sha256, int flags) {
        BufferedImage image = createBaseFrame();

        // 构建 START 帧数据
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        int dataLength = 1 + fileNameBytes.length + 8 + 4 + 32 + Constants.START_PARAMS_BYTES;

        ByteBuffer buffer = ByteBuffer.allocate(dataLength);
        buffer.put((byte) fileNameBytes.length);
        buffer.put(fileNameBytes);
        buffer.putLong(fileSize);
        buffer.putInt(totalFrames);
        buffer.put(sha256);
        buffer.putInt(flags);

        byte[] data = buffer.array();

        // 编码帧头
        FrameHeader header = new FrameHeader(FrameType.START, 0, dataLength);
        encodeHeader(image, header);

        // 编码数据区
        encodeData(image, data);

        return image;
    }

    /**
     * 编码 DATA 帧
     *
     * @param frameIndex 帧序号
     * @param payload    数据负载
     */
    public BufferedImage encodeDataFrame(int frameIndex, byte[] payload) {
        BufferedImage image = createBaseFrame();

        // 计算 CRC32
        CRC32 crc = new CRC32();
        crc.update(payload);
        int crcValue = (int) crc.getValue();

        // 构建完整数据（负载 + CRC32）
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + 4);
        buffer.put(payload);
        buffer.putInt(crcValue);
        byte[] data = buffer.array();

        // 编码帧头
        FrameHeader header = new FrameHeader(FrameType.DATA, frameIndex, payload.length);
        encodeHeader(image, header);

        // 编码数据区
        encodeData(image, data);

        return image;
    }

    /**
     * 编码 EOF 帧
     *
     * @param totalFrames 总帧数
     * @param sha256      文件 SHA-256 校验和
     */
    public BufferedImage encodeEofFrame(int totalFrames, byte[] sha256) {
        BufferedImage image = createBaseFrame();

        // 构建 EOF 帧数据
        ByteBuffer buffer = ByteBuffer.allocate(4 + 32);
        buffer.putInt(totalFrames);
        buffer.put(sha256);
        byte[] data = buffer.array();

        // 编码帧头
        FrameHeader header = new FrameHeader(FrameType.EOF, 0, data.length);
        encodeHeader(image, header);

        // 编码数据区
        encodeData(image, data);

        return image;
    }

    /**
     * 编码帧头到图像
     */
    private void encodeHeader(BufferedImage image, FrameHeader header) {
        byte[] headerBytes = header.toBytes();
        int[] bits = BlockCodec.bytesToBits(headerBytes);

        for (int i = 0; i < bits.length; i++) {
            int[] blockPos = FrameLayout.headerBitIndexToBlock(i);
            int pixelX = Constants.CONTENT_START_X + blockPos[0] * Constants.BLOCK_SIZE;
            int pixelY = Constants.CONTENT_START_Y + blockPos[1] * Constants.BLOCK_SIZE;

            int color = (bits[i] == 0) ? Constants.COLOR_BLACK : Constants.COLOR_WHITE;
            fillBlock(image, pixelX, pixelY, color);
        }
    }

    /**
     * 编码数据区到图像
     */
    private void encodeData(BufferedImage image, byte[] data) {
        int[] bits = BlockCodec.bytesToBits(data);

        for (int i = 0; i < bits.length && i < Constants.DATA_BLOCKS_PER_FRAME; i++) {
            int[] blockPos = FrameLayout.dataBitIndexToBlock(i);
            int pixelX = Constants.CONTENT_START_X + blockPos[0] * Constants.BLOCK_SIZE;
            int pixelY = Constants.CONTENT_START_Y + blockPos[1] * Constants.BLOCK_SIZE;

            int color = (bits[i] == 0) ? Constants.COLOR_BLACK : Constants.COLOR_WHITE;
            fillBlock(image, pixelX, pixelY, color);
        }
    }

    /**
     * 填充一个 8x8 像素块
     */
    private void fillBlock(BufferedImage image, int pixelX, int pixelY, int color) {
        for (int dy = 0; dy < Constants.BLOCK_SIZE; dy++) {
            for (int dx = 0; dx < Constants.BLOCK_SIZE; dx++) {
                if (pixelX + dx < Constants.FRAME_WIDTH && pixelY + dy < Constants.FRAME_HEIGHT) {
                    image.setRGB(pixelX + dx, pixelY + dy, color);
                }
            }
        }
    }

    /**
     * 获取每帧数据容量（字节）
     */
    public int getPayloadCapacity() {
        // 数据区容量减去 CRC32 的 4 字节
        return Constants.DATA_BYTES_PER_FRAME - 4;
    }

    /**
     * 解码帧头
     *
     * @param image   源图像
     * @param offsetX 水平偏移（像素）
     * @param offsetY 垂直偏移（像素）
     * @return FrameHeader，如果解码失败返回 null
     */
    public FrameHeader decodeHeader(BufferedImage image, int offsetX, int offsetY) {
        int bitCount = Constants.HEADER_SIZE_BYTES * 8;
        int[] bits = new int[bitCount];

        for (int i = 0; i < bitCount; i++) {
            int[] blockPos = FrameLayout.headerBitIndexToBlock(i);
            int pixelX = Constants.CONTENT_START_X + blockPos[0] * Constants.BLOCK_SIZE + offsetX;
            int pixelY = Constants.CONTENT_START_Y + blockPos[1] * Constants.BLOCK_SIZE + offsetY;
            bits[i] = BlockCodec.decodeBlockAt(image, pixelX, pixelY);
        }

        byte[] headerBytes = BlockCodec.bitsToBytes(bits);
        return FrameHeader.fromBytes(headerBytes);
    }

    /**
     * 解码数据区（不包含 CRC 校验）
     *
     * @param image      源图像
     * @param dataLength 数据长度（字节）
     * @param offsetX    水平偏移（像素）
     * @param offsetY    垂直偏移（像素）
     * @return 数据字节数组，失败返回 null
     */
    public byte[] decodeData(BufferedImage image, int dataLength, int offsetX, int offsetY) {
        return decodeBytes(image, dataLength, offsetX, offsetY);
    }

    /**
     * 解码数据区并验证 CRC32
     *
     * @param image      源图像
     * @param dataLength 数据长度（字节，不含 CRC）
     * @param offsetX    水平偏移（像素）
     * @param offsetY    垂直偏移（像素）
     * @return 解码后的数据负载，CRC 校验失败返回 null
     */
    public byte[] decodeDataWithCrc(BufferedImage image, int dataLength, int offsetX, int offsetY) {
        int totalLength = dataLength + 4;
        byte[] raw = decodeBytes(image, totalLength, offsetX, offsetY);
        if (raw == null || raw.length < totalLength) {
            return null;
        }

        byte[] payload = Arrays.copyOf(raw, dataLength);
        int expectedCrc = ByteBuffer.wrap(raw, dataLength, 4).getInt();

        CRC32 crc = new CRC32();
        crc.update(payload);
        int actualCrc = (int) crc.getValue();
        if (expectedCrc != actualCrc) {
            return null;
        }

        return payload;
    }

    private byte[] decodeBytes(BufferedImage image, int byteLength, int offsetX, int offsetY) {
        int maxBytes = Constants.DATA_BLOCKS_PER_FRAME / 8;
        if (byteLength <= 0 || byteLength > maxBytes) {
            return null;
        }

        int bitCount = byteLength * 8;
        int[] bits = new int[bitCount];
        for (int i = 0; i < bitCount; i++) {
            int[] blockPos = FrameLayout.dataBitIndexToBlock(i);
            int pixelX = Constants.CONTENT_START_X + blockPos[0] * Constants.BLOCK_SIZE + offsetX;
            int pixelY = Constants.CONTENT_START_Y + blockPos[1] * Constants.BLOCK_SIZE + offsetY;
            bits[i] = BlockCodec.decodeBlockAt(image, pixelX, pixelY);
        }
        return BlockCodec.bitsToBytes(bits);
    }
}
