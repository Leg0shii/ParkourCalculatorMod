package de.legoshi.parkourcalc.forge.core.lwjgl2;

import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiViewport;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.callback.ImPlatformFuncViewport;
import imgui.type.ImInt;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Vendored ImGuiGL3 from ImGui-Java-Lwjgl2 1.0.0, plus a legacy GL 2.1 path
 * (GLSL 120 shaders, no VAO) so Macs stuck on a 2.1 context don't crash.
 * GL enums kept as raw ints from the upstream decompile.
 */
public final class ImGuiGl3Compat {
    private boolean useVao;
    private boolean hasBaseVertex;
    private String glslVersion = "";
    private int gFontTexture = 0;
    private int gShaderHandle = 0;
    private int gVertHandle = 0;
    private int gFragHandle = 0;
    private int gAttribLocationTex = 0;
    private int gAttribLocationProjMtx = 0;
    private int gAttribLocationVtxPos = 0;
    private int gAttribLocationVtxUV = 0;
    private int gAttribLocationVtxColor = 0;
    private int gVboHandle = 0;
    private int gElementsHandle = 0;
    private int gVertexArrayObjectHandle = 0;
    private final ImVec2 displaySize = new ImVec2();
    private final ImVec2 framebufferScale = new ImVec2();
    private final ImVec2 displayPos = new ImVec2();
    private final ImVec4 clipRect = new ImVec4();
    private final FloatBuffer orthoProjMatrix = BufferUtils.createFloatBuffer(16);
    private int lastActiveTexture = 0;
    private int lastProgram = 0;
    private int lastTexture = 0;
    private int lastArrayBuffer = 0;
    private int lastVertexArrayObject = 0;
    private final IntBuffer lastViewport = BufferUtils.createIntBuffer(16);
    private final IntBuffer lastScissorBox = BufferUtils.createIntBuffer(16);
    private int lastBlendSrcRgb = 0;
    private int lastBlendDstRgb = 0;
    private int lastBlendSrcAlpha = 0;
    private int lastBlendDstAlpha = 0;
    private int lastBlendEquationRgb = 0;
    private int lastBlendEquationAlpha = 0;
    private boolean lastEnableBlend = false;
    private boolean lastEnableCullFace = false;
    private boolean lastEnableDepthTest = false;
    private boolean lastEnableStencilTest = false;
    private boolean lastEnableScissorTest = false;

    public void init() {
        ContextCapabilities caps = GLContext.getCapabilities();
        useVao = caps.OpenGL30 || caps.GL_ARB_vertex_array_object;
        hasBaseVertex = caps.OpenGL32;
        glslVersion = caps.OpenGL30 ? "#version 130" : "#version 120";
        setupBackendCapabilitiesFlags();
        createDeviceObjects();
        if (ImGui.getIO().hasConfigFlags(1024)) {
            initPlatformInterface();
        }
    }

