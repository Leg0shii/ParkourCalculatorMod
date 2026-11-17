package de.legoshi.parkourcalc.preview;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

final class PreviewOptions {

    int width = 1920;
    int height = 1080;
    boolean headless;
    boolean help;
    boolean listViews;
    String tas;
    String mcVersion;
    String scale;
    String shot;
    String inline;
    Path script;
    Path settings;
    Path saves;
    Path ini;
    Path backgroundImage;
    float[] background = {0.11f, 0.12f, 0.15f};
    final List<String> views = new ArrayList<>();

    private PreviewOptions() {}

    static PreviewOptions parse(String[] args) {
        PreviewOptions o = new PreviewOptions();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--help":
                case "-h":
                    o.help = true;
                    break;
                case "--headless":
                    o.headless = true;
                    break;
                case "--list-views":
                    o.listViews = true;
                    break;
                case "--size": {
                    String[] wh = value(args, ++i, a).toLowerCase().split("x");
                    if (wh.length != 2) throw new IllegalArgumentException("--size takes WIDTHxHEIGHT, e.g. 1920x1080");
                    o.width = Integer.parseInt(wh[0]);
                    o.height = Integer.parseInt(wh[1]);
                    break;
                }
                case "--tas":
                    o.tas = value(args, ++i, a);
                    break;
                case "--mc":
                    o.mcVersion = value(args, ++i, a);
                    break;
                case "--scale":
                    o.scale = value(args, ++i, a);
                    break;
                case "--view":
                    for (String v : value(args, ++i, a).split(",")) if (!v.trim().isEmpty()) o.views.add(v.trim());
                    break;
                case "--shot":
                    o.shot = value(args, ++i, a);
                    break;
                case "--do":
                    o.inline = value(args, ++i, a);
                    break;
                case "--script":
                    o.script = Paths.get(value(args, ++i, a));
                    break;
                case "--settings":
                    o.settings = Paths.get(value(args, ++i, a));
                    break;
                case "--saves":
                    o.saves = Paths.get(value(args, ++i, a));
                    break;
                case "--ini":
                    o.ini = Paths.get(value(args, ++i, a));
                    break;
                case "--background": {
                    String v = value(args, ++i, a);
                    if (v.startsWith("#") && (v.length() == 7)) {
                        int rgb = Integer.parseInt(v.substring(1), 16);
                        o.background = new float[]{(rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f};
                    } else {
                        o.backgroundImage = Paths.get(v);
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown option " + a);
            }
        }
        return o;
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) throw new IllegalArgumentException(option + " needs a value");
        return args[i];
    }

    static String usage() {
        return String.join("\n",
                "PKC UI preview: the real PKC ImGui windows outside Minecraft.",
                "",
                "Options:",
                "  --size WxH          window size in pixels (default 1920x1080)",
                "  --scale S           UI scale: auto or a preset (0.75, 1, 1.25, 1.5, 2, 2.5)",
                "  --view A,B          open windows by their View flag (see --list-views), -NAME closes one, e.g. AngleSolver,-TickInfo",
                "  --tas FILE          TAS save to load (default: a bundled HPK jump; none for an empty TAS)",
                "  --mc VERSION        game version the UI acts as (default: the TAS's version, else 26.3)",
                "  --settings FILE     start from a copy of a parkourcalculator.json (the file itself is never written)",
                "  --saves DIR         TAS folder the file browser lists (copied, never written)",
                "  --ini FILE          ImGui layout to start from, e.g. the game's parkourcalculator.ini (copied, never written)",
                "  --background V      #RRGGBB or an image file shown behind the UI, e.g. an in-game screenshot",
                "  --do \"CMD; CMD\"     script commands, separated by ;",
                "  --script FILE       script commands, one per line",
                "  --shot FILE         screenshot at the end of the script",
                "  --headless          no visible window; exits after the script (default shot: " + "build/ui-preview/preview.png)",
                "  --list-views        print the view names and exit",
                "",
                "Script commands (coordinates in window pixels, top-left origin):",
                "  view NAME [-NAME]   open (or with -, close) a window",
                "  set FIELD VALUE     set a Settings field, e.g. set showPath false",
                "  scale S             change the UI scale",
                "  ui open|closed      show the full UI or only the pinned panels",
                "  select A[-B]        select TAS rows A to B (1-based)",
                "  move X Y | click X Y [left|right|middle] | rightclick X Y | doubleclick X Y",
                "  press [BUTTON] | release [BUTTON] | drag X1 Y1 X2 Y2 | scroll DY",
                "  hold ctrl|shift|alt | release ctrl|shift|alt",
                "  key NAME            e.g. key enter, key ctrl+z, key down",
                "  type TEXT           types text into the focused field",
                "  frames N | wait MS  keep rendering",
                "  windows             print every window with its position and size",
                "  place NAME X Y [W H]  move (and resize) a window, NAME as printed by windows or its title",
                "  focus NAME          bring a window to the front",
                "  shot FILE [window NAME | X Y W H]   save a PNG of the screen, a window or a rectangle",
                "  exit");
    }
}
