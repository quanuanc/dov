package dev.cheng.dov.receiver;

import dev.cheng.dov.protocol.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * CLI entry point that reconstructs the original file from a v1.0 video container.
 */
public final class ReceiverApp {
    private ReceiverApp() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: receiver <input-video> <output-file-or-dir>");
            System.exit(1);
        }

        Path inputVideo = Paths.get(args[0]);
        Path outputTarget = Paths.get(args[1]);

        if (!Files.isRegularFile(inputVideo)) {
            System.err.println("Input video does not exist: " + inputVideo);
            System.exit(1);
        }

        try {
            VideoFile videoFile = VideoContainerIO.read(inputVideo);
            VideoConfig config = videoFile.config();
            if (config.version() != DovVideoConstants.FORMAT_VERSION) {
                throw new VideoFormatException("Unsupported video version: " + config.version());
            }

            List<VideoFrame> frames = videoFile.frames();
            if (frames.isEmpty()) {
                throw new VideoFormatException("Video contains no frames");
            }

            VideoFrame headerFrame = frames.getFirst();
            if (headerFrame.type() != FrameType.HEADER) {
                throw new VideoFormatException("First frame is not a HEADER");
            }

            TransferMetadata metadata = MetadataCodec.decode(headerFrame.payload());

            Path outputFile = outputTarget;
            if (Files.exists(outputTarget) && Files.isDirectory(outputTarget)) {
                outputFile = outputTarget.resolve(metadata.fileName());
            }
            Path parent = outputFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalWritten = 0L;

            try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
                for (int i = 1; i < frames.size(); i++) {
                    VideoFrame frame = frames.get(i);
                    if (frame.type() == FrameType.END) {
                        if (i != frames.size() - 1) {
                            throw new VideoFormatException("END frame arrived before final position");
                        }
                        break;
                    }
                    if (frame.type() != FrameType.DATA) {
                        throw new VideoFormatException("Unexpected frame type in data sequence: " + frame.type());
                    }
                    byte[] chunk = frame.payload();
                    if (chunk.length == 0) {
                        continue;
                    }
                    if (totalWritten + chunk.length > metadata.fileSize()) {
                        throw new VideoFormatException("Video contains more data than declared in metadata");
                    }
                    outputStream.write(chunk);
                    digest.update(chunk);
                    totalWritten += chunk.length;
                }
            }

            if (frames.getLast().type() != FrameType.END) {
                throw new VideoFormatException("Video is missing terminating END frame");
            }

            if (totalWritten != metadata.fileSize()) {
                throw new VideoFormatException(
                        "Decoded file length mismatch. Expected " + metadata.fileSize() + " but got " + totalWritten);
            }

            byte[] hash = digest.digest();
            if (!MessageDigest.isEqual(hash, metadata.sha256())) {
                throw new VideoFormatException(
                        "SHA-256 mismatch. Expected " + HexFormat.of().formatHex(metadata.sha256())
                                + " but got " + HexFormat.of().formatHex(hash));
            }

            System.out.printf(
                    "Recovered %s (%d bytes) to %s%nSHA-256: %s%n",
                    metadata.fileName(),
                    metadata.fileSize(),
                    outputFile,
                    HexFormat.of().formatHex(hash)
            );
        } catch (IOException | VideoFormatException | NoSuchAlgorithmException e) {
            System.err.println("Decoding failed: " + e.getMessage());
            System.exit(1);
        }
    }

}
