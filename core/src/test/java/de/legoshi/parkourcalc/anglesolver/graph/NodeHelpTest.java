package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeHelp;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeType;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamSpec;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class NodeHelpTest {

    @Test
    public void everyNodeHasHelp() {
        for (NodeType t : NodeCatalog.all()) {
            String help = NodeHelp.node(t.id);
            assertNotNull("missing node help for '" + t.id + "'", help);
            assertFalse("blank node help for '" + t.id + "'", help.trim().isEmpty());
        }
    }

    @Test
    public void everyParamHasHelp() {
        for (NodeType t : NodeCatalog.all()) {
            for (ParamSpec spec : t.params) {
                String help = NodeHelp.param(t.id, spec.key);
                assertNotNull("missing param help for '" + t.id + "/" + spec.key + "'", help);
                assertFalse("blank param help for '" + t.id + "/" + spec.key + "'", help.trim().isEmpty());
            }
        }
    }

    @Test
    public void noEmDashesInCopy() {
        for (NodeType t : NodeCatalog.all()) {
            assertFalse("em dash in node help for '" + t.id + "'", NodeHelp.node(t.id).contains("—"));
            for (ParamSpec spec : t.params) {
                String help = NodeHelp.param(t.id, spec.key);
                assertFalse("em dash in param help for '" + t.id + "/" + spec.key + "'",
                        help.contains("—"));
            }
        }
    }
}
