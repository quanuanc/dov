package dev.cheng.dov.protocol.frame;

import dev.cheng.dov.protocol.Constants;

/**
 * 帧布局计算工具
 * <p>
 * 帧结构：
 * - 安全边距 (16像素)
 * - 四角定位标 (32x32像素)
 * - 帧头区域 (3行块)
 * - 数据区域
 * - 校验区域 (2行块)
 */
public class FrameLayout {

    private FrameLayout() {
    }

    // === 角标位置（像素坐标）===

    /**
     * 获取左上角标的像素位置
     */
    public static int[] getTopLeftCorner() {
        return new int[]{Constants.SAFE_MARGIN, Constants.SAFE_MARGIN};
    }

    /**
     * 获取右上角标的像素位置
     */
    public static int[] getTopRightCorner() {
        return new int[]{
                Constants.FRAME_WIDTH - Constants.SAFE_MARGIN - Constants.CORNER_SIZE,
                Constants.SAFE_MARGIN
        };
    }

    /**
     * 获取左下角标的像素位置
     */
    public static int[] getBottomLeftCorner() {
        return new int[]{
                Constants.SAFE_MARGIN,
                Constants.FRAME_HEIGHT - Constants.SAFE_MARGIN - Constants.CORNER_SIZE
        };
    }

    /**
     * 获取右下角标的像素位置
     */
    public static int[] getBottomRightCorner() {
        return new int[]{
                Constants.FRAME_WIDTH - Constants.SAFE_MARGIN - Constants.CORNER_SIZE,
                Constants.FRAME_HEIGHT - Constants.SAFE_MARGIN - Constants.CORNER_SIZE
        };
    }

    // === 数据区域计算 ===

    /**
     * 获取帧头区域起始块坐标（相对于内容区域）
     */
    public static int getHeaderStartRow() {
        return Constants.CORNER_BLOCKS;
    }

    /**
     * 获取帧头区域的行数
     */
    public static int getHeaderRows() {
        return Constants.HEADER_ROWS;
    }

    /**
     * 获取数据区域起始块行号
     */
    public static int getDataStartRow() {
        return Constants.DATA_START_ROW;
    }

    /**
     * 获取数据区域起始块列号（跳过左侧角标区域）
     */
    public static int getDataStartCol() {
        return Constants.CORNER_BLOCKS;
    }

    /**
     * 获取数据区域每行可用的块数
     */
    public static int getDataColsPerRow() {
        return Constants.DATA_COLS;
    }

    /**
     * 获取数据区域的总行数
     */
    public static int getDataRows() {
        return Constants.DATA_ROWS;
    }

    /**
     * 获取每帧可承载的数据字节数
     */
    public static int getPayloadCapacity() {
        return Constants.DATA_BYTES_PER_FRAME;
    }

    /**
     * 将块坐标（相对于内容区域）转换为像素坐标
     *
     * @param blockX 块的 X 坐标
     * @param blockY 块的 Y 坐标
     * @return 像素坐标 [x, y]
     */
    public static int[] blockToPixel(int blockX, int blockY) {
        return new int[]{
                Constants.CONTENT_START_X + blockX * Constants.BLOCK_SIZE,
                Constants.CONTENT_START_Y + blockY * Constants.BLOCK_SIZE
        };
    }

    /**
     * 将数据索引转换为块坐标
     *
     * @param bitIndex 数据位索引
     * @return 块坐标 [blockX, blockY]（相对于内容区域）
     */
    public static int[] dataBitIndexToBlock(int bitIndex) {
        int row = bitIndex / Constants.DATA_COLS;
        int col = bitIndex % Constants.DATA_COLS;
        return new int[]{
                Constants.CORNER_BLOCKS + col,
                Constants.DATA_START_ROW + row
        };
    }

    /**
     * 将帧头位索引转换为块坐标
     *
     * @param bitIndex 帧头位索引
     * @return 块坐标 [blockX, blockY]（相对于内容区域）
     */
    public static int[] headerBitIndexToBlock(int bitIndex) {
        int row = bitIndex / Constants.GRID_COLS;
        int col = bitIndex % Constants.GRID_COLS;
        return new int[]{
                col,
                Constants.HEADER_START_ROW + row
        };
    }
}
