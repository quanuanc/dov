package dev.cheng.dovsender;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class GLRenderer {
    private final ProtocolConfig cfg;
    private long window;
    private int textureId;

    public GLRenderer(ProtocolConfig cfg) {
        this.cfg = cfg;
    }

    public void init() {
        if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to init GLFW");

        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);

        window = GLFW.glfwCreateWindow(cfg.width, cfg.height, "HDMI Sender - VideoTransferA", MemoryUtil.NULL,
                MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) throw new RuntimeException("Failed to create GLFW window");

        // make context current
        GLFW.glfwMakeContextCurrent(window);
        // disable vsync here; we will control frame timing via FrameRateController (optionally enable)
        GLFW.glfwSwapInterval(0);

        GL.createCapabilities();

        // create texture
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, cfg.width, cfg.height, 0, GL11.GL_RGB,
                GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        // linear filtering ok
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // setup simple ortho projection
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, cfg.width, cfg.height, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(window);
    }

    public void renderFrame(ByteBuffer pixelData) {
        // Upload pixels
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, cfg.width, cfg.height, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE,
                pixelData);

        // render fullscreen quad with texture
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glLoadIdentity();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2i(0, 0);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2i(cfg.width, 0);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2i(cfg.width, cfg.height);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2i(0, cfg.height);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();
    }

    public void destroy() {
        GL11.glDeleteTextures(textureId);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}
