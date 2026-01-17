package dev.cheng.dov.protocol.file;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * 文件重组器
 * <p>
 * 将分片数据按顺序写入目标文件，并提供校验计算。
 */
public class FileAssembler {

    /**
     * 组装文件
     *
     * @param outputDir   输出目录
     * @param fileName    文件名
     * @param totalFrames 总帧数
     * @param frameData   帧数据映射
     * @param listener    进度监听器
     * @return 输出文件路径
     */
    public Path assemble(Path outputDir, String fileName, int totalFrames,
                         Map<Integer, byte[]> frameData, ProgressListener listener) throws IOException {
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(fileName);
        return assemble(outputFile, totalFrames, frameData, listener);
    }

    /**
     * 组装文件到指定路径
     *
     * @param outputFile  输出文件路径
     * @param totalFrames 总帧数
     * @param frameData   帧数据映射
     * @param listener    进度监听器
     * @return 输出文件路径
     */
    public Path assemble(Path outputFile, int totalFrames,
                         Map<Integer, byte[]> frameData, ProgressListener listener) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (OutputStream os = Files.newOutputStream(outputFile)) {
            for (int i = 0; i < totalFrames; i++) {
                byte[] chunk = frameData.get(i);
                if (chunk == null) {
                    throw new IOException("Missing frame: " + i);
                }
                os.write(chunk);
                if (listener != null) {
                    listener.onProgress(i + 1, totalFrames);
                }
            }
        }

        return outputFile;
    }

    /**
     * 计算文件 SHA-256
     */
    public byte[] computeSha256(Path filePath) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        try (var is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return digest.digest();
    }

    public interface ProgressListener {
        void onProgress(int currentFrame, int totalFrames);
    }
}
