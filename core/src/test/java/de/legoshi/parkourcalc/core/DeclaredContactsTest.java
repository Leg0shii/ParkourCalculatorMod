package de.legoshi.parkourcalc.core;

import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.replay.DeclaredContacts;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeclaredContactsTest {
    private DeclaredContacts contacts() {
        return new DeclaredContacts(new JsonParser().parse("{\"source\":{\"startTick\":0},\"contacts\":[{\"state\":1,\"axis\":0,\"plane\":0.5,\"direction\":1}]}").getAsJsonObject());
    }

    @Test public void resetsOnlyBlockedCarryAtTheCorrectState() {
        DeclaredContacts contacts = contacts();
        ExactJumpModel.Movement moved = contacts.move(0, Vec3dCore.ZERO, new Vec3dCore(0.8,0.1,0.7), false);
        assertEquals(0.5, moved.position.x, 0);
        assertEquals(0, moved.velocity.x, 0);
        assertEquals(0.7, moved.velocity.z, 0);
        assertTrue(contacts.horizontalCollision);
        ExactJumpModel.Movement next = contacts.move(1, moved.position, new Vec3dCore(0.2,0.1,0.7), false);
        assertEquals(0.7, next.position.x, 0);
        assertEquals(0.2, next.velocity.x, 0);
        assertFalse(contacts.horizontalCollision);
    }

    @Test public void missingOrWrongSideContactCannotGrantAReset() {
        DeclaredContacts contacts = contacts();
        ExactJumpModel.Movement missed = contacts.move(0, Vec3dCore.ZERO, new Vec3dCore(0.2,0,0), false);
        assertEquals(0.2, missed.position.x, 0);
        assertEquals(0.2, missed.velocity.x, 0);
        assertFalse(contacts.violations.isEmpty());
        contacts = contacts();
        ExactJumpModel.Movement wrongSide = contacts.move(0, new Vec3dCore(0.6,0,0), new Vec3dCore(-0.1,0,0), false);
        assertEquals(-0.1, wrongSide.velocity.x, 0);
        assertFalse(contacts.violations.isEmpty());
    }

    @Test public void identityMovementBoundaryPreservesTheExactKernel() {
        JumpPhysicsInputs physics = new JumpPhysicsInputs(4);
        physics.jumpTick = -1;
        physics.initialVelocity = new Vec3dCore(0.004,0.1,-0.2);
        ExactJumpModel model = ExactJumpModel.forMcVersion("1.8.9");
        double[] yaw = {-45.1234,75.1134,299.89,0.00001};
        ForwardPath normal = model.forward(physics,yaw);
        ForwardPath hooked = model.forward(physics,yaw,(tick,pos,vel)->new ExactJumpModel.Movement(pos.add(vel),vel));
        assertArrayEquals(normal.posX,hooked.posX,0);
        assertArrayEquals(normal.posY,hooked.posY,0);
        assertArrayEquals(normal.posZ,hooked.posZ,0);
        assertArrayEquals(normal.velX,hooked.velX,0);
        assertArrayEquals(normal.velY,hooked.velY,0);
        assertArrayEquals(normal.velZ,hooked.velZ,0);
    }
}
