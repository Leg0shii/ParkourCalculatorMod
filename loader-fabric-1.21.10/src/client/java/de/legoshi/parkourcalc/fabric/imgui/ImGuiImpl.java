package de.legoshi.parkourcalc.fabric.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.extension.implot.ImPlot;
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

/**
 * Manages ImGui initialization, rendering lifecycle, and cleanup.
 */
public final class ImGuiImpl {

    private static final String FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Regular.ttf";
    private static final int BASE_FONT_SIZE = 18;
    private static final String INI_FILENAME = "parkourcalculator.ini";

    private static final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    private static Settings settings;
    private static ImFont[] presetFonts;
    private static int appliedScaleIndex = -1;

    private ImGuiImpl() {}

    public static void create(long windowHandle, Settings settingsRef) {
        settings = settingsRef;

        ImGui.createContext();
        ImPlot.createContext();

        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(INI_FILENAME);
        io.setConfigFlags(ImGuiConfigFlags.DockingEnable);

        configurePresetFonts();
        applyScale(settings.scaleIndex);

        imGuiGlfw.init(windowHandle, false);
        imGuiGl3.init();
    }

    public static void beginImGuiRendering() {
        applyPendingScale();
        bindMinecraftFramebuffer();

        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();
        // imGuiGlfw polls GLFW directly; pinned overlays would still see play-mode clicks.
        if (!FabricParkourCalculator.isUiFocused()) {
            ImGuiIO io = ImGui.getIO();
            io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE);
            for (int i = 0; i < 5; i++) {
                io.setMouseDown(i, false);
            }
        }
        ImGui.newFrame();
    }

    private static void applyPendingScale() {
        if (settings.scaleIndex == appliedScaleIndex) return;
        applyScale(settings.scaleIndex);
    }

    private static void applyScale(int newIdx) {
        float newScale = Settings.PRESET_SCALES[newIdx];
        if (appliedScaleIndex < 0) {
            ImGui.getStyle().scaleAllSizes(newScale);
        } else {
            float oldScale = Settings.PRESET_SCALES[appliedScaleIndex];
            ImGui.getStyle().scaleAllSizes(newScale / oldScale);
        }
        ImGui.getIO().setFontDefault(presetFonts[newIdx]);
        appliedScaleIndex = newIdx;
    }

    public static void endImGuiRendering() {
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        handleViewports();
    }

    public static void dispose() {
        imGuiGl3.shutdown();
        imGuiGlfw.shutdown();

        ImPlot.destroyContext();
        ImGui.destroyContext();
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
        byte[] fontData = readFontBytes();

        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(glyphRanges);

        presetFonts = new ImFont[Settings.PRESET_SCALES.length];
        for (int i = 0; i < Settings.PRESET_SCALES.length; i++) {
            int px = Math.round(BASE_FONT_SIZE * Settings.PRESET_SCALES[i]);
            presetFonts[i] = io.getFonts().addFontFromMemoryTTF(fontData, px, config);
        }

        io.getFonts().build();
        config.destroy();
    }

    private static byte[] readFontBytes() {
        try (InputStream fontStream = ImGuiImpl.class.getResourceAsStream(FONT_PATH)) {
            Objects.requireNonNull(fontStream, "Font not found: " + FONT_PATH);
            return IOUtils.toByteArray(fontStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load font: " + FONT_PATH, e);
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