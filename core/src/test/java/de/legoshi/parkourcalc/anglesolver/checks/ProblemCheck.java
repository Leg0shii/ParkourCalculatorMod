package de.legoshi.parkourcalc.anglesolver.checks;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;

/** One capture-driven check: the folder {@code resources/problems/<check>/} names it, {@link Checks} maps the
 *  name to the class, and {@code ProblemsTest} runs it on every capture in the folder. */
public interface ProblemCheck {

    void run(ProblemFixture pf);
}
