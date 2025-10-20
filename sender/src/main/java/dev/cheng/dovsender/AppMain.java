package dev.cheng.dovsender;

import java.nio.file.Path;

public class AppMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: mvn -q -pl sender org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=dev.cheng.dovsender.AppMain -Dexec.args=\"<file> [options]\"");
            System.out.println("Options:");
            System.out.println("  --display-index N     Fullscreen on monitor index N (default primary)");
            System.out.println("  --vsync               Enable VSync (default off)");
            System.out.println("  --no-hide-cursor      Do not hide cursor");
            System.out.println("  --warmup-sec S        Warmup seconds before sending (default 3)");
            System.out.println("  --width W --height H  Override resolution (default 1920x1080)");
            System.out.println("  --fps F               Override fps (default 60)");
            System.exit(1);
        }
        Path file = Path.of(args[0]);

        // defaults
        int targetDisplay = -1; // -1 primary
        boolean vsync = false;
        boolean hideCursor = true;
        int warmupSec = 3;
        int width = 1920, height = 1080, fps = 60;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--display-index": targetDisplay = Integer.parseInt(args[++i]); break;
                case "--vsync": vsync = true; break;
                case "--no-hide-cursor": hideCursor = false; break;
                case "--warmup-sec": warmupSec = Integer.parseInt(args[++i]); break;
                case "--width": width = Integer.parseInt(args[++i]); break;
                case "--height": height = Integer.parseInt(args[++i]); break;
                case "--fps": fps = Integer.parseInt(args[++i]); break;
                default: System.out.println("Unknown option: " + args[i]); System.exit(2);
            }
        }

        ProtocolConfig cfg = ProtocolConfig.make(width, height, fps, null, null, null);
        GLRenderer renderer = new GLRenderer(cfg).withTargetDisplay(targetDisplay).withVsync(vsync).withHideCursor(hideCursor);
        FrameEncoder encoder = new FrameEncoder(cfg);
        FECEncoder fec = new FECEncoder(cfg); // stub for now

        FileSender sender = new FileSender(file, cfg, encoder, fec, renderer);
        sender.startAndSend(warmupSec);
    }
}
