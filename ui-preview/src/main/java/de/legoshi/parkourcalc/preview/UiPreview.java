package de.legoshi.parkourcalc.preview;

import de.legoshi.parkourcalc.core.Application;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.save.WorldDescriptor;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.Settings;
import imgui.ImGui;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UiPreview implements PreviewScript.Host {

    private static final String DEMO_TAS = "/strats/warmup-jump.json";
    private static final String CHILD_PROPERTY = "pkc.preview.child";
    private static final String DEFAULT_SHOT = "build/ui-preview/preview.png";
    private static final long TICK_NANOS = 50_000_000L;

    private final PreviewOptions options;
    private Application app;
    private PreviewWindow window;
    private PreviewInput input;
    private boolean exitRequested;
    private boolean captureFailed;

    private UiPreview(PreviewOptions options) {
        this.options = options;
    }

    public static void main(String[] args) throws Exception {
        PreviewOptions options;
        try {
            options = PreviewOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(PreviewOptions.usage());
            System.exit(2);
            return;
        }
        if (options.help) {
            System.out.println(PreviewOptions.usage());
            return;
        }
        if (options.listViews) {
            for (String view : viewNames()) System.out.println(view);
            return;
        }
        Integer relaunched = relaunchWithVirtualScreen(args);
        if (relaunched != null) {
            System.exit(relaunched);
            return;
        }
        System.setProperty("java.awt.headless", "true");
        int code = new UiPreview(options).run();
        System.exit(code);
    }

    private int run() throws IOException {
        List<PreviewScript.Step> steps = new ArrayList<>();
        steps.add(new PreviewScript.Step("start", null, 3, 0));
        List<String> lines = new ArrayList<>();
        if (options.script != null) lines.addAll(Files.readAllLines(options.script, StandardCharsets.UTF_8));
        if (options.inline != null) lines.addAll(PreviewScript.splitInline(options.inline));
        String shot = options.shot;
        if (shot == null && options.headless && lines.isEmpty()) shot = DEFAULT_SHOT;
        if (shot != null) lines.add("shot \"" + shot + "\"");
        steps.addAll(PreviewScript.parse(lines));
        boolean scripted = options.headless || steps.size() > 1;

        Path home = Files.createTempDirectory("pkc-ui-preview");
        Path saves = home.resolve("saves");
        Files.createDirectories(saves);
        if (options.saves != null) copySaves(options.saves, saves);
        Path settingsFile = home.resolve("parkourcalculator.json");
        if (options.settings != null) Files.copy(options.settings, settingsFile, StandardCopyOption.REPLACE_EXISTING);

        PreviewGame game = new PreviewGame();
        app = new Application(new PreviewSimulator(), game);
        app.initSettingsStorage(settingsFile);
        Settings settings = app.getSettings();
        if (options.scale != null) scale(options.scale);
        for (String view : options.views) {
            boolean on = !view.startsWith("-");
            view(on ? view : view.substring(1), on);
        }

        TasSetup tas = prepareTas(saves);
        String mcVersion = options.mcVersion != null ? options.mcVersion : tas.mcVersion != null ? tas.mcVersion : "26.3";
        if (tas.start != null) game.setPlayerPosition(tas.start, tas.startYaw);

        String ini = null;
        if (options.ini != null) {
            Path layout = home.resolve("layout.ini");
            Files.copy(options.ini, layout, StandardCopyOption.REPLACE_EXISTING);
            ini = layout.toString();
        }
        window = new PreviewWindow(options.width, options.height, !options.headless, settings, ini, options.background);
        if (options.backgroundImage != null) window.setBackgroundImage(options.backgroundImage);
        input = new PreviewInput(window.handle(), scripted);
        game.setInput(input);

        WorldDescriptor world = tas.world;
        app.setModVersion("ui-preview");
        app.setSaveStore(new FileSystemSaveStore(saves, "ui-preview", mcVersion, () -> world));
        app.setupUi();
        app.setControlPanelOpen(true);

        System.out.println("PKC UI preview: " + options.width + "x" + options.height + (options.headless ? ", headless" : "")
                + ", TAS " + (tas.name == null ? "(empty)" : tas.name) + ", MC " + mcVersion + ", data in " + home);
        int step = 0;
        int framesLeft = 0;
        long waitUntil = 0;
        long nextTick = System.nanoTime();
        while (!exitRequested && !window.shouldClose()) {
            GLFW.glfwPollEvents();
            while (framesLeft == 0 && System.nanoTime() >= waitUntil && step < steps.size()) {
                PreviewScript.Step s = steps.get(step++);
                if (s.action != null) {
                    try {
                        s.action.run(this);
                    } catch (RuntimeException e) {
                        System.err.println("Script step failed: " + s.source + ": " + e.getMessage());
                        captureFailed = true;
                    }
                }
                framesLeft = s.frames;
                if (s.millis > 0) waitUntil = System.nanoTime() + s.millis * 1_000_000L;
                if (exitRequested) break;
            }
            if (exitRequested) break;
            if (framesLeft == 0 && System.nanoTime() >= waitUntil && step >= steps.size()) {
                if (options.headless) break;
                if (input.isScripted()) input.setScripted(false);
            }
            long now = System.nanoTime();
            if (now >= nextTick) {
                app.tickPlayback();
                nextTick = now + TICK_NANOS;
            }
            app.tickDrag();
            app.resolveAutoScaleIfNeeded(window.framebufferHeight());
            window.beginFrame(input);
            app.getOverlayManager().render(ImGui.getIO(), true);
            window.endFrame();
            if (pendingCapture != null) {
                Runnable capture = pendingCapture;
                pendingCapture = null;
                capture.run();
            }
            window.present();
            if (framesLeft > 0) framesLeft--;
        }
        window.dispose();
        return captureFailed ? 1 : 0;
    }

    private Runnable pendingCapture;

    @Override
    public PreviewInput input() {
        return input;
    }

    @Override
    public void view(String name, boolean on) {
        Field field = findField("view", name);
        if (field == null) throw new IllegalArgumentException("unknown view " + name + ", known: " + String.join(", ", viewNames()));
        try {
            field.setBoolean(app.getSettings(), on);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void set(String fieldName, String value) {
        Field field = findField("", fieldName);
        if (field == null) throw new IllegalArgumentException("Settings has no field " + fieldName);
        Settings settings = app.getSettings();
        try {
            Class<?> type = field.getType();
            if (type == boolean.class) field.setBoolean(settings, Boolean.parseBoolean(value));
            else if (type == int.class) field.setInt(settings, Integer.parseInt(value));
            else if (type == float.class) field.setFloat(settings, Float.parseFloat(value));
            else if (type == double.class) field.setDouble(settings, Double.parseDouble(value));
            else if (type == String.class) field.set(settings, value);
            else if (type.isEnum()) field.set(settings, enumValue(type, value));
            else throw new IllegalArgumentException(fieldName + " has type " + type.getSimpleName() + ", not settable here");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String value) {
        return Enum.valueOf((Class) type, value.toUpperCase(Locale.ROOT));
    }

    @Override
    public void scale(String value) {
        Settings settings = app.getSettings();
        if (value.equalsIgnoreCase("auto")) {
            settings.scaleIndex = Settings.AUTO_SCALE_INDEX;
            return;
        }
        float wanted = Float.parseFloat(value.endsWith("%") ? value.substring(0, value.length() - 1) : value);
        if (value.endsWith("%")) wanted /= 100f;
        for (int i = 0; i < Settings.PRESET_SCALES.length; i++) {
            if (Math.abs(Settings.PRESET_SCALES[i] - wanted) < 1e-3f) {
                settings.scaleIndex = i;
                return;
            }
        }
        throw new IllegalArgumentException("scale must be auto or one of " + Arrays.toString(Settings.PRESET_SCALES));
    }

    @Override
    public void ui(boolean open) {
        app.setControlPanelOpen(open);
    }

    @Override
    public void select(int first, int count) {
        app.getSelection().selectRows(first, count);
    }

    @Override
    public void capture(String path, String windowName, int[] rect) {
        int[] crop = rect;
        if (windowName != null) {
            crop = windowRects().get(matchWindow(windowName));
        }
        int[] c = crop == null ? new int[]{0, 0, 0, 0} : crop;
        pendingCapture = () -> {
            BufferedImage image = window.capture(c[0], c[1], c[2], c[3]);
            File file = new File(path);
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            try {
                ImageIO.write(image, "png", file);
                System.out.println("Saved " + file.getAbsolutePath() + " (" + image.getWidth() + "x" + image.getHeight() + ")");
            } catch (IOException e) {
                System.err.println("Could not write " + path + ": " + e.getMessage());
                captureFailed = true;
            }
        };
    }

    private static String matchWindow(String wanted) {
        Map<String, int[]> rects = windowRects();
        String key = normalize(wanted);
        for (String name : rects.keySet()) if (name.equals(wanted)) return name;
        for (String name : rects.keySet()) if (normalize(displayName(name)).equals(key) || normalize(windowId(name)).equals(key)) return name;
        for (String name : rects.keySet()) if (!key.isEmpty() && normalize(name).contains(key)) return name;
        throw new IllegalArgumentException("no window called " + wanted + ", known: " + rects.keySet());
    }

    private static String displayName(String name) {
        int i = name.indexOf("##");
        return i < 0 ? name : name.substring(0, i);
    }

    private static String windowId(String name) {
        int i = name.lastIndexOf("##");
        return i < 0 ? name : name.substring(i + 2);
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Override
    public void place(String windowName, float x, float y, float w, float h) {
        String name = matchWindow(windowName);
        ImGui.setWindowPos(name, x, y);
        if (w > 0 && h > 0) ImGui.setWindowSize(name, w, h);
        ImGui.setWindowFocus(name);
    }

    @Override
    public void focus(String windowName) {
        ImGui.setWindowFocus(matchWindow(windowName));
    }

    @Override
    public void printWindows() {
        for (Map.Entry<String, int[]> e : windowRects().entrySet()) {
            int[] r = e.getValue();
            System.out.println("window \"" + e.getKey() + "\" x=" + r[0] + " y=" + r[1] + " w=" + r[2] + " h=" + r[3]);
        }
    }

    private static Map<String, int[]> windowRects() {
        Map<String, int[]> out = new LinkedHashMap<>();
        int[] rect = null;
        for (String line : ImGui.saveIniSettingsToMemory().split("\\r?\\n")) {
            if (line.startsWith("[Window][") && line.endsWith("]")) {
                rect = new int[4];
                out.put(line.substring("[Window][".length(), line.length() - 1), rect);
            } else if (line.startsWith("[")) {
                rect = null;
            } else if (rect != null && line.startsWith("Pos=")) {
                String[] p = line.substring(4).split(",");
                rect[0] = Integer.parseInt(p[0].trim());
                rect[1] = Integer.parseInt(p[1].trim());
            } else if (rect != null && line.startsWith("Size=")) {
                String[] p = line.substring(5).split(",");
                rect[2] = Integer.parseInt(p[0].trim());
                rect[3] = Integer.parseInt(p[1].trim());
            }
        }
        out.keySet().removeIf(n -> n.startsWith("Debug##"));
        out.values().removeIf(r -> r[2] <= 0 || r[3] <= 0);
        return out;
    }

    @Override
    public void exit() {
        exitRequested = true;
    }

    private static List<String> viewNames() {
        List<String> names = new ArrayList<>();
        for (Field f : Settings.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != boolean.class) continue;
            if (f.getName().startsWith("view") && f.getName().length() > 4) names.add(f.getName().substring(4));
        }
        return names;
    }

    private static Field findField(String prefix, String name) {
        String wanted = (prefix + name).replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        for (Field f : Settings.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getName().toLowerCase(Locale.ROOT).equals(wanted)) return f;
        }
        return null;
    }

    private static final class TasSetup {
        String name;
        String mcVersion;
        WorldDescriptor world = WorldDescriptor.singleplayer("Overworld", "Preview World");
        Vec3dCore start;
        float startYaw;
    }

    private TasSetup prepareTas(Path saves) throws IOException {
        TasSetup setup = new TasSetup();
        if (options.tas != null && options.tas.equalsIgnoreCase("none")) return setup;
        String contents;
        String name;
        if (options.tas == null) {
            try (InputStream in = UiPreview.class.getResourceAsStream(DEMO_TAS)) {
                if (in == null) throw new IOException("Demo TAS missing from the core jar: " + DEMO_TAS);
                contents = readAll(in);
            }
            name = "demo";
        } else {
            Path file = Paths.get(options.tas);
            contents = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String fileName = file.getFileName().toString();
            name = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
        }
        SaveFile parsed = SaveIO.parseSafe(contents);
        if (parsed == null) throw new IOException("Not a PKC TAS file: " + (options.tas == null ? DEMO_TAS : options.tas));
        Files.write(saves.resolve(name + ".json"), contents.getBytes(StandardCharsets.UTF_8));
        Files.write(saves.resolve(".lastopen"), name.getBytes(StandardCharsets.UTF_8));
        setup.name = name;
        setup.mcVersion = parsed.mcVersion;
        if (parsed.world != null) {
            setup.world = new WorldDescriptor(parsed.world.dimension, parsed.world.worldName, parsed.world.serverAddress);
        }
        if (parsed.start != null && parsed.start.pos != null) {
            setup.start = SaveIO.posOf(parsed.start);
            setup.startYaw = parsed.start.yaw;
        }
        return setup;
    }

    private static void copySaves(Path from, Path to) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(from)) {
            for (Path source : (Iterable<Path>) files::iterator) {
                Path rel = from.relativize(source);
                boolean hidden = false;
                for (Path part : rel) if (part.toString().startsWith(".")) hidden = true;
                if (hidden) continue;
                Path target = to.resolve(rel.toString());
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else if (source.toString().endsWith(".json")) Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Integer relaunchWithVirtualScreen(String[] args) throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("linux") || Boolean.getBoolean(CHILD_PROPERTY)) return null;
        String display = System.getenv("DISPLAY");
        if (display != null && !display.isEmpty()) return null;
        String xvfbRun = onPath("xvfb-run");
        if (xvfbRun == null) {
            System.err.println("No DISPLAY and no xvfb-run on the PATH: install Xvfb (apt install xvfb) or set DISPLAY.");
            return null;
        }
        List<String> cmd = new ArrayList<>(Arrays.asList(xvfbRun, "-a", "-s", "-screen 0 3840x2160x24 -nolisten tcp",
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-D" + CHILD_PROPERTY + "=true",
                "-cp", System.getProperty("java.class.path"),
                UiPreview.class.getName()));
        cmd.addAll(Arrays.asList(args));
        if (!cmd.contains("--headless")) cmd.add("--headless");
        ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
        pb.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
        if (System.getenv("XDG_RUNTIME_DIR") == null) {
            Path runtime = Files.createTempDirectory("pkc-ui-preview-xdg");
            pb.environment().put("XDG_RUNTIME_DIR", runtime.toString());
        }
        return pb.start().waitFor();
    }

    private static String onPath(String binary) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            File f = new File(dir, binary);
            if (f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }
}
