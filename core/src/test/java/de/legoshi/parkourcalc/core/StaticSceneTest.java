package de.legoshi.parkourcalc.core;

import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.replay.StaticScene;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaticSceneTest {
    private StaticScene scene(String solids, String extra) {
        return new StaticScene(new JsonParser().parse("{\"version\":1,\"profile\":\"minecraft-1.8.9\",\"bounds\":[-20,-10,-20,20,20,20],\"solids\":" + solids + extra + "}").getAsJsonObject());
    }

    @Test public void wallStopsOnlyItsNormalVelocity() {
        StaticScene scene = scene("[[-10,0,-10,10,1,10],[1,1,-3,2,5,3]]", ",\"stepHeight\":0");
        StaticScene.Contact hit = scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(0.7,-0.0784,0.2), true, false);
        assertEquals(1-StaticScene.HALF_WIDTH, hit.position.x, 0);
        assertEquals(0, hit.velocity.x, 0);
        assertEquals(0.2, hit.velocity.z, 0);
        assertTrue(hit.horizontalCollision);
        assertTrue(hit.onGround);
        assertEquals(2, hit.clips.size());
        assertEquals(1, hit.clips.get(0).axis);
        assertEquals(0, hit.clips.get(1).axis);
        assertEquals(1, hit.clips.get(1).obstacle.min.x, 0);
        assertThrows(UnsupportedOperationException.class, () -> hit.clips.clear());
    }

    @Test public void entryAndExitFacesDoNotUseAnEpsilonOverlap() {
        StaticScene scene = scene("[[1,0,1,2,5,2]]", ",\"stepHeight\":0");
        Vec3dCore start = new Vec3dCore(0.5,1,1-StaticScene.HALF_WIDTH);
        StaticScene.Contact touch = scene.move(scene.startBox(start), new Vec3dCore(1,0,0), false, false);
        assertFalse(touch.horizontalCollision);
        assertEquals(1, touch.velocity.x, 0);
    }

    @Test public void landingIsResolvedBeforeHorizontalMovement() {
        StaticScene scene = scene("[[0,0,0,1,1,1]]", ",\"stepHeight\":0");
        StaticScene.Contact hit = scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(2,-0.1,0), false, false);
        assertTrue(hit.onGround);
        assertEquals(1, hit.position.y, 0);
        assertEquals(2.5, hit.position.x, 0);
    }

    @Test public void stepsUpHalfBlockButRespectsCeiling() {
        String floor = "[-10,0,-10,10,1,10],[1,1,-1,2,1.5,2]";
        StaticScene scene = scene("["+floor+"]", "");
        StaticScene.Contact step = scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(0.8,-0.0784,0), true, false);
        assertTrue(step.stepped);
        assertTrue(step.clips.stream().anyMatch(clip -> clip.axis == 1 && clip.obstacle.max.y == 1.5));
        assertFalse(step.clips.stream().anyMatch(clip -> clip.axis == 0 || clip.axis == 2));
        assertTrue(step.stepTriggers.stream().anyMatch(clip -> clip.axis == 0));
        assertEquals(1.5, step.position.y, 0);
        assertFalse(step.horizontalCollision);
        StaticScene low = scene("["+floor+",[-2,2.8,-2,3,4,3]]", "");
        StaticScene.Contact blocked = low.move(low.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(0.8,-0.0784,0), true, false);
        assertFalse(blocked.stepped);
        assertTrue(blocked.horizontalCollision);
    }

    @Test public void selectedStepRetainsItsFinalHorizontalContacts() {
        StaticScene scene = scene("[[-2,0,-2,1,1,3],[1,0,-2,3,1.5,3],[1.5,0,-3,2,6,3],[-3,0,1.5,3,6,2]]", "");
        StaticScene.Contact step = scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(0.8,-0.0784,0.8), true, false);
        assertTrue(step.stepped);
        assertEquals(1.5, step.position.y, 0);
        assertEquals(1.5-StaticScene.HALF_WIDTH, step.position.x, 0);
        assertEquals(1.5-StaticScene.HALF_WIDTH, step.position.z, 0);
        assertTrue(step.blockedX);
        assertTrue(step.blockedZ);
        assertEquals(0, step.velocity.x, 0);
        assertEquals(0, step.velocity.z, 0);
        assertTrue(step.clips.stream().anyMatch(clip -> clip.axis == 0));
        assertTrue(step.clips.stream().anyMatch(clip -> clip.axis == 2));
        assertTrue(step.clips.stream().anyMatch(clip -> clip.axis == 1));
    }

    @Test public void sneakingAtAnEdgeKeepsTheUnclippedCarry() {
        StaticScene scene = scene("[[0,0,0,1,1,1]]", "");
        StaticScene.Contact hit = scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(0.9,-0.0784,0), true, true);
        assertTrue(hit.position.x < 1.4);
        assertFalse(hit.horizontalCollision);
        assertEquals(0.9, hit.velocity.x, 0);
    }

    @Test public void ladderClimbCarryRequiresPostMovementOccupancyAndWallHit() {
        StaticScene scene = scene("[[1.75,0,-1,2,5,2]]", ",\"stepHeight\":0,\"ladders\":[[1,1,0]]");
        StaticScene.Contact entry = scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(1,0,0), false, false);
        assertTrue(entry.horizontalCollision);
        assertTrue(scene.ladder(entry.position));
        assertEquals(0.2, entry.velocity.y, 0);
        assertEquals(0, entry.velocity.x, 0);
        StaticScene.Contact miss = scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(0.6,0,0), false, false);
        assertFalse(miss.horizontalCollision);
        assertEquals(0, miss.velocity.y, 0);
    }

    @Test public void refusesMissingGeometryCoverageAndOverlappingStarts() {
        StaticScene scene = scene("[[0,0,0,1,1,1]]", "");
        assertThrows(IllegalArgumentException.class, () -> scene.startBox(new Vec3dCore(0.5,0,0.5)));
        assertThrows(IllegalArgumentException.class, () -> scene.move(scene.startBox(new Vec3dCore(0.5,1,0.5)), new Vec3dCore(100,0,0), false, false));
    }
}