    public void renderDrawData(ImDrawData drawData) {
        if (drawData.getCmdListsCount() <= 0) {
            return;
        }
        drawData.getDisplaySize(displaySize);
        drawData.getDisplayPos(displayPos);
        drawData.getFramebufferScale(framebufferScale);
        float clipOffX = displayPos.x;
        float clipOffY = displayPos.y;
        float clipScaleX = framebufferScale.x;
        float clipScaleY = framebufferScale.y;
        int fbWidth = (int) (displaySize.x * framebufferScale.x);
        int fbHeight = (int) (displaySize.y * framebufferScale.y);
        if (fbWidth <= 0 || fbHeight <= 0) {
            return;
        }
        backupGlState();
        bind(fbWidth, fbHeight);

        for (int cmdListIdx = 0; cmdListIdx < drawData.getCmdListsCount(); cmdListIdx++) {
            GL15.glBufferData(34962, drawData.getCmdListVtxBufferData(cmdListIdx), 35040);
            GL15.glBufferData(34963, drawData.getCmdListIdxBufferData(cmdListIdx), 35040);

            for (int cmdBufferIdx = 0; cmdBufferIdx < drawData.getCmdListCmdBufferSize(cmdListIdx); cmdBufferIdx++) {
                drawData.getCmdListCmdBufferClipRect(cmdListIdx, cmdBufferIdx, clipRect);
                float clipMinX = (clipRect.x - clipOffX) * clipScaleX;
                float clipMinY = (clipRect.y - clipOffY) * clipScaleY;
                float clipMaxX = (clipRect.z - clipOffX) * clipScaleX;
                float clipMaxY = (clipRect.w - clipOffY) * clipScaleY;
                if (clipMaxX <= clipMinX || clipMaxY <= clipMinY) {
                    continue;
                }
                GL11.glScissor((int) clipMinX, (int) ((float) fbHeight - clipMaxY), (int) (clipMaxX - clipMinX), (int) (clipMaxY - clipMinY));
                int textureId = drawData.getCmdListCmdBufferTextureId(cmdListIdx, cmdBufferIdx);
                int elemCount = drawData.getCmdListCmdBufferElemCount(cmdListIdx, cmdBufferIdx);
                int idxBufferOffset = drawData.getCmdListCmdBufferIdxOffset(cmdListIdx, cmdBufferIdx);
                int vtxBufferOffset = drawData.getCmdListCmdBufferVtxOffset(cmdListIdx, cmdBufferIdx);
                int indices = idxBufferOffset * 2;
                GL11.glBindTexture(3553, textureId);
                if (hasBaseVertex) {
                    GL32.glDrawElementsBaseVertex(4, elemCount, 5123, indices, vtxBufferOffset);
                } else {
                    GL11.glDrawElements(4, elemCount, 5123, indices);
                }
            }
        }

        unbind();
        restoreModifiedGlState();
    }

    public void dispose() {
        GL15.glDeleteBuffers(gVboHandle);
        GL15.glDeleteBuffers(gElementsHandle);
        GL20.glDetachShader(gShaderHandle, gVertHandle);
        GL20.glDetachShader(gShaderHandle, gFragHandle);
        GL20.glDeleteProgram(gShaderHandle);
        GL11.glDeleteTextures(gFontTexture);
        shutdownPlatformInterface();
    }

    public void updateFontsTexture() {
        GL11.glDeleteTextures(gFontTexture);
        ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer buffer = fontAtlas.getTexDataAsRGBA32(width, height);
        gFontTexture = GL11.glGenTextures();
        GL11.glBindTexture(3553, gFontTexture);
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
        GL11.glTexImage2D(3553, 0, 6408, width.get(), height.get(), 0, 6408, 5121, buffer);
        fontAtlas.setTexID(gFontTexture);
    }

    private void setupBackendCapabilitiesFlags() {
        ImGuiIO io = ImGui.getIO();
        io.setBackendRendererName("imgui_java_impl_opengl3_lwjgl2_compat");
        if (hasBaseVertex) {
            io.addBackendFlags(8);
        }
        io.addBackendFlags(4096);
    }

    private void createDeviceObjects() {
        int lastTexture = GL11.glGetInteger(32873);
        int lastArrayBuffer = GL11.glGetInteger(34964);
        int lastVertexArray = useVao ? GL11.glGetInteger(34229) : 0;
        createShaders();
        gAttribLocationTex = GL20.glGetUniformLocation(gShaderHandle, "Texture");
        gAttribLocationProjMtx = GL20.glGetUniformLocation(gShaderHandle, "ProjMtx");
        gAttribLocationVtxPos = GL20.glGetAttribLocation(gShaderHandle, "Position");
        gAttribLocationVtxUV = GL20.glGetAttribLocation(gShaderHandle, "UV");
        gAttribLocationVtxColor = GL20.glGetAttribLocation(gShaderHandle, "Color");
        gVboHandle = GL15.glGenBuffers();
        gElementsHandle = GL15.glGenBuffers();
        updateFontsTexture();
        GL11.glBindTexture(3553, lastTexture);
        GL15.glBindBuffer(34962, lastArrayBuffer);
        if (useVao) {
            GL30.glBindVertexArray(lastVertexArray);
        }
    }

