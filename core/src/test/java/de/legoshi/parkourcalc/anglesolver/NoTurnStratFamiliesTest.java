package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StratFamilies;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NoTurnStratFamiliesTest {

    private static StratFamilies library(String... rows) throws Exception {
        StringBuilder text = new StringBuilder();
        for (String row : rows) text.append(row).append('\n');
        return StratFamilies.fromSchedules(
                StratFamilies.readSchedules(new BufferedReader(new StringReader(text.toString()))), -1);
    }

    private static int[] keys(String text, int length) {
        return NoTurnKeys.parse(text, length);
    }

    @Test
    public void aScheduleIsCutAtItsJumpTicks() throws Exception {
        StratFamilies lib = library("3;;2,15;Sx2 WAx12 - W WAx10");
        assertEquals(1, lib.count(StratFamilies.RUN_UP));
        assertEquals(1, lib.count(StratFamilies.MIDDLE));
        assertEquals(1, lib.count(StratFamilies.FINAL));
        StratFamilies.Segment[] segments = lib.segmentsFor(26, new int[]{2, 15});
        assertEquals(3, segments.length);
        assertEquals(0, segments[0].start);
        assertEquals(2, segments[0].length);
        assertEquals(2, segments[1].start);
        assertEquals(13, segments[1].length);
        assertEquals(15, segments[2].start);
        assertEquals(11, segments[2].length);
    }

    @Test
    public void aFamilyAndItsMirrorShareTheWeight() throws Exception {
        StratFamilies lib = library("3;;0;W WAx10", "1;;0;W WDx10");
        assertEquals(1, lib.count(StratFamilies.FINAL));
        StratFamilies.Segment[] segments = lib.segmentsFor(11, new int[]{0});
        assertEquals(1, segments.length);
        assertEquals(2, segments[0].cost.length);
        assertEquals(segments[0].cost[0], segments[0].cost[1]);
        assertEquals(2, segments[0].cost[0]);
        assertTrue(segments[0].indexOf(keys("W WAx10", 11)) >= 0);
        assertTrue(segments[0].indexOf(keys("W WDx10", 11)) >= 0);
    }

    @Test
    public void lateChangesOfAMiddleSegmentAreCountedFromTheNextJump() throws Exception {
        StratFamilies lib = library("1;;0,13;WAx11 Sx2 Wx11");
        StratFamilies.Segment[] shorter = lib.segmentsFor(23, new int[]{0, 12});
        assertNotNull(shorter);
        assertTrue(shorter[0].indexOf(keys("WAx10 Sx2 Wx11", 23)) >= 0);
        StratFamilies.Segment[] longer = lib.segmentsFor(25, new int[]{0, 14});
        assertTrue(longer[0].indexOf(keys("WAx12 Sx2 Wx11", 25)) >= 0);
    }

    @Test
    public void earlyChangesAreCountedFromTheJump() throws Exception {
        StratFamilies lib = library("1;;0,13;W WAx12 Wx11");
        StratFamilies.Segment[] longer = lib.segmentsFor(25, new int[]{0, 14});
        assertTrue(longer[0].indexOf(keys("W WAx13 Wx11", 25)) >= 0);
    }

    @Test
    public void theRunUpIsCountedBackFromTheFirstJump() throws Exception {
        StratFamilies lib = library("1;;4;Sx2 -x2 Wx11");
        StratFamilies.Segment[] segments = lib.segmentsFor(18, new int[]{7});
        assertArrayEquals(keys("Sx5 -x2", 7), segments[0].keys[0]);
        assertNull(lib.segmentsFor(13, new int[]{2}));
    }

    @Test
    public void candidatesAreSortedByCostAndCommonFamiliesAreCheap() throws Exception {
        StratFamilies lib = library("6;;0;Wx11", "1;;0;Sx11", "1;;0;-x11");
        StratFamilies.Segment segment = lib.segmentsFor(11, new int[]{0})[0];
        assertArrayEquals(keys("Wx11", 11), segment.keys[0]);
        for (int i = 1; i < segment.cost.length; i++) assertTrue(segment.cost[i - 1] <= segment.cost[i]);
        assertEquals(1, segment.cost[0]);
        assertEquals(6, segment.cost[1]);
        StratFamilies.Segment[] all = {segment};
        assertEquals(1, StratFamilies.minCost(all));
        assertEquals(6, StratFamilies.maxCost(all));
        assertEquals(6, StratFamilies.costOf(all, keys("Sx11", 11)));
        assertEquals(-1, StratFamilies.costOf(all, keys("Ax11", 11)));
    }

    @Test
    public void aRowIsLeftOutByItsOrigin() throws Exception {
        List<StratFamilies.Schedule> rows = StratFamilies.readSchedules(new BufferedReader(new StringReader(
                "1;716;0;Wx11\n1;;0;Sx11\n1;119,716;0;Ax11\n")));
        assertEquals(3, StratFamilies.fromSchedules(rows, -1).count(StratFamilies.FINAL));
        assertEquals(1, StratFamilies.fromSchedules(rows, 716).count(StratFamilies.FINAL));
        assertEquals(2, StratFamilies.fromSchedules(rows, 119).count(StratFamilies.FINAL));
    }

    @Test
    public void aCopyOfALeftOutRowIsLeftOutToo() throws Exception {
        List<StratFamilies.Schedule> rows = StratFamilies.readSchedules(new BufferedReader(new StringReader(
                "1;716;0;Wx11\n4;;0;Wx11\n1;;0;Sx11\n1;119;0;Ax11\n")));
        assertEquals(3, StratFamilies.fromSchedules(rows).count(StratFamilies.FINAL));
        assertEquals(2, StratFamilies.fromSchedules(rows, 716).count(StratFamilies.FINAL));
        assertEquals(1, StratFamilies.fromSchedules(rows, 716, 119).count(StratFamilies.FINAL));
    }

    @Test
    public void aProblemWithoutJumpsHasNoSegments() throws Exception {
        StratFamilies lib = library("1;;0;Wx11");
        assertNull(lib.segmentsFor(11, new int[0]));
        assertNull(lib.segmentsFor(11, new int[]{11}));
    }

    @Test
    public void theShippedLibraryCoversMostHumanStratsWithTheirOwnJumpLeftOut() throws Exception {
        File root = new File(Fixtures.class.getResource("/captures/hpk").toURI());
        Pattern number = Pattern.compile("^[jJ](\\d+)");
        List<String> missing = new ArrayList<>();
        int total = 0;
        File[] folders = root.listFiles();
        Arrays.sort(folders);
        for (File folder : folders) {
            File[] files = folder.listFiles();
            Arrays.sort(files);
            for (File file : files) {
                String name = file.getName().replace(".json", "");
                Matcher m = number.matcher(name);
                assertTrue(name, m.find());
                NoTurnProblem p = NoTurnCapture.problem("hpk/" + folder.getName() + "/" + name);
                StratFamilies lib = StratFamilies.shippedWithout(Integer.parseInt(m.group(1)));
                StratFamilies.Segment[] segments = lib.segmentsFor(p.n, p.jumpTicks);
                assertNotNull(name, segments);
                total++;
                if (StratFamilies.costOf(segments, p.baseCombos()) < 0) missing.add(name);
            }
        }
        assertEquals(59, total);
        assertTrue("human Strats outside the families: " + missing, missing.size() <= 8);
    }
}
