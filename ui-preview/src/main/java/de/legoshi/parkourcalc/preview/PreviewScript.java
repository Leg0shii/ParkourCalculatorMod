package de.legoshi.parkourcalc.preview;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PreviewScript {

    interface Host {
        PreviewInput input();

        void view(String name, boolean on);

        void set(String field, String value);

        void scale(String value);

        void ui(boolean open);

        void select(int first, int count);

        void capture(String path, String window, int[] rect);

        void printWindows();

        void place(String window, float x, float y, float w, float h);

        void focus(String window);

        void exit();
    }

    static final class Step {
        final String source;
        final Action action;
        final int frames;
        final long millis;

        Step(String source, Action action, int frames, long millis) {
            this.source = source;
            this.action = action;
            this.frames = frames;
            this.millis = millis;
        }
    }

    interface Action {
        void run(Host host);
    }

    private static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        KEYS.put("enter", GLFW.GLFW_KEY_ENTER);
        KEYS.put("return", GLFW.GLFW_KEY_ENTER);
        KEYS.put("escape", GLFW.GLFW_KEY_ESCAPE);
        KEYS.put("esc", GLFW.GLFW_KEY_ESCAPE);
        KEYS.put("tab", GLFW.GLFW_KEY_TAB);
        KEYS.put("backspace", GLFW.GLFW_KEY_BACKSPACE);
        KEYS.put("delete", GLFW.GLFW_KEY_DELETE);
        KEYS.put("insert", GLFW.GLFW_KEY_INSERT);
        KEYS.put("space", GLFW.GLFW_KEY_SPACE);
        KEYS.put("up", GLFW.GLFW_KEY_UP);
        KEYS.put("down", GLFW.GLFW_KEY_DOWN);
        KEYS.put("left", GLFW.GLFW_KEY_LEFT);
        KEYS.put("right", GLFW.GLFW_KEY_RIGHT);
        KEYS.put("home", GLFW.GLFW_KEY_HOME);
        KEYS.put("end", GLFW.GLFW_KEY_END);
        KEYS.put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        KEYS.put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        for (char c = 'a'; c <= 'z'; c++) KEYS.put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        for (char c = '0'; c <= '9'; c++) KEYS.put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        for (int i = 1; i <= 12; i++) KEYS.put("f" + i, GLFW.GLFW_KEY_F1 + i - 1);
    }

    private PreviewScript() {}

    static List<String> splitInline(String commands) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (char c : commands.toCharArray()) {
            if (quote == 0 && (c == '"' || c == '\'')) quote = c;
            else if (c == quote) quote = 0;
            if (c == ';' && quote == 0) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    static List<Step> parse(List<String> lines) {
        List<Step> steps = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            List<String> args = tokenize(line);
            String cmd = args.get(0).toLowerCase(Locale.ROOT);
            try {
                compile(line, cmd, args.subList(1, args.size()), steps);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Bad script line \"" + line + "\": " + e.getMessage(), e);
            }
        }
        return steps;
    }

    private static void compile(String line, String cmd, List<String> a, List<Step> out) {
        switch (cmd) {
            case "frames":
                out.add(new Step(line, null, Integer.parseInt(a.get(0)), 0));
                break;
            case "wait":
                out.add(new Step(line, null, 1, Long.parseLong(a.get(0))));
                break;
            case "move": {
                float x = Float.parseFloat(a.get(0));
                float y = Float.parseFloat(a.get(1));
                out.add(new Step(line, h -> { h.input().mouseX = x; h.input().mouseY = y; }, 1, 0));
                break;
            }
            case "click":
            case "rightclick":
            case "doubleclick": {
                float x = Float.parseFloat(a.get(0));
                float y = Float.parseFloat(a.get(1));
                int button = cmd.equals("rightclick") ? 1 : a.size() > 2 ? button(a.get(2)) : 0;
                int clicks = cmd.equals("doubleclick") ? 2 : 1;
                out.add(new Step(line, h -> { h.input().mouseX = x; h.input().mouseY = y; }, 1, 0));
                for (int i = 0; i < clicks; i++) {
                    out.add(new Step(line, h -> h.input().buttons[button] = true, 1, 0));
                    out.add(new Step(line, h -> h.input().buttons[button] = false, 1, 0));
                }
                break;
            }
            case "press": {
                int button = a.isEmpty() ? 0 : button(a.get(0));
                out.add(new Step(line, h -> h.input().buttons[button] = true, 1, 0));
                break;
            }
            case "release": {
                if (!a.isEmpty() && isModifier(a.get(0))) {
                    String mod = a.get(0);
                    out.add(new Step(line, h -> modifier(h.input(), mod, false), 1, 0));
                } else {
                    int button = a.isEmpty() ? 0 : button(a.get(0));
                    out.add(new Step(line, h -> h.input().buttons[button] = false, 1, 0));
                }
                break;
            }
            case "hold": {
                String mod = a.get(0);
                if (!isModifier(mod)) throw new IllegalArgumentException("hold takes ctrl, shift or alt");
                out.add(new Step(line, h -> modifier(h.input(), mod, true), 1, 0));
                break;
            }
            case "drag": {
                float x1 = Float.parseFloat(a.get(0));
                float y1 = Float.parseFloat(a.get(1));
                float x2 = Float.parseFloat(a.get(2));
                float y2 = Float.parseFloat(a.get(3));
                out.add(new Step(line, h -> { h.input().mouseX = x1; h.input().mouseY = y1; }, 1, 0));
                out.add(new Step(line, h -> h.input().buttons[0] = true, 1, 0));
                int parts = 8;
                for (int i = 1; i <= parts; i++) {
                    float t = i / (float) parts;
                    out.add(new Step(line, h -> {
                        h.input().mouseX = x1 + (x2 - x1) * t;
                        h.input().mouseY = y1 + (y2 - y1) * t;
                    }, 1, 0));
                }
                out.add(new Step(line, h -> h.input().buttons[0] = false, 1, 0));
                break;
            }
            case "scroll": {
                float dy = Float.parseFloat(a.get(0));
                out.add(new Step(line, h -> h.input().wheel = dy, 1, 0));
                break;
            }
            case "key": {
                String[] parts = a.get(0).toLowerCase(Locale.ROOT).split("\\+");
                List<String> mods = new ArrayList<>(Arrays.asList(parts).subList(0, parts.length - 1));
                Integer key = KEYS.get(parts[parts.length - 1]);
                if (key == null) throw new IllegalArgumentException("unknown key " + parts[parts.length - 1]);
                for (String m : mods) if (!isModifier(m)) throw new IllegalArgumentException("unknown modifier " + m);
                out.add(new Step(line, h -> {
                    for (String m : mods) modifier(h.input(), m, true);
                    h.input().keys.add(key);
                }, 1, 0));
                out.add(new Step(line, h -> {
                    h.input().keys.remove(key);
                    for (String m : mods) modifier(h.input(), m, false);
                }, 1, 0));
                break;
            }
            case "type": {
                String text = String.join(" ", a);
                out.add(new Step(line, h -> h.input().chars.append(text), 1, 0));
                break;
            }
            case "view": {
                for (String v : a) {
                    boolean on = !v.startsWith("-");
                    String name = on ? v : v.substring(1);
                    out.add(new Step(line, h -> h.view(name, on), 0, 0));
                }
                out.add(new Step(line, null, 2, 0));
                break;
            }
            case "set": {
                String field = a.get(0);
                String value = String.join(" ", a.subList(1, a.size()));
                out.add(new Step(line, h -> h.set(field, value), 2, 0));
                break;
            }
            case "scale": {
                String value = a.get(0);
                out.add(new Step(line, h -> h.scale(value), 3, 0));
                break;
            }
            case "ui": {
                boolean open = !a.get(0).equalsIgnoreCase("closed") && !a.get(0).equalsIgnoreCase("close");
                out.add(new Step(line, h -> h.ui(open), 2, 0));
                break;
            }
            case "select": {
                String[] range = a.get(0).split("-");
                int first = Integer.parseInt(range[0]);
                int last = range.length > 1 ? Integer.parseInt(range[1]) : first;
                out.add(new Step(line, h -> h.select(first - 1, last - first + 1), 2, 0));
                break;
            }
            case "shot": {
                String path = a.get(0);
                String window = null;
                int[] rect = null;
                if (a.size() >= 3 && a.get(1).equalsIgnoreCase("window")) {
                    window = String.join(" ", a.subList(2, a.size()));
                } else if (a.size() >= 5) {
                    rect = new int[]{Integer.parseInt(a.get(1)), Integer.parseInt(a.get(2)),
                            Integer.parseInt(a.get(3)), Integer.parseInt(a.get(4))};
                }
                String w = window;
                int[] r = rect;
                out.add(new Step(line, null, 2, 0));
                out.add(new Step(line, h -> h.capture(path, w, r), 1, 0));
                break;
            }
            case "place": {
                String name = a.get(0);
                float x = Float.parseFloat(a.get(1));
                float y = Float.parseFloat(a.get(2));
                float w = a.size() >= 5 ? Float.parseFloat(a.get(3)) : 0f;
                float h = a.size() >= 5 ? Float.parseFloat(a.get(4)) : 0f;
                out.add(new Step(line, host -> host.place(name, x, y, w, h), 2, 0));
                break;
            }
            case "focus": {
                String name = String.join(" ", a);
                out.add(new Step(line, host -> host.focus(name), 2, 0));
                break;
            }
            case "windows":
                out.add(new Step(line, Host::printWindows, 0, 0));
                break;
            case "exit":
            case "quit":
                out.add(new Step(line, Host::exit, 0, 0));
                break;
            default:
                throw new IllegalArgumentException("unknown command " + cmd);
        }
    }

    private static boolean isModifier(String m) {
        String s = m.toLowerCase(Locale.ROOT);
        return s.equals("ctrl") || s.equals("shift") || s.equals("alt");
    }

    private static void modifier(PreviewInput in, String m, boolean down) {
        switch (m.toLowerCase(Locale.ROOT)) {
            case "ctrl":
                in.ctrl = down;
                setKey(in, GLFW.GLFW_KEY_LEFT_CONTROL, down);
                break;
            case "shift":
                in.shift = down;
                setKey(in, GLFW.GLFW_KEY_LEFT_SHIFT, down);
                break;
            default:
                in.alt = down;
                setKey(in, GLFW.GLFW_KEY_LEFT_ALT, down);
                break;
        }
    }

    private static void setKey(PreviewInput in, int key, boolean down) {
        if (down) in.keys.add(key);
        else in.keys.remove(key);
    }

    private static int button(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "left":
                return 0;
            case "right":
                return 1;
            case "middle":
                return 2;
            default:
                throw new IllegalArgumentException("unknown mouse button " + name);
        }
    }

    private static List<String> tokenize(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean any = false;
        for (char c : line.toCharArray()) {
            if (quote == 0 && (c == '"' || c == '\'')) {
                quote = c;
                any = true;
            } else if (c == quote) {
                quote = 0;
            } else if (Character.isWhitespace(c) && quote == 0) {
                if (any) out.add(current.toString());
                current.setLength(0);
                any = false;
            } else {
                current.append(c);
                any = true;
            }
        }
        if (any) out.add(current.toString());
        return out;
    }
}
