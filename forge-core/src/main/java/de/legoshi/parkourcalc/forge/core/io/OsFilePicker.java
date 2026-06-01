package de.legoshi.parkourcalc.forge.core.io;

import de.legoshi.parkourcalc.core.ports.FilePickerPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** OS-native file picker via ProcessBuilder; Forge has no TinyFileDialogs on LWJGL2. */
public final class OsFilePicker implements FilePickerPort {

    @Override
    public Path pickTasFile() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean windows = os.contains("win");
        List<String> cmd;
        Charset enc;
        if (windows) {
            cmd = buildWindowsCmd();
            enc = StandardCharsets.UTF_8;
        } else if (os.contains("mac")) {
            cmd = buildMacCmd();
            enc = StandardCharsets.UTF_8;
        } else {
            cmd = buildLinuxCmd();
            enc = StandardCharsets.UTF_8;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            String out = readAll(p, enc);
            int exit = p.waitFor();
            String trimmed = out.trim();
            if (trimmed.isEmpty()) return null;
            if (exit != 0 && !windows) return null;
            return Paths.get(trimmed);
        } catch (IOException | InterruptedException e) {
            System.err.println("[ParkourCalculator] File picker failed: " + e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String readAll(Process p, Charset enc) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), enc));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }

    private static List<String> buildWindowsCmd() {
        // -STA: WinForms OpenFileDialog requires single-threaded apartment for COM.
        // TopMost parent form so the dialog surfaces above the MC window.
        String script =
                "[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new();"
                + "Add-Type -AssemblyName System.Windows.Forms;"
                + "$f=New-Object System.Windows.Forms.Form;"
                + "$f.TopMost=$true;$f.StartPosition='Manual';"
                + "$f.Location=New-Object System.Drawing.Point(-2000,-2000);"
                + "$f.Size=New-Object System.Drawing.Size(1,1);"
                + "$f.ShowInTaskbar=$false;$f.Show();"
                + "$d=New-Object System.Windows.Forms.OpenFileDialog;"
                + "$d.Filter='TAS files (*.tas)|*.tas';"
                + "$d.Title='Import .tas';$d.Multiselect=$false;"
                + "$r=$d.ShowDialog($f);$f.Close();"
                + "if ($r -eq 'OK') { Write-Output $d.FileName }";
        List<String> out = new ArrayList<String>();
        out.add("powershell");
        out.add("-NoProfile");
        out.add("-STA");
        out.add("-Command");
        out.add(script);
        return out;
    }

    private static List<String> buildMacCmd() {
        List<String> out = new ArrayList<String>();
        out.add("osascript");
        out.add("-e");
        out.add("POSIX path of (choose file with prompt \"Import .json\" of type {\"json\"})");
        return out;
    }

    private static List<String> buildLinuxCmd() {
        List<String> out = new ArrayList<String>();
        out.add("zenity");
        out.add("--file-selection");
        out.add("--title=Import .json");
        out.add("--file-filter=TAS files | *.json");
        return out;
    }
}
