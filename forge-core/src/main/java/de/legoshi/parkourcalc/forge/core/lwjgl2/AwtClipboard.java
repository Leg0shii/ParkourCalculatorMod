package de.legoshi.parkourcalc.forge.core.lwjgl2;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

final class AwtClipboard {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5;

    private AwtClipboard() {
    }

    static void setString(String text) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                return;
            } catch (Exception e) {
                if (attempt == MAX_ATTEMPTS) {
                    System.err.println("[ParkourCalculator] clipboard copy skipped: " + e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
