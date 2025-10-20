package dev.cheng.dov.sender;

import dev.cheng.dov.protocol.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * CLI entry point responsible for turning a file into the v1.0 video container.
 */
public final class SenderApp {
    private SenderApp() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: sender <input-file> <output-video>");
            System.exit(1);
        }

        Path inputFile = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);

        if (!Files.isRegularFile(inputFile)) {
            System.err.println("Input file does not exist: " + inputFile);
            System.exit(1);
        }

        try {
            long fileSize = Files.size(inputFile);
            byte[] sha256 = computeSha256(inputFile);

            int maxChunkSize = DovVideoConstants.MAX_CHUNK_BYTES;
            long dataFrameCount = (fileSize + maxChunkSize - 1L) / maxChunkSize;
            if (dataFrameCount > Integer.MAX_VALUE - 2L) {
                throw new IllegalArgumentException("File is too large for v1.0 container");
            }
            int totalFrames = (int) dataFrameCount + 2; // header + data + end
            System.out.println("Total frames to be created: " + totalFrames);

            TransferMetadata metadata = new TransferMetadata(
                    inputFile.getFileName().toString(),
                    fileSize,
                    sha256
            );
            byte[] metadataBytes = MetadataCodec.encode(metadata);

            if (metadataBytes.length > maxChunkSize) {
                throw new IllegalStateException("Metadata does not fit into a single frame");
            }

            List<VideoFrame> frames = new ArrayList<>(totalFrames);
            frames.add(new VideoFrame(
                    FrameType.HEADER,
                    0,
                    totalFrames,
                    metadataBytes
            ));

            try (InputStream inputStream = Files.newInputStream(inputFile)) {
                byte[] buffer = new byte[maxChunkSize];
                int frameIndex = 1;
                while (true) {
                    int read = inputStream.readNBytes(buffer, 0, buffer.length);
                    if (read == 0) {
                        break;
                    }
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    frames.add(new VideoFrame(
                            FrameType.DATA,
                            frameIndex,
                            totalFrames,
                            chunk
                    ));
                    frameIndex++;
                }
            }

            frames.add(new VideoFrame(
                    FrameType.END,
                    totalFrames - 1,
                    totalFrames,
                    new byte[0]
            ));
            System.out.println("All frames created, size: " + frames.size());

            if (frames.size() != totalFrames) {
                throw new IllegalStateException("Unexpected frame count: expected "
                        + totalFrames + " but built " + frames.size());
            }

            VideoContainerIO.write(outputFile, VideoConfig.defaultConfig(), frames);

            System.out.printf(
                    "Encoded %s (%d bytes) into %s using %d frames.%nSHA-256: %s%n",
                    metadata.fileName(),
                    metadata.fileSize(),
                    outputFile,
                    frames.size(),
                    HexFormat.of().formatHex(metadata.sha256())
            );
        } catch (IOException | NoSuchAlgorithmException | VideoFormatException e) {
            System.err.println("Encoding failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static byte[] computeSha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }
}
