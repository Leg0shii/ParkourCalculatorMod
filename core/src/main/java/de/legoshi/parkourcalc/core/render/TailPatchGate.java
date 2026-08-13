package de.legoshi.parkourcalc.core.render;

public final class TailPatchGate {

    private TailPatchGate() {
    }

    public static boolean canPatch(boolean built, boolean structuralHashSame, boolean boxCountSame,
                                   int hitboxEdges, boolean useSubtick, PathRenderPlan plan,
                                   int bakedConstraintFaceVerts, int bakedConstraintLineVerts) {
        return built && structuralHashSame && boxCountSame
                && hitboxEdges == plan.patch.hitboxEdges()
                && !useSubtick
                && plan.reachLineVerts == 0
                && plan.constraintFaceVerts == bakedConstraintFaceVerts
                && plan.constraintLineVerts == bakedConstraintLineVerts;
    }
}