    private void createShaders() {
        String vertShaderSource;
        String fragShaderSource;
        if ("#version 120".equals(glslVersion)) {
            vertShaderSource = getVertexShaderGlsl120();
            fragShaderSource = getFragmentShaderGlsl120();
        } else {
            vertShaderSource = getVertexShaderGlsl130();
            fragShaderSource = getFragmentShaderGlsl130();
        }

        gVertHandle = createAndCompileShader(35633, vertShaderSource);
        gFragHandle = createAndCompileShader(35632, fragShaderSource);
        gShaderHandle = GL20.glCreateProgram();
        GL20.glAttachShader(gShaderHandle, gVertHandle);
        GL20.glAttachShader(gShaderHandle, gFragHandle);
        GL20.glLinkProgram(gShaderHandle);
        if (GL20.glGetProgrami(gShaderHandle, 35714) == 0) {
            throw new IllegalStateException("Failed to link shader program:\n" + GL20.glGetProgramInfoLog(gShaderHandle, 9999999));
        }
    }

    private void backupGlState() {
        lastActiveTexture = GL11.glGetInteger(34016);
        GL13.glActiveTexture(33984);
        lastProgram = GL11.glGetInteger(35725);
        lastTexture = GL11.glGetInteger(32873);
        lastArrayBuffer = GL11.glGetInteger(34964);
        lastVertexArrayObject = useVao ? GL11.glGetInteger(34229) : 0;
        GL11.glGetInteger(2978, lastViewport);
        GL11.glGetInteger(3088, lastScissorBox);
        lastBlendSrcRgb = GL11.glGetInteger(32969);
        lastBlendDstRgb = GL11.glGetInteger(32968);
        lastBlendSrcAlpha = GL11.glGetInteger(32971);
        lastBlendDstAlpha = GL11.glGetInteger(32970);
        lastBlendEquationRgb = GL11.glGetInteger(32777);
        lastBlendEquationAlpha = GL11.glGetInteger(34877);
        lastEnableBlend = GL11.glIsEnabled(3042);
        lastEnableCullFace = GL11.glIsEnabled(2884);
        lastEnableDepthTest = GL11.glIsEnabled(2929);
        lastEnableStencilTest = GL11.glIsEnabled(2960);
        lastEnableScissorTest = GL11.glIsEnabled(3089);
    }

