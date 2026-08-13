package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SequenceIdentityTest {

    private static List<ColdStratFinder.Segment> oneSegment(int press) {
        return java.util.Collections.singletonList(new ColdStratFinder.Segment(0, press));
    }

    @Test
    public void leadingWaitTicksDoNotChangeTheIdentity() {
        ColdStratFinder.SeqInfo padded = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.NONE, KeyLine.NONE, KeyLine.W}, oneSegment(2), KeyLine.W);
        ColdStratFinder.SeqInfo bare = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.W}, oneSegment(0), KeyLine.W);
        assertEquals(bare.orderKey, padded.orderKey);
        assertEquals(bare.concreteKey, padded.concreteKey);
    }

    @Test
    public void aMidMomentumComboChangeIsADifferentStrat() {
        ColdStratFinder.SeqInfo pure = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.W, KeyLine.W}, oneSegment(1), KeyLine.W);
        ColdStratFinder.SeqInfo mixed = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.W, KeyLine.WA}, oneSegment(1), KeyLine.W);
        assertNotEquals(pure.orderKey, mixed.orderKey);
        assertEquals("W", ColdStratFinder.sequenceLabel(pure.seq, KeyLine.W));
        assertEquals("W>WA", ColdStratFinder.sequenceLabel(mixed.seq, KeyLine.WA));
    }

    @Test
    public void holdLengthVariantsShareTheIdentityButNotTheConcreteLine() {
        ColdStratFinder.SeqInfo hold1 = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.NONE, KeyLine.W}, oneSegment(1), KeyLine.W);
        ColdStratFinder.SeqInfo hold2 = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.W, KeyLine.W}, oneSegment(1), KeyLine.W);
        assertEquals(hold1.orderKey, hold2.orderKey);
        assertNotEquals(hold1.concreteKey, hold2.concreteKey);
    }

    @Test
    public void aDifferentAirComboShowsInIdentityAndLabel() {
        ColdStratFinder.SeqInfo wAir = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.W}, oneSegment(0), KeyLine.W);
        ColdStratFinder.SeqInfo waAir = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.W}, oneSegment(0), KeyLine.WA);
        assertNotEquals(wAir.orderKey, waAir.orderKey);
        assertEquals("W, air WA", ColdStratFinder.sequenceLabel(waAir.seq, KeyLine.WA));
        assertEquals("W", ColdStratFinder.sequenceLabel(wAir.seq, KeyLine.W));
    }

    @Test
    public void alwaysWaKeepsItsFullIdentity() {
        ColdStratFinder.SeqInfo wa = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.WA, KeyLine.WA, KeyLine.WA}, oneSegment(2), KeyLine.WA);
        assertEquals("WA", ColdStratFinder.sequenceLabel(wa.seq, KeyLine.WA));
        assertEquals(1, wa.seq.length);
        assertEquals(1, wa.seq[0].length);
        assertEquals(KeyLine.WA, wa.seq[0][0]);
        ColdStratFinder.SeqInfo waDropped = ColdStratFinder.sequenceOf(
                new int[]{KeyLine.WA, KeyLine.W, KeyLine.WA}, oneSegment(2), KeyLine.WA);
        assertNotEquals(wa.orderKey, waDropped.orderKey);
        assertEquals("WA>W>WA", ColdStratFinder.sequenceLabel(waDropped.seq, KeyLine.WA));
        assertEquals(3, waDropped.seq[0].length);
        org.junit.Assert.assertTrue(Arrays.equals(
                new int[]{KeyLine.WA, KeyLine.W, KeyLine.WA}, waDropped.seq[0]));
    }
}
