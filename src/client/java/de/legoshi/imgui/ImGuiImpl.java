package de.legoshi.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
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

    private ImGuiImpl() {}

    public static void create(long windowHandle) {
        ImGui.createContext();
        ImPlot.createContext();

        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(INI_FILENAME);
        io.setConfigFlags(ImGuiConfigFlags.DockingEnable);

        float scale = queryDpiScale(windowHandle);
        configureFont(scale);
        ImGui.getStyle().scaleAllSizes(scale);

        imGuiGlfw.init(windowHandle, false);
        imGuiGl3.init();
    }

    public static void beginImGuiRendering() {
        bindMinecraftFramebuffer();

        imGuiGl3.newFrame();
        imGuiGlfw.newFrame();
        ImGui.newFrame();
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

    private static float queryDpiScale(long windowHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xScale = stack.mallocFloat(1);
            FloatBuffer yScale = stack.mallocFloat(1);
            GLFW.glfwGetWindowContentScale(windowHandle, xScale, yScale);
            return Math.max(xScale.get(0), yScale.get(0));
        }
    }

    private static void configureFont(float scale) {
        ImGuiIO io = ImGui.getIO();
        io.getFonts().clear();

        short[] glyphRanges = buildGlyphRanges();
        int scaledFontSize = Math.round(BASE_FONT_SIZE * scale);

        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(glyphRanges);

        try (InputStream fontStream = ImGuiImpl.class.getResourceAsStream(FONT_PATH)) {
            Objects.requireNonNull(fontStream, "Font not found: " + FONT_PATH);
            byte[] fontData = IOUtils.toByteArray(fontStream);
            io.getFonts().addFontFromMemoryTTF(fontData, scaledFontSize, config);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load font: " + FONT_PATH, e);
        } finally {
            config.destroy();
        }

        io.getFonts().build();
        io.setFontGlobalScale(1.0f);
    }

    private static short[] buildGlyphRanges() {
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesDefault());
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesCyrillic());
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesJapanese());
        return builder.buildRanges();
    }
}