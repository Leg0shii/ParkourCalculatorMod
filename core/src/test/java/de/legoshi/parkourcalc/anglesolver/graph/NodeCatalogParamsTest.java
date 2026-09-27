package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeHelp;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeType;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamSpec;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamValues;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class NodeCatalogParamsTest {

    @Test
    public void everyDeclaredParamIsReadByItsNodeOrTheRunner() {
        List<String> unread = new ArrayList<>();
        for (NodeType type : NodeCatalog.all()) {
            ParamValues values = type.defaultParams();
            type.factory.create(values);
            Set<String> read = values.readKeys();
            for (ParamSpec spec : type.params) {
                if (spec.key.equals(type.budgetParam) || "budgetMs".equals(spec.key)) continue;
                if (!read.contains(spec.key)) unread.add(type.id + "." + spec.key);
            }
        }
        assertTrue("declared params never read by their node: " + unread, unread.isEmpty());
    }

    @Test
    public void everyDeclaredParamHasHelpAndNoOrphanHelpRemains() {
        List<String> missing = new ArrayList<>();
        for (NodeType type : NodeCatalog.all()) {
            for (ParamSpec spec : type.params) {
                String help = NodeHelp.param(type.id, spec.key);
                if (help == null || help.isEmpty()) missing.add(type.id + "." + spec.key);
            }
        }
        assertTrue("declared params without help text: " + missing, missing.isEmpty());
        for (String[] orphan : new String[][] {{"certBnb", "optNodeCap"}, {"ilsPolish", "roundCap"}, {"certBnb", "optSec"}}) {
            String help = NodeHelp.param(orphan[0], orphan[1]);
            assertTrue("orphan help for a param that no longer exists: " + orphan[0] + "." + orphan[1],
                    help == null || help.isEmpty());
        }
    }
}
