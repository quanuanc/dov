package dev.cheng.dov.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 全局常量定义
 */
public final class Constants {

    private static final Properties PROPERTIES = loadProperties();

    private Constants() {
    }

    // === 帧分辨率 ===
    public static final int FRAME_WIDTH = readInt("dov.frameWidth", 1920);
    public static final int FRAME_HEIGHT = readInt("dov.frameHeight", 1080);

    // === 像素块 ===
    public static final int BLOCK_SIZE = readInt("dov.blockSize", 8);

    // === 帧布局 ===
    public static final int SAFE_MARGIN = readInt("dov.safeMargin", 16); // 安全边距 (像素)
    public static final int CORNER_SIZE = readInt("dov.cornerSize", 32); // 角标大小 (像素)

    // === 计算得出的布局参数 ===
    // 内容区域起始位置 (安全边距之后)
    public static final int CONTENT_START_X = SAFE_MARGIN;
    public static final int CONTENT_START_Y = SAFE_MARGIN;
    // 内容区域尺寸
    public static final int CONTENT_WIDTH = FRAME_WIDTH - 2 * SAFE_MARGIN;
    public static final int CONTENT_HEIGHT = FRAME_HEIGHT - 2 * SAFE_MARGIN;
    // 块网格尺寸
    public static final int GRID_COLS = CONTENT_WIDTH / BLOCK_SIZE;   // 236
    public static final int GRID_ROWS = CONTENT_HEIGHT / BLOCK_SIZE;  // 131

    // === 角标位置 (相对于内容区域，以块为单位) ===
    public static final int CORNER_BLOCKS = CORNER_SIZE / BLOCK_SIZE; // 4 blocks

    // === 帧头区域 ===
    public static final int HEADER_START_ROW = CORNER_BLOCKS;  // 角标下方
    public static final int HEADER_ROWS = readInt("dov.headerRows", 3);  // 帧头行数
    public static final int HEADER_SIZE_BYTES = 10;            // 帧头字节数

    // === 数据区域 ===
    public static final int DATA_START_ROW = HEADER_START_ROW + HEADER_ROWS;
    // 预留底部角标和校验区
    public static final int CHECKSUM_ROWS = readInt("dov.checksumRows", 2);
    public static final int DATA_END_ROW = GRID_ROWS - CORNER_BLOCKS - CHECKSUM_ROWS;
    public static final int DATA_ROWS = DATA_END_ROW - DATA_START_ROW;
    // 数据区可用块数
    public static final int DATA_COLS = GRID_COLS - 2 * CORNER_BLOCKS; // 扣除左右角标区域
    public static final int DATA_BLOCKS_PER_FRAME = DATA_ROWS * DATA_COLS;
    // 每帧数据容量 (字节)
    public static final int DATA_BYTES_PER_FRAME = DATA_BLOCKS_PER_FRAME / 8;

    // === 协议常量 ===
    public static final byte[] MAGIC = {0x44, 0x56};  // "DV"
    public static final int START_PARAMS_BYTES = 4;   // START 帧参数长度
    public static final int START_FLAG_DIRECTORY = 0x01; // 发送目录标记

    // === 发送参数 ===
    public static final int TARGET_FPS = readInt("dov.targetFps", 30); // 目标帧率
    public static final int START_REPEAT = 5;         // START 帧重复次数
    public static final int DATA_REPEAT = 1;          // DATA 帧重复次数
    public static final int EOF_REPEAT = 5;           // EOF 帧重复次数
    public static final int TAIL_FRAMES = readInt("dov.tailFrames", 5);   // 尾部加重帧数
    public static final int TAIL_REPEAT = readInt("dov.tailRepeat", 3);   // 尾部重复次数
    public static final int FRAME_INTERVAL_MS = 1000 / TARGET_FPS; // 帧间隔
    public static final int IDLE_INTERVAL_MS = 200;   // IDLE 帧间隔
    public static final int EOF_GRACE_MS = readInt("dov.eofGraceMs", 1500); // EOF 后等待补齐

    // === 检测阈值 ===
    public static final int BLACK_THRESHOLD = 64;     // 黑色判定阈值
    public static final int WHITE_THRESHOLD = 192;    // 白色判定阈值
    public static final int CORNER_SEARCH_RANGE = 8;  // 角标搜索范围（像素）

    // === 接收参数 ===
    public static final int FRAME_TIMEOUT_MS = 10_000;     // 帧超时 (10s)
    public static final int CONNECTION_TIMEOUT_MS = 60_000; // 连接超时 (60s)
    public static final int FRAME_QUEUE_SIZE = 10;          // 采集队列长度

    // === 颜色常量 ===
    public static final int COLOR_BLACK = 0xFF000000;
    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_GRAY = 0xFF808080;

    static {
        if (FRAME_WIDTH <= 0 || FRAME_HEIGHT <= 0) {
            throw new IllegalArgumentException("Invalid frame size");
        }
        if (BLOCK_SIZE <= 0) {
            throw new IllegalArgumentException("Invalid block size");
        }
        if (SAFE_MARGIN < 0 || CORNER_SIZE <= 0) {
            throw new IllegalArgumentException("Invalid layout parameters");
        }
        if (SAFE_MARGIN % BLOCK_SIZE != 0 || CORNER_SIZE % BLOCK_SIZE != 0) {
            throw new IllegalArgumentException("Margins must align with block size");
        }
        if (CONTENT_WIDTH <= 0 || CONTENT_HEIGHT <= 0
                || CONTENT_WIDTH % BLOCK_SIZE != 0
                || CONTENT_HEIGHT % BLOCK_SIZE != 0) {
            throw new IllegalArgumentException("Content area must align with block size");
        }
        if (HEADER_ROWS <= 0 || CHECKSUM_ROWS < 0) {
            throw new IllegalArgumentException("Invalid header/checksum rows");
        }
        if (DATA_ROWS <= 0 || DATA_COLS <= 0) {
            throw new IllegalArgumentException("Invalid data region");
        }
        if (TARGET_FPS <= 0) {
            throw new IllegalArgumentException("Invalid target FPS");
        }
        if (TAIL_FRAMES < 0 || TAIL_REPEAT < 1) {
            throw new IllegalArgumentException("Invalid tail repeat settings");
        }
        if (EOF_GRACE_MS < 0) {
            throw new IllegalArgumentException("Invalid EOF grace");
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        Path configPath = Paths.get(System.getProperty("dov.config", "dov.properties"));
        if (Files.isRegularFile(configPath)) {
            try (InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            } catch (IOException ignored) {
                // ignore
            }
        }
        return properties;
    }

    private static int readInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
