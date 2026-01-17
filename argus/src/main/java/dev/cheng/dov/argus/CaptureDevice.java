package dev.cheng.dov.argus;

import dev.cheng.dov.protocol.Constants;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集设备封装
 */
public class CaptureDevice {

    private OpenCVFrameGrabber grabber;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private final Object lock = new Object();

    /**
     * 设备信息
     */
    public static class DeviceInfo {
        private final int id;
        private final String name;

        public DeviceInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 枚举采集设备
     */
    public static List<DeviceInfo> listDevices() {
        List<DeviceInfo> devices = new ArrayList<>();

        try {
            Method method = FrameGrabber.class.getMethod("getDeviceDescriptions");
            Object result = method.invoke(null);
            if (result instanceof String[]) {
                String[] descriptions = (String[]) result;
                for (int i = 0; i < descriptions.length; i++) {
                    devices.add(new DeviceInfo(i, descriptions[i]));
                }
            }
        } catch (Exception ignored) {
            // 忽略，使用默认设备列表
        }

        if (devices.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                devices.add(new DeviceInfo(i, "设备 " + i));
            }
        }

        return devices;
    }

    /**
     * 打开设备
     */
    public void open(int deviceId) throws FrameGrabber.Exception {
        synchronized (lock) {
            close();
            grabber = new OpenCVFrameGrabber(deviceId);
            grabber.setImageWidth(Constants.FRAME_WIDTH);
            grabber.setImageHeight(Constants.FRAME_HEIGHT);
            grabber.setFrameRate(Constants.TARGET_FPS);
            grabber.start();
        }
    }

    /**
     * 读取一帧
     */
    public BufferedImage read() throws FrameGrabber.Exception {
        synchronized (lock) {
            if (grabber == null) {
                return null;
            }
            Frame frame = grabber.grab();
            if (frame == null) {
                return null;
            }
            return converter.getBufferedImage(frame, 1.0, false, null);
        }
    }

    /**
     * 关闭设备
     */
    public void close() {
        synchronized (lock) {
            if (grabber != null) {
                try {
                    grabber.stop();
                } catch (FrameGrabber.Exception ignored) {
                    // ignore
                }
                try {
                    grabber.release();
                } catch (FrameGrabber.Exception ignored) {
                    // ignore
                }
                grabber = null;
            }
        }
    }
}
