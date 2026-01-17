package dev.cheng.dov.protocol.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件分块器
 * <p>
 * 将文件分割为固定大小的块，并计算 SHA-256 校验和
 */
public class FileChunker {

    private final int chunkSize;

    public FileChunker(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * 分块结果
     */
    public static class ChunkResult {
        private final List<byte[]> chunks;
        private final byte[] sha256;
        private final long fileSize;
        private final String fileName;

        public ChunkResult(List<byte[]> chunks, byte[] sha256, long fileSize, String fileName) {
            this.chunks = chunks;
            this.sha256 = sha256;
            this.fileSize = fileSize;
            this.fileName = fileName;
        }

        public List<byte[]> getChunks() {
            return chunks;
        }

        public byte[] getSha256() {
            return sha256;
        }

        public long getFileSize() {
            return fileSize;
        }

        public String getFileName() {
            return fileName;
        }

        public int getTotalFrames() {
            return chunks.size();
        }
    }

    /**
     * 分块文件
     *
     * @param filePath 文件路径
     * @return 分块结果
     */
    public ChunkResult chunkFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        long fileSize = Files.size(filePath);

        List<byte[]> chunks = new ArrayList<>();
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                // 更新校验和
                digest.update(buffer, 0, bytesRead);

                // 创建块
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                chunks.add(chunk);
            }
        }

        byte[] sha256 = digest.digest();
        return new ChunkResult(chunks, sha256, fileSize, fileName);
    }

    /**
     * 计算需要的帧数
     *
     * @param fileSize 文件大小
     * @return 帧数
     */
    public int calculateFrameCount(long fileSize) {
        return (int) Math.ceil((double) fileSize / chunkSize);
    }
}
