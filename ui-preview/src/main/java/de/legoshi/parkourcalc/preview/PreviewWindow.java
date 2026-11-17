package de.legoshi.parkourcalc.preview;

import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.extension.implot.ImPlot;
import imgui.extension.implot.ImPlotContext;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

final class PreviewWindow {

    private static final String FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Regular.ttf";
    private static final String BOLD_FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Bold.ttf";
    private static final int BASE_FONT_SIZE = 18;

    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private final long handle;
    private final boolean visible;
    private final Settings settings;
    private final float[] background;
    private ImPlotContext implotContext;
    private ImFont[] presetFonts;
    private ImFont[] boldPresetFonts;
    private int appliedScaleIndex = -1;
    private int fbo;
    private int colorBuffer;
    private int fboWidth;
    private int fboHeight;
    private int backgroundTexture;
    private int backgroundWidth;
    private int backgroundHeight;

    PreviewWindow(int width, int height, boolean visible, Settings settings, String iniFile, float[] background) {
        this.visible = visible;
        this.settings = settings;
        this.background = background;
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW could not start. On Linux without a screen run it under Xvfb.");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, visible ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_SCALE_TO_MONITOR, GLFW.GLFW_FALSE);
        handle = GLFW.glfwCreateWindow(width, height, "PKC UI preview", 0L, 0L);
        if (handle == 0L) throw new IllegalStateException("No OpenGL 3.2 window could be created.");
        GLFW.glfwMakeContextCurrent(handle);
        GLFW.glfwSwapInterval(visible ? 1 : 0);
        GL.createCapabilities();

        ImGui.createContext();
        implotContext = ImPlot.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(iniFile);
        configurePresetFonts();
        applyScale(settings.scaleIndex);
        imGuiGlfw.init(handle, true);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        imGuiGl3.init("#version 150");
    }

    long handle() {
        return handle;
    }

    boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(handle);
    }

    int framebufferHeight() {
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize(handle, w, h);
        return h[0];
    }

    void setBackgroundImage(Path image) throws IOException {
        BufferedImage img = ImageIO.read(image.toFile());
        if (img == null) throw new IOException("Not an image: " + image);
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) 0xFF);
            }
        }
        pixels.flip();
        backgroundTexture = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, backgroundTexture);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, w, h, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
        backgroundWidth = w;
        backgroundHeight = h;
    }

    void beginFrame(PreviewInput input) {
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize(handle, w, h);
        ensureFramebuffer(Math.max(1, w[0]), Math.max(1, h[0]));
        if (settings.scaleIndex != appliedScaleIndex) applyScale(settings.scaleIndex);

        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL11C.glViewport(0, 0, fboWidth, fboHeight);
        GL11C.glClearColor(background[0], background[1], background[2], 1f);
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);

        imGuiGlfw.newFrame();
        input.applyTo(ImGui.getIO());
        ImGui.newFrame();
        if (backgroundTexture != 0) drawBackground();
    }

    private void drawBackground() {
        ImGuiIO io = ImGui.getIO();
        float dw = io.getDisplaySizeX();
        float dh = io.getDisplaySizeY();
        float scale = Math.max(dw / backgroundWidth, dh / backgroundHeight);
        float u = dw / (backgroundWidth * scale);
        float v = dh / (backgroundHeight * scale);
        float u0 = (1f - u) / 2f;
        float v0 = (1f - v) / 2f;
        ImGui.getBackgroundDrawList().addImage(backgroundTexture, 0, 0, dw, dh, u0, v0, u0 + u, v0 + v);
    }

    void endFrame() {
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    BufferedImage capture(int cropX, int cropY, int cropW, int cropH) {
        float scaleX = fboWidth / Math.max(1f, ImGui.getIO().getDisplaySizeX());
        float scaleY = fboHeight / Math.max(1f, ImGui.getIO().getDisplaySizeY());
        int x0 = clamp(Math.round(cropX * scaleX), 0, fboWidth);
        int y0 = clamp(Math.round(cropY * scaleY), 0, fboHeight);
        int x1 = cropW <= 0 ? fboWidth : clamp(Math.round((cropX + cropW) * scaleX), x0, fboWidth);
        int y1 = cropH <= 0 ? fboHeight : clamp(Math.round((cropY + cropH) * scaleY), y0, fboHeight);
        int w = Math.max(1, x1 - x0);
        int h = Math.max(1, y1 - y0);
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, fbo);
        GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 4);
        GL11C.glReadPixels(x0, fboHeight - y0 - h, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            int row = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = row + x * 4;
                int rgb = (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | (pixels.get(i + 2) & 0xFF);
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    void present() {
        if (!visible) return;
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, fbo);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, 0);
        GL30C.glBlitFramebuffer(0, 0, fboWidth, fboHeight, 0, 0, fboWidth, fboHeight,
                GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
        GLFW.glfwSwapBuffers(handle);
    }

    void dispose() {
        imGuiGl3.dispose();
        imGuiGlfw.dispose();
        ImPlot.destroyContext(implotContext);
        ImGui.destroyContext();
        GLFW.glfwDestroyWindow(handle);
        GLFW.glfwTerminate();
    }

    private void ensureFramebuffer(int width, int height) {
        if (fbo != 0 && width == fboWidth && height == fboHeight) return;
        if (fbo != 0) {
            GL30C.glDeleteFramebuffers(fbo);
            GL30C.glDeleteRenderbuffers(colorBuffer);
        }
        fbo = GL30C.glGenFramebuffers();
        colorBuffer = GL30C.glGenRenderbuffers();
        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, colorBuffer);
        GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL11C.GL_RGBA8, width, height);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_RENDERBUFFER, colorBuffer);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Framebuffer incomplete: " + status);
        fboWidth = width;
        fboHeight = height;
    }

    private void applyScale(int index) {
        if (index < 0) index = Settings.DEFAULT_SCALE_INDEX;
        ThemeManager.setScrollbarMetrics(settings.scrollbarSize, settings.scrollbarGrabMinSize);
        ThemeManager.apply(Settings.PRESET_SCALES[index]);
        ImGui.getIO().setFontDefault(presetFonts[index]);
        Fonts.setBoldFont(boldPresetFonts[index]);
        appliedScaleIndex = settings.scaleIndex;
    }

    private void configurePresetFonts() {
        ImGuiIO io = ImGui.getIO();
        io.getFonts().clear();
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(io.getFonts().getGlyphRangesDefault());
        builder.addRanges(io.getFonts().getGlyphRangesCyrillic());
        builder.addRanges(io.getFonts().getGlyphRangesJapanese());
        short[] glyphRanges = builder.buildRanges();
        byte[] fontData = readResource(FONT_PATH);
        byte[] boldFontData = readResource(BOLD_FONT_PATH);
        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(glyphRanges);
        presetFonts = new ImFont[Settings.PRESET_SCALES.length];
        boldPresetFonts = new ImFont[Settings.PRESET_SCALES.length];
        for (int i = 0; i < Settings.PRESET_SCALES.length; i++) {
            int px = Math.round(BASE_FONT_SIZE * Settings.PRESET_SCALES[i]);
            presetFonts[i] = io.getFonts().addFontFromMemoryTTF(fontData, px, config);
            boldPresetFonts[i] = io.getFonts().addFontFromMemoryTTF(boldFontData, px, config);
        }
        io.getFonts().build();
        config.destroy();
    }

    private static byte[] readResource(String path) {
        try (InputStream in = PreviewWindow.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
