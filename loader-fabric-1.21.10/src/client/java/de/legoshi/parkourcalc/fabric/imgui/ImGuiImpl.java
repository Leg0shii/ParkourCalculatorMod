package de.legoshi.parkourcalc.fabric.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.extension.implot.ImPlot;
import imgui.extension.implot.ImPlotContext;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import org.apache.commons.io.IOUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Manages ImGui initialization, rendering lifecycle, and cleanup.
 */
public final class ImGuiImpl {

    private static final String FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Regular.ttf";
    private static final String BOLD_FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Bold.ttf";
    private static final int BASE_FONT_SIZE = 18;
    private static final String INI_FILENAME = "parkourcalculator.ini";

    private static final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private static ImPlotContext implotContext;

    private static Settings settings;
    private static IntConsumer autoScaleResolver;
    private static ImFont[] presetFonts;
    private static ImFont[] boldPresetFonts;
    private static int appliedScaleIndex = -1;

    private ImGuiImpl() {}

    public static void create(long windowHandle, Settings settingsRef, IntConsumer autoScaleResolverRef) {
        settings = settingsRef;
        autoScaleResolver = autoScaleResolverRef;

        ImGui.createContext();
        implotContext = ImPlot.createContext();

        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(INI_FILENAME);

        configurePresetFonts();
        applyScale(settings.scaleIndex);

        imGuiGlfw.init(windowHandle, false);
        // 1.86's ImGuiImplGl3 omits the GL_UNPACK_* reset that 1.90 does internally, so MC's
        // leftover pixel-store state scrambles the font atlas on upload (glyphs render as garbage).
        // Normalize to GL defaults before init() uploads the atlas.
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        imGuiGl3.init();
    }

    public static void beginImGuiRendering() {
        autoScaleResolver.accept(currentFramebufferHeight());
        applyPendingScale();
        bindMinecraftFramebuffer();

        imGuiGlfw.newFrame();
        ImGui.newFrame();
        // imGuiGlfw feeds the polled cursor pos into ImGui during newFrame, so the
        // off-screen override below has to run AFTER it to win.
        if (!FabricParkourCalculator.isUiFocused()) {
            ImGuiIO io = ImGui.getIO();
            io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE);
            for (int i = 0; i < 5; i++) {
                io.setMouseDown(i, false);
            }
        }
    }

    private static void applyPendingScale() {
        if (settings.scaleIndex == appliedScaleIndex) return;
        applyScale(settings.scaleIndex);
    }

    private static void applyScale(int newIdx) {
        if (newIdx < 0) newIdx = Settings.DEFAULT_SCALE_INDEX;
        ThemeManager.setScrollbarMetrics(settings.scrollbarSize, settings.scrollbarGrabMinSize);
        ThemeManager.apply(Settings.PRESET_SCALES[newIdx]);
        ImGui.getIO().setFontDefault(presetFonts[newIdx]);
        Fonts.setBoldFont(boldPresetFonts[newIdx]);
        appliedScaleIndex = newIdx;
    }

    public static void endImGuiRendering() {
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        handleViewports();
    }

    public static void dispose() {
        imGuiGl3.dispose();
        imGuiGlfw.dispose();

        ImPlot.destroyContext(implotContext);
        ImGui.destroyContext();
    }

    public static void keyCallback(long window, int key, int scancode, int action, int mods) {
        imGuiGlfw.keyCallback(window, key, scancode, action, mods);
    }

    public static void charCallback(long window, int codepoint) {
        imGuiGlfw.charCallback(window, codepoint);
    }

    public static void mouseButtonCallback(long window, int button, int action, int mods) {
        imGuiGlfw.mouseButtonCallback(window, button, action, mods);
    }

    public static void scrollCallback(long window, double xOffset, double yOffset) {
        imGuiGlfw.scrollCallback(window, xOffset, yOffset);
    }

    private static int currentFramebufferHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return 0;
        Framebuffer fb = mc.getFramebuffer();
        return fb == null ? 0 : fb.textureHeight;
    }

    private static void bindMinecraftFramebuffer() {
        Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        int framebufferId = ((GlTexture) Objects.requireNonNull(framebuffer.getColorAttachment()))
                .getOrCreateFramebuffer(((GlBackend) RenderSystem.getDevice()).getBufferManager(), null);

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebufferId);
        GL11C.glViewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
    }

    private static void handleViewports() {
        if (!ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            return;
        }

        long currentContext = GLFW.glfwGetCurrentContext();
        ImGui.updatePlatformWindows();
        ImGui.renderPlatformWindowsDefault();
        GLFW.glfwMakeContextCurrent(currentContext);
    }

    private static void configurePresetFonts() {
        ImGuiIO io = ImGui.getIO();
        io.getFonts().clear();

        short[] glyphRanges = buildGlyphRanges();
        byte[] fontData = readFontBytes(FONT_PATH);
        byte[] boldFontData = readFontBytes(BOLD_FONT_PATH);

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

    private static byte[] readFontBytes(String path) {
        try (InputStream fontStream = ImGuiImpl.class.getResourceAsStream(path)) {
            Objects.requireNonNull(fontStream, "Font not found: " + path);
            return IOUtils.toByteArray(fontStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load font: " + path, e);
        }
    }

    private static short[] buildGlyphRanges() {
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesDefault());
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesCyrillic());
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesJapanese());
        return builder.buildRanges();
    }
}