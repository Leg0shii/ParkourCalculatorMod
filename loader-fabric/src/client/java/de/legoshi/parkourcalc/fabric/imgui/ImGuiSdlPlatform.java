package de.legoshi.parkourcalc.fabric.imgui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImGuiPlatformIO;
import imgui.ImGuiViewport;
import imgui.ImVec2;
import imgui.callback.ImPlatformFuncViewport;
import imgui.callback.ImPlatformFuncViewportFloat;
import imgui.callback.ImPlatformFuncViewportImVec2;
import imgui.callback.ImPlatformFuncViewportString;
import imgui.callback.ImPlatformFuncViewportSuppBoolean;
import imgui.callback.ImPlatformFuncViewportSuppImVec2;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiBackendFlags;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiViewportFlags;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLStdinc;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

final class ImGuiSdlPlatform {

    private static final int MOUSE_BUTTONS = 5;
    private static final int SCANCODE_COUNT = 512;
    private static final Object TEXT_INPUT_OWNER = new Object();

    private static final long MONITOR_REFRESH_NANOS = 2_000_000_000L;

    private final boolean[] mouseJustPressed = new boolean[MOUSE_BUTTONS];
    private final Map<Long, Boolean> popOutWindows = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<long[]> pendingWindowEvents = new ConcurrentLinkedQueue<>();
    private long lastFrameNanos;
    private long lastMonitorRefreshNanos;
    private long mainWindow;
    private long glContext;
    private long textInputWindow;

