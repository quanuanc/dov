package dev.cheng.dovsender;

import java.nio.file.Path;

public class AppMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java -jar video-transfer-a.jar <file-to-send>");
            System.exit(1);
        }
        Path file = Path.of(args[0]);

        ProtocolConfig cfg = ProtocolConfig.defaults();
        GLRenderer renderer = new GLRenderer(cfg);
        FrameEncoder encoder = new FrameEncoder(cfg);
        FECEncoder fec = new FECEncoder(cfg); // stub for now

        FileSender sender = new FileSender(file, cfg, encoder, fec, renderer);
        sender.startAndSend();
    }
}
