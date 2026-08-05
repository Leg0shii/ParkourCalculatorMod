package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.ui.theme.HudMessageStyle;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HudMessagesTest {

    private static final long T0 = 1_000_000_000L;
    private static final long TICK = 100_000_000L;

    @Test
    public void consecutiveDuplicateBumpsCountInsteadOfStacking() {
        HudMessages messages = new HudMessages();
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0);
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0 + TICK);
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0 + 2 * TICK);

        List<HudMessages.Entry> visible = messages.visible(T0 + 2 * TICK, 5);
        assertEquals(1, visible.size());
        assertEquals("Undo (3x)", visible.get(0).display());
    }

    @Test
    public void differentMessageStartsNewEntryNewestFirst() {
        HudMessages messages = new HudMessages();
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0);
        messages.push("Redo", HudMessages.COLOR_DEFAULT, T0 + TICK);

        List<HudMessages.Entry> visible = messages.visible(T0 + TICK, 5);
        assertEquals(2, visible.size());
        assertEquals("Redo", visible.get(0).display());
        assertEquals("Undo", visible.get(1).display());
    }

    @Test
    public void nonConsecutiveRepeatIsANewEntry() {
        HudMessages messages = new HudMessages();
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0);
        messages.push("Redo", HudMessages.COLOR_DEFAULT, T0 + TICK);
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0 + 2 * TICK);

        List<HudMessages.Entry> visible = messages.visible(T0 + 2 * TICK, 5);
        assertEquals(3, visible.size());
        assertEquals("Undo", visible.get(0).display());
        assertEquals("Redo", visible.get(1).display());
        assertEquals("Undo", visible.get(2).display());
    }

    @Test
    public void sameTextDifferentColorDoesNotMerge() {
        HudMessages messages = new HudMessages();
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0);
        messages.push("Undo", HudMessageStyle.COLOR_WARN, T0 + TICK);

        assertEquals(2, messages.visible(T0 + TICK, 5).size());
    }

    @Test
    public void entriesExpireAfterDisplayDuration() {
        HudMessages messages = new HudMessages();
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0);

        assertEquals(1, messages.visible(T0 + HudMessages.DISPLAY_NANOS - 1, 5).size());
        assertTrue(messages.visible(T0 + HudMessages.DISPLAY_NANOS, 5).isEmpty());
    }

    @Test
    public void duplicateRefreshesTheTimer() {
        HudMessages messages = new HudMessages();
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0);
        long later = T0 + HudMessages.DISPLAY_NANOS - TICK;
        messages.push("Undo", HudMessages.COLOR_DEFAULT, later);

        List<HudMessages.Entry> visible = messages.visible(T0 + HudMessages.DISPLAY_NANOS, 5);
        assertEquals(1, visible.size());
        assertEquals("Undo (2x)", visible.get(0).display());
    }

    @Test
    public void visibleTrimsToMaxCountDroppingOldest() {
        HudMessages messages = new HudMessages();
        messages.push("one", HudMessages.COLOR_DEFAULT, T0);
        messages.push("two", HudMessages.COLOR_DEFAULT, T0 + TICK);
        messages.push("three", HudMessages.COLOR_DEFAULT, T0 + 2 * TICK);

        List<HudMessages.Entry> visible = messages.visible(T0 + 2 * TICK, 2);
        assertEquals(2, visible.size());
        assertEquals("three", visible.get(0).display());
        assertEquals("two", visible.get(1).display());
    }

    @Test
    public void alphaFadesOutAtEndOfLife() {
        HudMessages messages = new HudMessages();
        messages.push("Undo", HudMessages.COLOR_DEFAULT, T0);
        HudMessages.Entry entry = messages.visible(T0, 5).get(0);

        assertEquals(1f, entry.alphaAt(T0), 0f);
        assertEquals(1f, entry.alphaAt(T0 + HudMessages.DISPLAY_NANOS - HudMessages.FADE_NANOS), 0f);
        assertEquals(0.5f, entry.alphaAt(T0 + HudMessages.DISPLAY_NANOS - HudMessages.FADE_NANOS / 2), 1e-6f);
        assertEquals(0f, entry.alphaAt(T0 + HudMessages.DISPLAY_NANOS), 0f);
    }
}