    void init() {
        ImGuiIO io = ImGui.getIO();
        io.setBackendPlatformName("imgui_java_impl_sdl3_minecraft");

        io.setKeyMap(ImGuiKey.Tab, InputConstants.KEY_TAB);
        io.setKeyMap(ImGuiKey.LeftArrow, InputConstants.KEY_LEFT);
        io.setKeyMap(ImGuiKey.RightArrow, InputConstants.KEY_RIGHT);
        io.setKeyMap(ImGuiKey.UpArrow, InputConstants.KEY_UP);
        io.setKeyMap(ImGuiKey.DownArrow, InputConstants.KEY_DOWN);
        io.setKeyMap(ImGuiKey.PageUp, InputConstants.KEY_PAGEUP);
        io.setKeyMap(ImGuiKey.PageDown, InputConstants.KEY_PAGEDOWN);
        io.setKeyMap(ImGuiKey.Home, InputConstants.KEY_HOME);
        io.setKeyMap(ImGuiKey.End, InputConstants.KEY_END);
        io.setKeyMap(ImGuiKey.Insert, InputConstants.KEY_INSERT);
        io.setKeyMap(ImGuiKey.Delete, InputConstants.KEY_DELETE);
        io.setKeyMap(ImGuiKey.Backspace, InputConstants.KEY_BACKSPACE);
        io.setKeyMap(ImGuiKey.Space, InputConstants.KEY_SPACE);
        io.setKeyMap(ImGuiKey.Enter, InputConstants.KEY_RETURN);
        io.setKeyMap(ImGuiKey.Escape, InputConstants.KEY_ESCAPE);
        io.setKeyMap(ImGuiKey.KeyPadEnter, InputConstants.KEY_NUMPADENTER);
        io.setKeyMap(ImGuiKey.A, InputConstants.KEY_A);
        io.setKeyMap(ImGuiKey.C, InputConstants.KEY_C);
        io.setKeyMap(ImGuiKey.V, InputConstants.KEY_V);
        io.setKeyMap(ImGuiKey.X, InputConstants.KEY_X);
        io.setKeyMap(ImGuiKey.Y, InputConstants.KEY_Y);
        io.setKeyMap(ImGuiKey.Z, InputConstants.KEY_Z);

        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(String text) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
            }
        });
        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                return Minecraft.getInstance().keyboardHandler.getClipboard();
            }
        });
        lastFrameNanos = 0L;

        mainWindow = Minecraft.getInstance().getWindow().handle();
        io.addBackendFlags(ImGuiBackendFlags.PlatformHasViewports);
        ImGui.getMainViewport().setPlatformHandle(mainWindow);
        installPlatformInterface();
        updateMonitors();
    }

    void newFrame() {
        ImGuiIO io = ImGui.getIO();
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        applyPendingWindowEvents();

        int screenW = window.getScreenWidth();
        int screenH = window.getScreenHeight();
        io.setDisplaySize(screenW, screenH);
        if (screenW > 0 && screenH > 0) {
            io.setDisplayFramebufferScale((float) window.getWidth() / screenW, (float) window.getHeight() / screenH);
        }

        long now = System.nanoTime();
        io.setDeltaTime(lastFrameNanos > 0L ? Math.max(1e-4f, (now - lastFrameNanos) / 1_000_000_000f) : 1f / 60f);
        lastFrameNanos = now;
        if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable) && now - lastMonitorRefreshNanos > MONITOR_REFRESH_NANOS) {
            updateMonitors();
            lastMonitorRefreshNanos = now;
        }

        updateMouse(io);
        updateModifiers(io);
        syncTextInput(mc, io);
    }

    private void updateMouse(ImGuiIO io) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer x = stack.mallocFloat(1);
            FloatBuffer y = stack.mallocFloat(1);
            int mask = io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                    ? SDLMouse.SDL_GetGlobalMouseState(x, y)
                    : SDLMouse.SDL_GetMouseState(x, y);
            for (int i = 0; i < MOUSE_BUTTONS; i++) {
                int sdlButton = toSdlButton(i);
                boolean held = (mask & (1 << (sdlButton - 1))) != 0;
                io.setMouseDown(i, mouseJustPressed[i] || held);
                mouseJustPressed[i] = false;
            }
            io.setMousePos(x.get(0), y.get(0));
        }
    }

    private static void updateModifiers(ImGuiIO io) {
        io.setKeyCtrl(InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL));
        io.setKeyShift(InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT));
        io.setKeyAlt(InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT));
        io.setKeySuper(InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI));
    }

    private void syncTextInput(Minecraft mc, ImGuiIO io) {
        boolean want = io.getWantTextInput();
        long focus = SDLKeyboard.SDL_GetKeyboardFocus();
        long target = want && focus != 0L && popOutWindows.containsKey(focus) ? focus : 0L;
        if (textInputWindow != target) {
            if (textInputWindow != 0L) SDLKeyboard.SDL_StopTextInput(textInputWindow);
            if (target != 0L) SDLKeyboard.SDL_StartTextInput(target);
            textInputWindow = target;
        }
        if (want && target == 0L) {
            mc.textInputManager().startTextInput(TEXT_INPUT_OWNER);
        } else {
            mc.textInputManager().stopTextInput(TEXT_INPUT_OWNER);
        }
    }

    boolean isPopOutWindowFocused() {
        long focus = SDLKeyboard.SDL_GetKeyboardFocus();
        return focus != 0L && popOutWindows.containsKey(focus);
    }

    boolean consumeWindowEvent(SDL_Event event) {
        int type = event.type();
        if (type < SDLEvents.SDL_EVENT_WINDOW_FIRST || type > SDLEvents.SDL_EVENT_WINDOW_LAST) return false;
        long handle = SDLVideo.SDL_GetWindowFromID(event.window().windowID());
        if (handle == 0L || !popOutWindows.containsKey(handle)) return false;
        if (type == SDLEvents.SDL_EVENT_WINDOW_MOVED
                || type == SDLEvents.SDL_EVENT_WINDOW_RESIZED
                || type == SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED) {
            pendingWindowEvents.add(new long[] {handle, type});
        }
        return true;
    }

    private void applyPendingWindowEvents() {
        long[] pending;
        while ((pending = pendingWindowEvents.poll()) != null) {
            ImGuiViewport viewport = ImGui.findViewportByPlatformHandle(pending[0]);
            if (viewport.isNotValidPtr()) continue;
            if (pending[1] == SDLEvents.SDL_EVENT_WINDOW_MOVED) {
                viewport.setPlatformRequestMove(true);
            } else if (pending[1] == SDLEvents.SDL_EVENT_WINDOW_RESIZED) {
                viewport.setPlatformRequestResize(true);
            } else {
                viewport.setPlatformRequestClose(true);
            }
        }
    }

    void renderViewports() {
        boolean popOut = ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                && ImGui.getPlatformIO().getViewportsSize() > 1;
        long window = SDLVideo.SDL_GL_GetCurrentWindow();
        glContext = SDLVideo.SDL_GL_GetCurrentContext();
        int swapInterval = 0;
        if (popOut) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer interval = stack.mallocInt(1);
                if (SDLVideo.SDL_GL_GetSwapInterval(interval)) swapInterval = interval.get(0);
            }
            SDLVideo.SDL_GL_SetSwapInterval(0);
        }
        ImGui.updatePlatformWindows();
        ImGui.renderPlatformWindowsDefault();
        if (popOut) {
            SDLVideo.SDL_GL_MakeCurrent(window, glContext);
            SDLVideo.SDL_GL_SetSwapInterval(swapInterval);
        }
    }

    private void installPlatformInterface() {
        ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
        platformIO.setPlatformCreateWindow(new ImPlatformFuncViewport() {
            @Override
            public void accept(ImGuiViewport viewport) {
                createWindow(viewport);
            }
        });
        platformIO.setPlatformDestroyWindow(new ImPlatformFuncViewport() {
            @Override
            public void accept(ImGuiViewport viewport) {
                destroyWindow(viewport);
            }
        });
        platformIO.setPlatformShowWindow(new ImPlatformFuncViewport() {
            @Override
            public void accept(ImGuiViewport viewport) {
                SDLVideo.SDL_ShowWindow(viewport.getPlatformHandle());
            }
        });
        platformIO.setPlatformSetWindowPos(new ImPlatformFuncViewportImVec2() {
            @Override
            public void accept(ImGuiViewport viewport, ImVec2 pos) {
                SDLVideo.SDL_SetWindowPosition(viewport.getPlatformHandle(), (int) pos.x, (int) pos.y);
            }
        });
        platformIO.setPlatformGetWindowPos(new ImPlatformFuncViewportSuppImVec2() {
            @Override
            public void get(ImGuiViewport viewport, ImVec2 out) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer x = stack.mallocInt(1);
                    IntBuffer y = stack.mallocInt(1);
                    SDLVideo.SDL_GetWindowPosition(viewport.getPlatformHandle(), x, y);
                    out.set(x.get(0), y.get(0));
                }
            }
        });
        platformIO.setPlatformSetWindowSize(new ImPlatformFuncViewportImVec2() {
            @Override
            public void accept(ImGuiViewport viewport, ImVec2 size) {
                SDLVideo.SDL_SetWindowSize(viewport.getPlatformHandle(), Math.max(1, (int) size.x), Math.max(1, (int) size.y));
            }
        });
        platformIO.setPlatformGetWindowSize(new ImPlatformFuncViewportSuppImVec2() {
            @Override
            public void get(ImGuiViewport viewport, ImVec2 out) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    SDLVideo.SDL_GetWindowSize(viewport.getPlatformHandle(), w, h);
                    out.set(w.get(0), h.get(0));
                }
            }
        });
        platformIO.setPlatformSetWindowFocus(new ImPlatformFuncViewport() {
            @Override
            public void accept(ImGuiViewport viewport) {
                SDLVideo.SDL_RaiseWindow(viewport.getPlatformHandle());
            }
        });
        platformIO.setPlatformGetWindowFocus(new ImPlatformFuncViewportSuppBoolean() {
            @Override
            public boolean get(ImGuiViewport viewport) {
                return hasWindowFlag(viewport, SDLVideo.SDL_WINDOW_INPUT_FOCUS);
            }
        });
        platformIO.setPlatformGetWindowMinimized(new ImPlatformFuncViewportSuppBoolean() {
            @Override
            public boolean get(ImGuiViewport viewport) {
                return hasWindowFlag(viewport, SDLVideo.SDL_WINDOW_MINIMIZED);
            }
        });
        platformIO.setPlatformSetWindowTitle(new ImPlatformFuncViewportString() {
            @Override
            public void accept(ImGuiViewport viewport, String title) {
                SDLVideo.SDL_SetWindowTitle(viewport.getPlatformHandle(), title);
            }
        });
        platformIO.setPlatformSetWindowAlpha(new ImPlatformFuncViewportFloat() {
            @Override
            public void accept(ImGuiViewport viewport, float alpha) {
                SDLVideo.SDL_SetWindowOpacity(viewport.getPlatformHandle(), alpha);
            }
        });
        platformIO.setPlatformRenderWindow(new ImPlatformFuncViewport() {
            @Override
            public void accept(ImGuiViewport viewport) {
                SDLVideo.SDL_GL_MakeCurrent(viewport.getPlatformHandle(), glContext);
            }
        });
        platformIO.setPlatformSwapBuffers(new ImPlatformFuncViewport() {
            @Override
            public void accept(ImGuiViewport viewport) {
                long handle = viewport.getPlatformHandle();
                SDLVideo.SDL_GL_MakeCurrent(handle, glContext);
                SDLVideo.SDL_GL_SwapWindow(handle);
            }
        });
    }

    private static boolean hasWindowFlag(ImGuiViewport viewport, long flag) {
        long handle = viewport.getPlatformHandle();
        return handle != 0L && (SDLVideo.SDL_GetWindowFlags(handle) & flag) != 0L;
    }

    private void createWindow(ImGuiViewport viewport) {
        long flags = SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY;
        if (viewport.hasFlags(ImGuiViewportFlags.NoDecoration)) flags |= SDLVideo.SDL_WINDOW_BORDERLESS;
        if (viewport.hasFlags(ImGuiViewportFlags.NoTaskBarIcon)) flags |= SDLVideo.SDL_WINDOW_UTILITY;
        if (viewport.hasFlags(ImGuiViewportFlags.TopMost)) flags |= SDLVideo.SDL_WINDOW_ALWAYS_ON_TOP;
        int width = Math.max(1, (int) viewport.getSizeX());
        int height = Math.max(1, (int) viewport.getSizeY());
        long handle = SDLVideo.SDL_CreateWindow("Parkour Calculator", width, height, flags);
        if (handle == 0L) {
            throw new IllegalStateException("Failed to create a pop-out window: " + SDLError.SDL_GetError());
        }
        SDLVideo.SDL_SetWindowPosition(handle, (int) viewport.getPosX(), (int) viewport.getPosY());
        popOutWindows.put(handle, Boolean.TRUE);
        viewport.setPlatformHandle(handle);
    }

    private void destroyWindow(ImGuiViewport viewport) {
        long handle = viewport.getPlatformHandle();
        if (handle != 0L && handle != mainWindow && popOutWindows.remove(handle) != null) {
            if (textInputWindow == handle) textInputWindow = 0L;
            SDLVideo.SDL_DestroyWindow(handle);
        }
        viewport.setPlatformHandle(0L);
    }

    private void updateMonitors() {
        ImGuiPlatformIO platformIO = ImGui.getPlatformIO();
        platformIO.resizeMonitors(0);
        IntBuffer displays = SDLVideo.SDL_GetDisplays();
        if (displays != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_Rect bounds = SDL_Rect.malloc(stack);
                SDL_Rect usable = SDL_Rect.malloc(stack);
                for (int i = 0; i < displays.limit(); i++) {
                    int display = displays.get(i);
                    if (!SDLVideo.SDL_GetDisplayBounds(display, bounds)) continue;
                    SDL_Rect work = SDLVideo.SDL_GetDisplayUsableBounds(display, usable) ? usable : bounds;
                    float scale = SDLVideo.SDL_GetDisplayContentScale(display);
                    platformIO.pushMonitors(bounds.x(), bounds.y(), bounds.w(), bounds.h(),
                            work.x(), work.y(), work.w(), work.h(), scale > 0f ? scale : 1f);
                }
            } finally {
                SDLStdinc.SDL_free(displays);
            }
        }
        if (platformIO.getMonitorsSize() == 0) {
            ImGuiIO io = ImGui.getIO();
            platformIO.pushMonitors(0f, 0f, io.getDisplaySizeX(), io.getDisplaySizeY(), 0f, 0f, io.getDisplaySizeX(), io.getDisplaySizeY(), 1f);
        }
    }

    void keyCallback(int scancode, int action) {
        if (scancode < 0 || scancode >= SCANCODE_COUNT) return;
        if (action == InputConstants.PRESS) {
            ImGui.getIO().setKeysDown(scancode, true);
        } else if (action == InputConstants.RELEASE) {
            ImGui.getIO().setKeysDown(scancode, false);
        }
    }

    void charCallback(int codepoint) {
        ImGui.getIO().addInputCharacter(codepoint);
    }

    void mouseButtonCallback(int sdlButton, int action) {
        int button = toImGuiButton(sdlButton);
        if (button < 0) return;
        if (action == InputConstants.PRESS) {
            mouseJustPressed[button] = true;
        }
    }

    void scrollCallback(double xOffset, double yOffset) {
        ImGuiIO io = ImGui.getIO();
        io.setMouseWheelH(io.getMouseWheelH() + (float) xOffset);
        io.setMouseWheel(io.getMouseWheel() + (float) yOffset);
    }

    void dispose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.textInputManager() != null) {
            mc.textInputManager().stopTextInput(TEXT_INPUT_OWNER);
        }
    }

    private static int toImGuiButton(int sdlButton) {
        switch (sdlButton) {
            case InputConstants.MOUSE_BUTTON_LEFT: return 0;
            case InputConstants.MOUSE_BUTTON_RIGHT: return 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE: return 2;
            case InputConstants.MOUSE_BUTTON_4: return 3;
            case InputConstants.MOUSE_BUTTON_5: return 4;
            default: return -1;
        }
    }

    private static int toSdlButton(int imguiButton) {
        switch (imguiButton) {
            case 0: return InputConstants.MOUSE_BUTTON_LEFT;
            case 1: return InputConstants.MOUSE_BUTTON_RIGHT;
            case 2: return InputConstants.MOUSE_BUTTON_MIDDLE;
            case 3: return InputConstants.MOUSE_BUTTON_4;
            default: return InputConstants.MOUSE_BUTTON_5;
        }
    }
}
