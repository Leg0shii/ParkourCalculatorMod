package de.legoshi.parkourcalc.core.ui;

import org.junit.Test;

import java.util.Arrays;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class CopyPasteTicksTest {

    private static InputData dataWithYaws(float... yaws) {
        InputData data = new InputData();
        for (float y : yaws) {
            InputRow row = new InputRow();
            row.setYaw(y);
            data.getRows().add(row);
        }
        return data;
    }

    private static InputOverlay overlay(InputData data, SelectionManager sel, int[] dirtySink) {
        return new InputOverlay(data, new Settings(), sel, t -> dirtySink[0] = t, null, null, null, null);
    }

    @Test
    public void pasteInsertsCopiesAfterSelectedRow() {
        InputData data = dataWithYaws(0f, 1f, 2f, 3f, 4f);
        SelectionManager sel = new SelectionManager(null);
        int[] dirty = { -99 };
        InputOverlay ov = overlay(data, sel, dirty);

        sel.selectRows(1, 3);
        ov.copySelectedRows();

        sel.selectRows(0, 1);
        ov.pasteRows();

        assertEquals(8, data.size());
        assertEquals(0f, data.get(0).getYaw(), 0f);
        assertEquals(1f, data.get(1).getYaw(), 0f);
        assertEquals(2f, data.get(2).getYaw(), 0f);
        assertEquals(3f, data.get(3).getYaw(), 0f);
        assertEquals(1f, data.get(4).getYaw(), 0f);
        assertEquals(1, dirty[0]);
        assertEquals(new TreeSet<>(Arrays.asList(1, 2, 3)), sel.getSelectedRows());
    }

    @Test
    public void pasteWithoutSelectionAppendsAtEnd() {
        InputData data = dataWithYaws(0f, 1f);
        SelectionManager sel = new SelectionManager(null);
        int[] dirty = { -99 };
        InputOverlay ov = overlay(data, sel, dirty);

        sel.selectRows(0, 2);
        ov.copySelectedRows();

        sel.clear();
        ov.pasteRows();

        assertEquals(4, data.size());
        assertEquals(0f, data.get(2).getYaw(), 0f);
        assertEquals(1f, data.get(3).getYaw(), 0f);
        assertEquals(2, dirty[0]);
    }

    @Test
    public void clipboardSurvivesDocumentSwap() {
        InputData first = dataWithYaws(5f, 6f);
        SelectionManager sel = new SelectionManager(null);
        int[] dirty = { -99 };
        InputOverlay ov = overlay(first, sel, dirty);

        sel.selectRows(0, 2);
        ov.copySelectedRows();

        first.clear();
        InputRow only = new InputRow();
        only.setYaw(9f);
        first.getRows().add(only);
        sel.clear();

        ov.pasteRows();

        assertEquals(3, first.size());
        assertEquals(9f, first.get(0).getYaw(), 0f);
        assertEquals(5f, first.get(1).getYaw(), 0f);
        assertEquals(6f, first.get(2).getYaw(), 0f);
    }

    @Test
    public void pastedRowsAreIndependentCopies() {
        InputData data = dataWithYaws(0f);
        SelectionManager sel = new SelectionManager(null);
        int[] dirty = { -99 };
        InputOverlay ov = overlay(data, sel, dirty);

        sel.selectRows(0, 1);
        ov.copySelectedRows();
        ov.pasteRows();

        assertEquals(2, data.size());
        assertNotSame(data.get(0), data.get(1));
        data.get(1).setYaw(42f);
        assertEquals(0f, data.get(0).getYaw(), 0f);
    }
}
