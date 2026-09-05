package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.HashMap;
import java.util.Map;

public final class NodeHelp {

    private static final Map<String, String> NODES = new HashMap<>();
    private static final Map<String, String> SHARED_PARAMS = new HashMap<>();
    private static final Map<String, Map<String, String>> NODE_PARAMS = new HashMap<>();

    private NodeHelp() {
    }

    public static String node(String nodeTypeId) {
        return NODES.get(nodeTypeId);
    }

    public static String param(String nodeTypeId, String paramKey) {
        Map<String, String> m = NODE_PARAMS.get(nodeTypeId);
        if (m != null) {
            String s = m.get(paramKey);
            if (s != null) return s;
        }
        return SHARED_PARAMS.get(paramKey);
    }

    private static void node(String id, String help) {
        NODES.put(id, help);
    }

    private static void shared(String key, String help) {
        SHARED_PARAMS.put(key, help);
    }

    private static void param(String nodeId, String key, String help) {
        Map<String, String> m = NODE_PARAMS.get(nodeId);
        if (m == null) {
            m = new HashMap<>();
            NODE_PARAMS.put(nodeId, m);
        }
        m.put(key, help);
    }

    static {
        shared("budgetSec", "How many seconds this stage may run. 0 means no separate limit, so it just"
                + " shares the overall solve time. Higher lets it search longer.");
        shared("budgetMs", "A finer millisecond cap on this stage, used to bail out fast when it is only"
                + " a first attempt. 0 means no millisecond cap, so the second-based limit applies.");
        shared("tickCap", "If the jump takes more than this many ticks, skip this stage entirely."
                + " A guard so very long jumps do not make the solve crawl.");
        shared("labelSuffix", "Optional text added to the solution's name when this stage succeeds, so you"
                + " can spot its work. Cosmetic only.");
        shared("warmSec", "Seconds to keep improving even after the jump already lands. 0 skips this stage"
                + " once the jump works.");
        shared("window", "How many ticks of a multi-jump route the solver tackles at once. Bigger looks"
                + " further ahead but is slower.");
        shared("commit", "How many ticks it locks in per step while building a route. Bigger moves faster"
                + " but leaves less room to undo a bad choice.");
        shared("windowLadder", "Optional list of window sizes to try in order, comma separated. Leave blank"
                + " to let the solver pick.");
        shared("commitLadder", "Optional list of commit sizes to try in order, comma separated. Leave blank"
                + " to let the solver pick.");
        shared("ffSec", "Seconds allowed for the phase that is still trying to make the jump land. 0 turns"
                + " that phase off. Only matters while the jump does not land yet.");
        shared("optSec", "Seconds allowed for the phase that improves a jump that already lands. 0 turns"
                + " that phase off. Only matters once the jump lands.");

        node("entry", "Where the solve begins. It hands the empty attempt to the first real stage."
                + " You cannot delete or configure it.");

        node("emit", "The finish line of the graph. Whatever solution reaches here becomes the result the"
                + " tool shows and plays back. You cannot delete or configure it.");

        node("router", "A fork in the road. It asks one yes or no question about the current attempt and"
                + " sends it down the TRUE or FALSE branch. It never changes the attempt itself, only where"
                + " it goes next.");
        param("router", "predicate", "Which yes or no question this fork asks about the current attempt,"
                + " for example whether a solution exists, whether it already lands, or whether it is a"
                + " single jump. Picks which branch each attempt follows.");
        param("router", "epsilon", "The tolerance for the questions that need one, such as 'violation small"
                + " enough' or 'close enough to best'. Higher is more lenient and sends more attempts down"
                + " TRUE. Range 0 to 1.");
        param("router", "cap", "The tick count used by the 'jump is short enough' question. Higher lets"
                + " longer jumps count as TRUE.");

        node("label", "Tags the current solution with a bit of text so you can tell later which path"
                + " produced it. Purely cosmetic.");
        param("label", "text", "The text tacked onto the solution's name. Leave blank to do nothing.");

        node("capCertify", "Checks whether the current landing is already as good as the jump physically"
                + " allows. If so, it can mark the solve finished so later stages do not waste time.");
        param("capCertify", "computeDualGap", "Also record how far the current landing is from the best"
                + " possible, for the readout. Does not change the solution.");
        param("capCertify", "markSettled", "When the landing is proven best possible, also freeze it so"
                + " later stages skip it.");
        param("capCertify", "skipIfSettled", "If the solution was already frozen as settled, skip this"
                + " check entirely.");

        node("report", "Posts the current progress, how good the jump is and whether it lands, to the live"
                + " readout. It does not change anything, only reports.");

        node("markSettled", "Freezes the current solution as good enough, telling later polish stages to"
                + " leave it alone.");

        node("wrapYaws", "Rewrites the aim angles into their normal -180 to 180 range without changing the"
                + " movement. Tidies up the numbers.");

        node("dualChain", "A from-scratch angle finder. It is the usual first attempt at making a jump"
                + " land, and can also tighten an existing solution.");
        param("dualChain", "keepBetter", "Keep this seed only when it beats the best solution so far, and"
                + " skip the miss note when it does not. Cosmetic.");
        param("dualChain", "slpPhase1Calls", "How hard the built-in angle solver tries just to find any"
                + " landing. Higher digs deeper for a first solution but is slower.");
        param("dualChain", "slpTotalCalls", "Total solver passes, including the ones that refine the"
                + " landing. Higher means more polish but is slower.");
        param("dualChain", "slpTrStartDeg", "How big the solver's first angle steps are, in degrees. Larger"
                + " takes bolder first guesses.");
        param("dualChain", "slpTrMaxDeg", "The largest angle step the solver may take, in degrees. Caps how"
                + " aggressive it gets.");
        param("dualChain", "slpTrMinDeg", "The smallest angle step before the solver calls it done, in"
                + " degrees. Lower means finer final tuning.");
        param("dualChain", "slpLpMaxIter", "Internal iteration limit for each solver step. Raise it only if"
                + " the solver struggles to settle.");
        param("dualChain", "cfMargins", "The ladder of safety margins the quick solver tries, tight to"
                + " loose, comma separated. It works down the list until a landing sticks.");
        param("dualChain", "cfMaxInertiaPasses", "How many refinement passes the quick solver makes. More"
                + " is more thorough but slower.");
        param("dualChain", "cfRungStallLimit", "How many no-progress steps the quick solver tolerates"
                + " before giving up on that path.");

        node("recedingHorizon", "Builds a multi-jump route from scratch by solving a few ticks at a time and"
                + " locking in the front of it as it goes. Only runs for routes longer than one jump.");

        node("setupPeel", "A backup route finder for multi-jumps. It spins the starting aim all the way"
                + " around and, for each promising start, tries to solve the rest of the route, keeping the"
                + " first one that fully works.");
        param("setupPeel", "candidateMs", "Milliseconds spent solving the rest of the route for each"
                + " starting aim it tries. Lower tries more aim angles but each more shallowly.");
        param("setupPeel", "stepDeg", "How far apart, in degrees, the starting aims it sweeps are. Smaller"
                + " checks more angles but is slower, since it makes roughly 360 divided by this many"
                + " attempts.");

        node("facingStep", "For jumps with a fixed-facing run-up before takeoff. It tries small changes to"
                + " the run-up aim, one significant-angle step at a time, seeds each promising aim, then hands"
                + " the best ones to the polish and translate stages in a loop, keeping the best landing.");
        param("facingStep", "windowDeg", "How far, in degrees, the run-up aim may wander either side of its"
                + " current value. 0 tries only the current aim; larger explores more run-up angles.");
        param("facingStep", "maxBuckets", "The most distinct run-up aims to try. Caps the work when the"
                + " window is wide.");

        node("freeStartImprove", "Only works when the start block position is left free. It nudges both the"
                + " aim angles and the exact standing spot together to make the jump land, or land better.");
        param("freeStartImprove", "jointOnly", "Only do the rescue pass that moves the start to make the"
                + " jump land, and skip the extra pass that improves an already-landing jump. Keeps this"
                + " stage focused on rescue.");
        param("freeStartImprove", "fsIntervalMargin", "Safety margin used when pinning the free start,"
                + " 0 to 1. Larger is more cautious about where the start may sit.");
        param("freeStartImprove", "fsInvariantTol", "How much slack to allow in the internal consistency"
                + " check, 0 to 1. Larger accepts looser starts.");
        param("freeStartImprove", "fsJointMargins", "Ladder of safety margins for the rescue pass, tight to"
                + " loose, comma separated, tried in order.");

        node("bnb", "A thorough angle search that rules out dead ends as it goes. When the jump does not"
                + " land yet it hunts for one that does; when it already lands it tries to make it better.");

        node("certBnb", "Like Pattern B&B, but it also proves how close to the best possible landing it got."
                + " When the jump does not land yet it searches for one that does; when it already lands it"
                + " tries to beat it.");
        param("certBnb", "ffNodeCap", "How many search branches the land-it phase may explore. 0 turns that"
                + " phase off; higher searches deeper. Only matters while the jump does not land yet.");
        param("certBnb", "optNodeCap", "How many search branches the improve phase may explore. 0 turns that"
                + " phase off; higher searches deeper. Only matters once the jump lands.");

        node("foldDriver", "Another angle solver that repeatedly replays and adjusts the jump. It can try"
                + " several different starting guesses and finish with a fine nudge pass.");
        param("foldDriver", "objectiveRounds", "How many rounds it spends pushing for a better landing"
                + " rather than just any landing. 0 means find a landing only. Higher optimizes more.");
        param("foldDriver", "multiStart", "How many different start guesses to try, 0 to 5. Only used when"
                + " the start block is free. More casts a wider net.");
        param("foldDriver", "ascentMs", "Milliseconds for a final fine-nudge pass after the main solve."
                + " 0 skips it.");

        node("homotopyLadder", "A rescue trick for jumps around walls. It loosens the walls, finds a"
                + " solution, then tightens them back step by step until the real jump lands. Only runs"
                + " while the jump does not land yet.");
        param("homotopyLadder", "cap", "If the jump takes more than this many ticks, skip this stage."
                + " A guard against very slow runs on long jumps.");

        node("ilsPolish", "Fine-tunes a jump that already lands: it repeatedly shakes up a few angles and"
                + " keeps the change only if the result is better.");
        param("ilsPolish", "roundCap", "Maximum shake-and-test rounds when no time limit is set. 0 turns"
                + " this stage off. With a time budget it simply runs until time is up.");
        param("ilsPolish", "perturbTicksMin", "The fewest ticks a single shake disturbs. Larger makes"
                + " coarser changes.");
        param("ilsPolish", "perturbTicksSpan", "Extra random ticks a shake may disturb on top of the"
                + " minimum. Larger makes the shake width vary more.");
        param("ilsPolish", "perturbMagMin", "The smallest a single shake turns an angle, in degrees.");
        param("ilsPolish", "perturbMagSpan", "Extra random turn a shake may add on top of the minimum, in"
                + " degrees. Larger makes wilder shakes.");

        node("wrapIls", "A polish pass that experiments with big multi-turn spins (wraps) to squeeze out a"
                + " better result. Only kept if it stays landing and beats what you had.");
        param("wrapIls", "minRemainingSec", "Only run this stage if at least this many seconds of solve"
                + " time remain, otherwise skip it. Stops it starting a job it cannot finish.");
        param("wrapIls", "span", "How wide the first spin window it searches is. Larger starts broader.");
        param("wrapIls", "maxSpan", "The widest the search window is allowed to grow to.");
        param("wrapIls", "candHighTarget", "How many promising high-spin candidates it keeps each round."
                + " More widens the search.");
        param("wrapIls", "kicks", "Allow random shake-ups to escape a rut. On by default.");
        param("wrapIls", "evalCap", "Cap on how many test replays it may run. 0 means no cap.");
        param("wrapIls", "roundCap", "Cap on how many rounds it may run. 0 means no cap.");
        param("wrapIls", "gateFlipMoves", "Restrict moves that flip a turn's direction. Off by default;"
                + " a niche control.");
        param("wrapIls", "maxAbsGf", "The largest total spin, in degrees, the search may use. Caps how many"
                + " full turns it will pile on.");

        node("leafSnap", "Snaps each aim angle onto the nearest exact value the game can actually use,"
                + " keeping the snap only if the jump still lands.");
        param("leafSnap", "pairPass", "Turn on a second, more thorough snapping pass that adjusts angles in"
                + " pairs. 0 off, 1 on. Slower but can land a cleaner result.");

        node("translatedStart", "Only with a free start block: slides the whole solution's start spot to the"
                + " best position that still lands the jump.");
    }
}
