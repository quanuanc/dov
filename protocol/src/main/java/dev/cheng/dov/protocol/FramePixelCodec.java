package dev.cheng.dov.protocol;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 * Maps encoded frame messages to visual frames and back.
 */
public final class FramePixelCodec {
    private static final int MODULE_SIZE = DovVideoConstants.MODULE_SIZE;
    private static final int GRID_ROWS = DovVideoConstants.GRID_ROWS;
    private static final int GRID_COLS = DovVideoConstants.GRID_COLS;
    private static final int DATA_ROWS = DovVideoConstants.DATA_ROWS;
    private static final int DATA_COLS = DovVideoConstants.DATA_COLS;
    private static final int SYMBOL_BITS = DovVideoConstants.SYMBOL_BITS;
    private static final int SYMBOLS_PER_FRAME = DovVideoConstants.SYMBOLS_PER_FRAME;
    private static final int MESSAGE_CAPACITY_BYTES = DovVideoConstants.MESSAGE_CAPACITY_BYTES;
    private static final int SYMBOLS_FOR_BYTES = MESSAGE_CAPACITY_BYTES * (Byte.SIZE / SYMBOL_BITS);
    private static final int LENGTH_PREFIX_BYTES = DovVideoConstants.LENGTH_PREFIX_BYTES;
    private static final int MAX_MESSAGE_BYTES = DovVideoConstants.MAX_MESSAGE_BYTES;
    private static final Color[] PALETTE = {
            new Color(10, 10, 10),
            new Color(90, 90, 90),
            new Color(170, 170, 170),
            new Color(245, 245, 245)
    };

    private FramePixelCodec() {
    }

    public static BufferedImage encode(VideoFrame frame) throws VideoFormatException {
        byte[] message = FrameMessageCodec.encode(frame);
        if (message.length > MAX_MESSAGE_BYTES) {
            throw new VideoFormatException("Message exceeds frame capacity: " + message.length);
        }

        ByteBuffer envelope = ByteBuffer.allocate(LENGTH_PREFIX_BYTES + message.length);
        envelope.putShort((short) message.length);
        envelope.put(message);
        byte[] envelopeBytes = envelope.array();

        byte[] symbols = new byte[SYMBOLS_PER_FRAME];
        int symbolIndex = 0;
        for (byte envelopeByte : envelopeBytes) {
            int value = envelopeByte & 0xFF;
            for (int shift = 6; shift >= 0; shift -= 2) {
                if (symbolIndex >= SYMBOLS_FOR_BYTES) {
                    break;
                }
                symbols[symbolIndex++] = (byte) ((value >> shift) & 0x03);
            }
        }
        while (symbolIndex < SYMBOLS_PER_FRAME) {
            symbols[symbolIndex++] = 0;
        }

        BufferedImage image = new BufferedImage(
                DovVideoConstants.FRAME_WIDTH,
                DovVideoConstants.FRAME_HEIGHT,
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = image.createGraphics();
        try {
            for (int row = 0; row < GRID_ROWS; row++) {
                for (int col = 0; col < GRID_COLS; col++) {
                    int symbolValue;
                    if (row == 0 || col == 0) {
                        symbolValue = alignmentSymbol(row, col);
                    } else {
                        int dataRow = row - 1;
                        int dataCol = col - 1;
                        int dataIndex = dataRow * DATA_COLS + dataCol;
                        symbolValue = symbols[Math.min(dataIndex, symbols.length - 1)];
                    }
                    Color color = colorForSymbol(symbolValue);
                    g.setColor(color);
                    g.fillRect(col * MODULE_SIZE, row * MODULE_SIZE, MODULE_SIZE, MODULE_SIZE);
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    public static VideoFrame decode(BufferedImage image) throws VideoFormatException {
        if (image.getWidth() != DovVideoConstants.FRAME_WIDTH
                || image.getHeight() != DovVideoConstants.FRAME_HEIGHT) {
            throw new VideoFormatException("Unexpected frame dimensions: "
                    + image.getWidth() + "x" + image.getHeight());
        }

        byte[] symbols = new byte[SYMBOLS_PER_FRAME];
        int symbolIndex = 0;

        validateAlignment(image);

        for (int row = 1; row < GRID_ROWS; row++) {
            for (int col = 1; col < GRID_COLS; col++) {
                int sampleX = col * MODULE_SIZE + MODULE_SIZE / 2;
                int sampleY = row * MODULE_SIZE + MODULE_SIZE / 2;
                int rgb = image.getRGB(sampleX, sampleY);
                int symbol = symbolFromColor(rgb);
                if (symbolIndex < symbols.length) {
                    symbols[symbolIndex++] = (byte) symbol;
                }
            }
        }

        byte[] envelope = new byte[MESSAGE_CAPACITY_BYTES];
        int envelopeIndex = 0;
        int symbolCursor = 0;
        while (envelopeIndex < envelope.length) {
            int value = 0;
            for (int shift = 6; shift >= 0; shift -= 2) {
                if (symbolCursor >= SYMBOLS_FOR_BYTES) {
                    symbolCursor++;
                    continue;
                }
                int symbol = symbols[Math.min(symbolCursor, symbols.length - 1)] & 0x03;
                value |= symbol << shift;
                symbolCursor++;
            }
            envelope[envelopeIndex++] = (byte) value;
        }

        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        int length = Short.toUnsignedInt(buffer.getShort());
        if (length > MAX_MESSAGE_BYTES) {
            throw new VideoFormatException("Decoded message exceeds capacity: " + length);
        }
        if (length > buffer.remaining()) {
            throw new VideoFormatException("Frame truncated, missing " + (length - buffer.remaining()) + " bytes");
        }
        byte[] message = new byte[length];
        buffer.get(message);
        return FrameMessageCodec.decode(message);
    }

    private static int alignmentSymbol(int row, int col) {
        return ((row + col) & 1) == 0 ? 0 : 3;
    }

    private static Color colorForSymbol(int symbol) {
        return PALETTE[symbol & 0x03];
    }

    private static int symbolFromColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int luminance = (r + g + b) / 3;
        if (luminance < 50) {
            return 0;
        } else if (luminance < 130) {
            return 1;
        } else if (luminance < 210) {
            return 2;
        }
        return 3;
    }

    private static void validateAlignment(BufferedImage image) throws VideoFormatException {
        for (int col = 0; col < GRID_COLS; col++) {
            int sampleX = col * MODULE_SIZE + MODULE_SIZE / 2;
            int sampleY = MODULE_SIZE / 2;
            int symbol = symbolFromColor(image.getRGB(sampleX, sampleY));
            int expected = alignmentSymbol(0, col);
            if (symbol != expected) {
                throw new VideoFormatException("Alignment row check failed at column " + col);
            }
        }
        for (int row = 0; row < GRID_ROWS; row++) {
            int sampleX = MODULE_SIZE / 2;
            int sampleY = row * MODULE_SIZE + MODULE_SIZE / 2;
            int symbol = symbolFromColor(image.getRGB(sampleX, sampleY));
            int expected = alignmentSymbol(row, 0);
            if (symbol != expected) {
                throw new VideoFormatException("Alignment column check failed at row " + row);
            }
        }
    }
}
