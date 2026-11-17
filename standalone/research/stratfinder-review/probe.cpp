#include "inputCracker.hpp"
#include "util.hpp"
#include <chrono>
#include <iomanip>
#include <iostream>
int main() {
    util::init();
    for (bool risky : {false, true}) {
        inputCracker cracker;
        cracker.changeSettings(2, 50, -1, false, 1024);
        cracker.riskyPrune(risky);
        cracker.setEffect(0, 1);
        cracker.setRotation(0.015);
        inputCracker::condition cond;
        cond.endAirborne = false;
        cond.z.enabled = true;
        cond.z.mm = -1.5;
        inputCracker::setCondWithBound(cond.z, -0.1276844242999637, -0.1276846279184921);
        auto start = std::chrono::steady_clock::now();
        auto solutions = cracker.matchSpeed(cond, 12);
        auto stats = cracker.getSearchStats();
        std::cout << "risky=" << risky << " solutions=" << solutions.size()
                  << " dfs=" << stats.inputDfsRecCalls << " forwards=" << stats.exeSeqCalls
                  << " boundPrunes=" << stats.minBoundPrunes + stats.maxBoundPrunes
                  << " seconds=" << std::chrono::duration<double>(std::chrono::steady_clock::now()-start).count() << "\n";
        for (const auto& solution : solutions)
            std::cout << solution.mothball << " vz=" << std::setprecision(17) << solution.vz << "\n";
    }
}
