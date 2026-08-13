package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import java.util.Locale;

public final class StratLabels {

    private StratLabels() {
    }

    public static String display(String label) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        if ("self".equals(label)) {
            return "original";
        }
        int at = label.indexOf('@');
        if (at > 0) {
            return shiftDisplay(label, at);
        }
        int slash = label.indexOf('/');
        if (slash >= 0) {
            String fam = familyDisplay(label.substring(0, slash));
            String shape = shapeDisplay(label.substring(slash + 1));
            return shape.isEmpty() ? fam : fam + ", " + shape;
        }
        if (label.startsWith("nt") || "ja".equals(label)) {
            return shapeDisplay(label);
        }
        return familyDisplay(label);
    }

    public static String describe(String label) {
        if (label == null) {
            return "";
        }
        if ("self".equals(label)) {
            return "Your current inputs, re-solved as-is.";
        }
        int at = label.indexOf('@');
        if (at > 0) {
            return "Your inputs with one key timing moved by one tick.";
        }
        StringBuilder sb = new StringBuilder();
        String base = label;
        int slash = label.indexOf('/');
        if (slash >= 0) {
            base = label.substring(0, slash);
        }
        String famDesc = familyDescribe(base);
        if (!famDesc.isEmpty()) {
            sb.append(famDesc);
        }
        String shape = slash >= 0 ? label.substring(slash + 1) : (label.startsWith("nt") || "ja".equals(label) ? label : "");
        String shapeDesc = shapeDescribe(shape);
        if (!shapeDesc.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(shapeDesc);
        }
        return sb.toString();
    }

    private static String shiftDisplay(String label, int at) {
        String key = keyWord(label.substring(0, at));
        String rest = label.substring(at + 1);
        int i = 0;
        while (i < rest.length() && Character.isDigit(rest.charAt(i))) {
            i++;
        }
        int row = i > 0 ? Integer.parseInt(rest.substring(0, i)) : 0;
        String suffix = rest.substring(i);
        String tick = "T" + (row + 1);
        if ("later".equals(suffix)) {
            return "press " + key + " later (" + tick + ")";
        }
        if ("earlier".equals(suffix)) {
            return "press " + key + " earlier (" + tick + ")";
        }
        if ("holdlonger".equals(suffix)) {
            return "hold " + key + " longer (" + tick + ")";
        }
        if ("releaseearlier".equals(suffix)) {
            return "release " + key + " earlier (" + tick + ")";
        }
        if ("taplater".equals(suffix)) {
            return "tap " + key + " later (" + tick + ")";
        }
        if ("tapearlier".equals(suffix)) {
            return "tap " + key + " earlier (" + tick + ")";
        }
        return key + " " + suffix + " (" + tick + ")";
    }

    private static String keyWord(String key) {
        if ("SPRINT".equals(key) || "SNEAK".equals(key) || "JUMP".equals(key)) {
            return key.toLowerCase(Locale.ROOT);
        }
        return key;
    }

    private static String familyDisplay(String base) {
        if (base.startsWith("bwmm")) {
            String tail = base.length() > 4 && base.charAt(4) == '+' ? base.substring(5) : "";
            return tail.isEmpty() ? "bwmm" : "bwmm " + familyDisplay(tail);
        }
        if (base.startsWith("fmm")) {
            return "fmm " + base.substring(3);
        }
        if (base.startsWith("pessi")) {
            return "pessi " + base.substring(5);
        }
        if (base.startsWith("markA") || base.startsWith("markD")) {
            return "mark " + base.charAt(4) + " " + base.substring(5);
        }
        if (base.startsWith("run") && base.endsWith("+jam")) {
            String d = base.substring(3, base.length() - 4);
            return "0".equals(d) ? "jam" : "run " + d + " jam";
        }
        if ("jam".equals(base)) {
            return "jam";
        }
        return base;
    }

    private static String shapeDisplay(String shape) {
        if ("nt".equals(shape)) {
            return "nt";
        }
        if ("ja".equals(shape)) {
            return "ja";
        }
        if ("nt45".equals(shape)) {
            return "nt 45";
        }
        if (shape.startsWith("nt[") && shape.endsWith("]")) {
            String inner = shape.substring(3, shape.length() - 1);
            boolean press = inner.endsWith("*");
            if (press) {
                inner = inner.substring(0, inner.length() - 1);
            }
            int bar = inner.indexOf('|');
            String momentum = inner.substring(0, bar);
            String air = inner.substring(bar + 1);
            StringBuilder sb = new StringBuilder("nt");
            if (!"-".equals(momentum)) {
                sb.append(", run ").append(momentum);
            }
            if (!"-".equals(air)) {
                sb.append(", air ").append(air);
                if (press) {
                    sb.append('*');
                }
            }
            return sb.toString();
        }
        return shape;
    }

    private static String familyDescribe(String base) {
        if (base.startsWith("bwmm")) {
            String tail = base.length() > 4 && base.charAt(4) == '+' ? base.substring(5) : "";
            String head = "Backwards-momentum: S-jump arc backwards, then W into the fire.";
            String tailDesc = familyDescribe(tail);
            return tailDesc.isEmpty() ? head : head + " " + tailDesc;
        }
        if (base.startsWith("fmm")) {
            return "fmm " + base.substring(3) + ": fire on W and jump, sprint re-engaged "
                    + base.substring(3) + "t after the fire.";
        }
        if (base.startsWith("pessi")) {
            return "pessi " + base.substring(5) + ": fire on jump alone, W and sprint "
                    + base.substring(5) + "t after the fire.";
        }
        if (base.startsWith("markA") || base.startsWith("markD")) {
            return "mark: fire on jump plus " + base.charAt(4) + ", W and sprint "
                    + base.substring(5) + "t after the fire.";
        }
        if (base.startsWith("run") && base.endsWith("+jam")) {
            String d = base.substring(3, base.length() - 4);
            if ("0".equals(d)) {
                return "jam: W, sprint and jump pressed together on the fire tick.";
            }
            return "run " + d + " jam: " + d + "t sprint runway, then jump.";
        }
        return "";
    }

    private static String shapeDescribe(String shape) {
        if (shape.isEmpty()) {
            return "";
        }
        if ("nt".equals(shape)) {
            return "No turn: facing stays fixed for the whole jump.";
        }
        if ("ja".equals(shape)) {
            return "Jump angle: facing stays fixed until the jump tick, one turn there.";
        }
        if ("nt45".equals(shape)) {
            return "No turn with 45 strafes: fixed facing, strafe keys carry the angle.";
        }
        if (shape.startsWith("nt[")) {
            String d = shapeDisplay(shape);
            return "No turn, fixed facing. Strafe pattern: " + d.substring(2).replaceFirst("^, ", "")
                    + ". A star means the air strafe starts on the fire tick itself.";
        }
        return "";
    }
}