    private void restoreModifiedGlState() {
        GL20.glUseProgram(lastProgram);
        GL11.glBindTexture(3553, lastTexture);
        GL13.glActiveTexture(lastActiveTexture);
        if (useVao) {
            GL30.glBindVertexArray(lastVertexArrayObject);
        }
        GL15.glBindBuffer(34962, lastArrayBuffer);
        GL20.glBlendEquationSeparate(lastBlendEquationRgb, lastBlendEquationAlpha);
        GL14.glBlendFuncSeparate(lastBlendSrcRgb, lastBlendDstRgb, lastBlendSrcAlpha, lastBlendDstAlpha);
        setEnabled(3042, lastEnableBlend);
        setEnabled(2884, lastEnableCullFace);
        setEnabled(2929, lastEnableDepthTest);
        setEnabled(2960, lastEnableStencilTest);
        setEnabled(3089, lastEnableScissorTest);
        GL11.glViewport(lastViewport.get(0), lastViewport.get(1), lastViewport.get(2), lastViewport.get(3));
        GL11.glScissor(lastScissorBox.get(0), lastScissorBox.get(1), lastScissorBox.get(2), lastScissorBox.get(3));
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    private void bind(int fbWidth, int fbHeight) {
        if (useVao) {
            gVertexArrayObjectHandle = GL30.glGenVertexArrays();
        }
        GL11.glEnable(3042);
        GL14.glBlendEquation(32774);
        GL14.glBlendFuncSeparate(770, 771, 1, 771);
        GL11.glDisable(2884);
        GL11.glDisable(2929);
        GL11.glDisable(2960);
        GL11.glEnable(3089);
        GL11.glViewport(0, 0, fbWidth, fbHeight);
        float left = displayPos.x;
        float right = displayPos.x + displaySize.x;
        float top = displayPos.y;
        float bottom = displayPos.y + displaySize.y;
        orthoProjMatrix.put(0, 2.0F / (right - left));
        orthoProjMatrix.put(5, 2.0F / (top - bottom));
        orthoProjMatrix.put(10, -1.0F);
        orthoProjMatrix.put(12, (right + left) / (left - right));
        orthoProjMatrix.put(13, (top + bottom) / (bottom - top));
        orthoProjMatrix.put(15, 1.0F);
        GL20.glUseProgram(gShaderHandle);
        GL20.glUniform1i(gAttribLocationTex, 0);
        GL20.glUniformMatrix4(gAttribLocationProjMtx, false, orthoProjMatrix);
        if (useVao) {
            GL30.glBindVertexArray(gVertexArrayObjectHandle);
        }
        GL15.glBindBuffer(34962, gVboHandle);
        GL15.glBindBuffer(34963, gElementsHandle);
        GL20.glEnableVertexAttribArray(gAttribLocationVtxPos);
        GL20.glEnableVertexAttribArray(gAttribLocationVtxUV);
        GL20.glEnableVertexAttribArray(gAttribLocationVtxColor);
        GL20.glVertexAttribPointer(gAttribLocationVtxPos, 2, 5126, false, 20, 0L);
        GL20.glVertexAttribPointer(gAttribLocationVtxUV, 2, 5126, false, 20, 8L);
        GL20.glVertexAttribPointer(gAttribLocationVtxColor, 4, 5121, true, 20, 16L);
    }

    private void unbind() {
        if (useVao) {
            GL30.glDeleteVertexArrays(gVertexArrayObjectHandle);
        }
    }

    private void initPlatformInterface() {
        ImGui.getPlatformIO().setRendererRenderWindow(new ImPlatformFuncViewport() {
            public void accept(ImGuiViewport vp) {
                if (!vp.hasFlags(256)) {
                    GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                    GL11.glClear(16384);
                }
                renderDrawData(vp.getDrawData());
            }
        });
    }

    private void shutdownPlatformInterface() {
        ImGui.destroyPlatformWindows();
    }

    private int createAndCompileShader(int type, CharSequence source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, 35713) == 0) {
            throw new IllegalStateException("Failed to compile shader:\n" + GL20.glGetShaderInfoLog(id, 9999999));
        }
        return id;
    }

    private String getVertexShaderGlsl120() {
        return glslVersion + "\nuniform mat4 ProjMtx;\nattribute vec2 Position;\nattribute vec2 UV;\nattribute vec4 Color;\nvarying vec2 Frag_UV;\nvarying vec4 Frag_Color;\nvoid main()\n{\n    Frag_UV = UV;\n    Frag_Color = Color;\n    gl_Position = ProjMtx * vec4(Position.xy,0,1);\n}\n";
    }

    private String getVertexShaderGlsl130() {
        return glslVersion + "\nuniform mat4 ProjMtx;\nin vec2 Position;\nin vec2 UV;\nin vec4 Color;\nout vec2 Frag_UV;\nout vec4 Frag_Color;\nvoid main()\n{\n    Frag_UV = UV;\n    Frag_Color = Color;\n    gl_Position = ProjMtx * vec4(Position.xy,0,1);\n}\n";
    }

    private String getFragmentShaderGlsl120() {
        return glslVersion + "\n#ifdef GL_ES\n    precision mediump float;\n#endif\nuniform sampler2D Texture;\nvarying vec2 Frag_UV;\nvarying vec4 Frag_Color;\nvoid main()\n{\n    gl_FragColor = Frag_Color * texture2D(Texture, Frag_UV.st);\n}\n";
    }

    private String getFragmentShaderGlsl130() {
        return glslVersion + "\nuniform sampler2D Texture;\nin vec2 Frag_UV;\nin vec4 Frag_Color;\nout vec4 Out_Color;\nvoid main()\n{\n    Out_Color = Frag_Color * texture(Texture, Frag_UV.st);\n}\n";
    }
}
