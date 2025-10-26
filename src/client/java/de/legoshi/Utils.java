package de.legoshi;

public class Utils {

    public static float parse(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
