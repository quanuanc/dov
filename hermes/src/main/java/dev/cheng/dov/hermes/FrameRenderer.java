package dev.cheng.dov.hermes;

import dev.cheng.dov.protocol.Constants;
import dev.cheng.dov.protocol.codec.FrameCodec;
import dev.cheng.dov.protocol.file.FileChunker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 帧渲染器
 * <p>
 * 负责将文件数据编码为帧图像。
 * 采用按需生成策略，只保存数据块，帧图像在需要时实时生成。
 */
public class FrameRenderer {

    private final FrameCodec frameCodec;
    private final FileChunker fileChunker;

    // 预生成的固定帧
    private Image idleFrame;

    // 文件数据（只保存原始数据，按需生成帧图像）
    private List<byte[]> chunks;
    private String fileName;
    private long fileSize;
    private int totalFrames;
    private byte[] sha256;
    private boolean directoryTransfer;
    private Path tempArchivePath;

    // 缓存当前帧（避免重复生成）
    private int cachedFrameIndex = -1;
    private Image cachedDataFrame;

    public FrameRenderer() {
        this.frameCodec = new FrameCodec();
        this.fileChunker = new FileChunker(frameCodec.getPayloadCapacity());

        // 预生成 IDLE 帧
        generateIdleFrame();
    }

    /**
     * 生成 IDLE 帧
     */
    private void generateIdleFrame() {
        BufferedImage bufferedImage = frameCodec.encodeIdleFrame();
        this.idleFrame = SwingFXUtils.toFXImage(bufferedImage, null);
    }

    /**
     * 准备文件传输
     *
     * @param filePath 文件路径
     * @param listener 进度监听器
     */
    public void prepareFile(Path filePath, PrepareListener listener) throws IOException {
        cleanupTempArchive();
        directoryTransfer = false;
        // 分块文件
        listener.onProgress("正在读取文件...", 0);
        FileChunker.ChunkResult result = fileChunker.chunkFile(filePath);

        this.fileName = result.getFileName();
        this.fileSize = result.getFileSize();
        this.totalFrames = result.getTotalFrames();
        this.sha256 = result.getSha256();
        this.chunks = result.getChunks();

        listener.onProgress("准备完成", 100);
        listener.onComplete();
    }

    /**
     * 准备文件夹传输
     *
     * @param directoryPath 文件夹路径
     * @param listener      进度监听器
     */
    public void prepareDirectory(Path directoryPath, PrepareListener listener) throws IOException {
        cleanupTempArchive();
        directoryTransfer = true;

        listener.onProgress("正在压缩文件夹...", 0);
        tempArchivePath = createZipFromDirectory(directoryPath);

        listener.onProgress("正在读取压缩包...", 10);
        FileChunker.ChunkResult result = fileChunker.chunkFile(tempArchivePath);

        this.fileName = directoryPath.getFileName().toString();
        this.fileSize = result.getFileSize();
        this.totalFrames = result.getTotalFrames();
        this.sha256 = result.getSha256();
        this.chunks = result.getChunks();

        listener.onProgress("准备完成", 100);
        listener.onComplete();
    }

    /**
     * 获取 IDLE 帧
     */
    public Image getIdleFrame() {
        return idleFrame;
    }

    /**
     * 获取 START 帧（按需生成）
     */
    public Image getStartFrame() {
        if (fileName == null) {
            return null;
        }
        int flags = directoryTransfer ? Constants.START_FLAG_DIRECTORY : 0;
        BufferedImage image = frameCodec.encodeStartFrame(fileName, fileSize, totalFrames, sha256, flags);
        return SwingFXUtils.toFXImage(image, null);
    }

    /**
     * 获取指定索引的 DATA 帧（按需生成，带缓存）
     */
    public Image getDataFrame(int index) {
        if (chunks == null || index < 0 || index >= chunks.size()) {
            return null;
        }

        // 使用缓存避免重复生成（因为每帧会重复发送多次）
        if (index == cachedFrameIndex && cachedDataFrame != null) {
            return cachedDataFrame;
        }

        BufferedImage image = frameCodec.encodeDataFrame(index, chunks.get(index));
        cachedDataFrame = SwingFXUtils.toFXImage(image, null);
        cachedFrameIndex = index;

        return cachedDataFrame;
    }

    /**
     * 获取 EOF 帧（按需生成）
     */
    public Image getEofFrame() {
        if (sha256 == null) {
            return null;
        }
        BufferedImage image = frameCodec.encodeEofFrame(totalFrames, sha256);
        return SwingFXUtils.toFXImage(image, null);
    }

    /**
     * 获取总帧数
     */
    public int getTotalFrames() {
        return totalFrames;
    }

    /**
     * 获取文件名
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 获取文件大小
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * 清理数据
     */
    public void clear() {
        this.chunks = null;
        this.fileName = null;
        this.fileSize = 0;
        this.totalFrames = 0;
        this.sha256 = null;
        this.cachedFrameIndex = -1;
        this.cachedDataFrame = null;
        this.directoryTransfer = false;
        cleanupTempArchive();
    }

    private void cleanupTempArchive() {
        if (tempArchivePath != null) {
            try {
                Files.deleteIfExists(tempArchivePath);
            } catch (IOException ignored) {
                // ignore
            }
            tempArchivePath = null;
        }
    }

    private Path createZipFromDirectory(Path directoryPath) throws IOException {
        Path zipPath = Files.createTempFile("dov-", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath, StandardOpenOption.WRITE))) {
            Path root = directoryPath.toAbsolutePath().normalize();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!root.equals(dir)) {
                        String entryName = root.relativize(dir).toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = root.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (InputStream is = Files.newInputStream(file)) {
                        is.transferTo(zos);
                    }
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return zipPath;
    }

    /**
     * 准备进度监听器
     */
    public interface PrepareListener {
        void onProgress(String message, int percent);

        void onComplete();

        void onError(String error);
    }
}
