package de.legoshi.parkourcalc.core.multireplay;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class MultiReplayLoader {

    public static final class Outcome {
        public final List<ReplayTrack> tracks;
        public final List<String> errors;

        Outcome(List<ReplayTrack> tracks, List<String> errors) {
            this.tracks = Collections.unmodifiableList(tracks);
            this.errors = Collections.unmodifiableList(errors);
        }
    }

    private final Simulator simulator;
    private final SimulationRunner mainRunner;
    private final MinecraftAccess mc;
    private final Runnable retriggerMainSimulation;

    public MultiReplayLoader(Simulator simulator, SimulationRunner mainRunner, MinecraftAccess mc,
                             Runnable retriggerMainSimulation) {
        this.simulator = simulator;
        this.mainRunner = mainRunner;
        this.mc = mc;
        this.retriggerMainSimulation = retriggerMainSimulation;
    }

    public Outcome load(Path folder) {
        List<ReplayTrack> tracks = new ArrayList<ReplayTrack>();
        List<String> errors = new ArrayList<String>();
        if (folder == null || !Files.isDirectory(folder)) {
            errors.add("Not a folder: " + folder);
            return new Outcome(tracks, errors);
        }
        if (!mc.isReady()) {
            errors.add("No world loaded.");
            return new Outcome(tracks, errors);
        }
        List<Path> files = listJsonFiles(folder, errors);
        if (files.isEmpty()) {
            if (errors.isEmpty()) errors.add("No .json files in " + folder);
            return new Outcome(tracks, errors);
        }

        Vec3dCore startPos = mainRunner.getStartPosition();
        Vec3dCore startVel = mainRunner.getStartVelocity();
        float startYaw = mainRunner.getStartYaw();
        float startPitch = mainRunner.getStartPitch();
        StartResumeState startResume = mainRunner.getStartResumeState();

        final SimulationRunner scratch = new SimulationRunner(simulator);
        try {
            for (Path file : files) {
                String name = stripJson(file.getFileName().toString());
                try {
                    final SaveFile parsed = parse(file);
                    final InputData data = new InputData();
                    SaveIO.applyRowsTo(parsed, data);
                    List<TickState> states = mc.runOnServerThread(new Supplier<List<TickState>>() {
                        @Override
                        public List<TickState> get() {
                            return simulate(scratch, parsed, data);
                        }
                    });
                    List<Vec3dCore> positions = new ArrayList<Vec3dCore>(states.size());
                    float[] yaws = new float[states.size()];
                    boolean[] sneaking = new boolean[states.size()];
                    boolean[] swings = new boolean[states.size()];
                    for (int i = 0; i < states.size(); i++) {
                        positions.add(states.get(i).position);
                        yaws[i] = states.get(i).yaw;
                        sneaking[i] = states.get(i).sneaking;
                        swings[i] = i > 0 && i - 1 < data.size() && data.get(i - 1).isKeyActive(InputRow.Key.LEFT_CLICK);
                    }
                    float[] pitches = foldPitches(parsed, data, states.size());
                    tracks.add(new ReplayTrack(name, ReplayPalette.colorFor(tracks.size()), positions, yaws, pitches,
                            sneaking, swings));
                } catch (Exception e) {
                    errors.add(name + ": " + (e.getMessage() != null ? e.getMessage() : e.toString()));
                }
            }
        } finally {
            mainRunner.setStartPosition(startPos);
            mainRunner.setStartVelocity(startVel);
            mainRunner.setStartYaw(startYaw);
            mainRunner.setStartPitch(startPitch);
            mainRunner.setStartResumeState(startResume);
            retriggerMainSimulation.run();
        }
        return new Outcome(tracks, errors);
    }

    private static List<Path> listJsonFiles(Path folder, List<String> errors) {
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.json")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) files.add(p);
            }
        } catch (IOException e) {
            errors.add("Failed to list folder: " + e.getMessage());
        }
        Collections.sort(files, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }
        });
        return files;
    }

    private static SaveFile parse(Path file) throws IOException {
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        SaveFile parsed = SaveIO.parseSafe(json);
        if (parsed == null) throw new IOException("not valid JSON");
        if (parsed.start == null || parsed.start.pos == null || parsed.start.pos.length < 3) {
            throw new IOException("missing start position");
        }
        if (parsed.version != SaveFile.FORMAT_VERSION) {
            throw new IOException("unsupported save format version " + parsed.version);
        }
        return parsed;
    }

    private static List<TickState> simulate(SimulationRunner scratch, SaveFile file, InputData data) {
        SaveFile.Start s = file.start;
        scratch.setStartPosition(SaveIO.posOf(s));
        scratch.setStartVelocity(SaveIO.velOf(s));
        scratch.setStartYaw(s.yaw);
        scratch.setStartPitch(s.pitch != null ? s.pitch : PlaybackController.DEFAULT_PITCH);
        scratch.setStartResumeState(SaveIO.resumeOf(s));
        return new ArrayList<TickState>(scratch.simulate(data));
    }

    static float[] foldPitches(SaveFile file, InputData data, int count) {
        float[] pitches = new float[count];
        if (count == 0) return pitches;
        pitches[0] = file.start.pitch != null ? file.start.pitch : PlaybackController.DEFAULT_PITCH;
        for (int i = 1; i < count; i++) {
            pitches[i] = i - 1 < data.size()
                    ? PlaybackController.applyPitch(pitches[i - 1], data.get(i - 1))
                    : pitches[i - 1];
        }
        return pitches;
    }

    static String stripJson(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }
}
