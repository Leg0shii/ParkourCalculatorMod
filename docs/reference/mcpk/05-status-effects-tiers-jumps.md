# Status Effects, Tiers, and Longest Jumps

Mirrored from the Minecraft Parkour Wiki (mcpk.wiki); captured in-repo because the wiki is Cloudflare-gated. Reference, not implementation. Source pages: Status Effects, Tiers, Longest Jumps.

## Status Effects

The 3 main status effects relevant to parkour. Applied from most potions, some beacon effects, or the /effect command. Effect command: /effect <player> <effect> <Duration> <Level-1>.

### Speed

Speed increases the player's ground speed, making them move 20% faster per level. Speed does not affect airborne movement, so sprint-jumping becomes less efficient the higher the level (for Speed II, generally slower to sprint-jump than to run).

| Speed Level | Max Walking Speed m/t | Max Sprinting Speed m/t |
| --- | --- | --- |
| None | 0.215 | 0.280 |
| I | 0.259 | 0.336 |
| II | 0.302 | 0.392 |
| III | 0.345 | 0.448 |
| IV | 0.388 | 0.505 |

Speed presented in meters per tick (m/t); multiply by 20 for m/s. For max ground speed with 45 strafe, divide by 0.98.

### Slowness

Slowness slows the player's ground speed, 15% slower per level. Does not change airborne movement, so sprint-jumping is more efficient.

| Slowness Level | Max Walking Speed m/t | Max Sprinting Speed m/t |
| --- | --- | --- |
| None | 0.215 | 0.280 |
| I | 0.183 | 0.238 |
| II | 0.151 | 0.196 |
| III | 0.118 | 0.154 |
| IV | 0.086 | 0.112 |
| V | 0.053 | 0.070 |
| VI | 0.021 | 0.028 |
| VII | 0.000 | 0.000 |

Multiply by 20 for m/s; divide by 0.98 for max ground speed with 45 strafe. Past level 7 (VII), Slowness completely stops the player.

### Speed and Slowness Interaction

Speed and Slowness multipliers multiply, not add. Example: Speed III (+60%) and Slowness IV (-60%) gives not +0% but -34%.

### Jump Boost

Jumping sets the player's vertical speed to 0.42 by default. Jump boost increases this initial vertical boost by 0.1 per level.

| Jump Boost Level | Max Height m | Duration t on flat ground |
| --- | --- | --- |
| None | 1.249 | 12 |
| I | 1.836 | 14 |
| II | 2.516 | 16 |
| III | 3.290 | 19 |
| IV | 4.153 | 21 |
| V | 5.103 | 23 |

Notes: In 1.9+ the default jump height is 1.252m, but that does not change jump height with jump boost in general. Past level 129, Jump Boost becomes negative (player unable to jump unless using negative speed to bounce on a slime block). Levels 252-256 the player can jump again with reduced height; level 256 has the same jump height as default.

## Tiers

Because Minecraft's physics is tick-based, the player's movement is not updated continuously. Some jumps are identical in difficulty despite different heights. For example +0.5 and +0.75 jumps of the same length have equal difficulty, because the player lands at Y=0.7532 either way. The range of heights matching the same difficulty corresponds to a Tier.

By convention: Tier 0 contains the default height (+0.0); positive Tiers correspond to positive heights; negative Tiers to negative heights.

On flat ground (Tier 0) a jump lasts 12 ticks. Convert duration to tiers: Tier = 12 - Duration.

### Jump Heights per Duration

The reason the height peak is higher in 1.9+ is because the momentum threshold was lowered to 0.003 and no longer affects jumping.

| Duration (ticks) | Height (1.8) | Height (1.9+) | Maximum Tier | Tier Updates |
| --- | --- | --- | --- | --- |
| 1 | 0.0 | 0.0 | - | |
| 2 | 0.4200 | 0.4200 | - | |
| 3 | 0.7532 | 0.7532 | - | |
| 4 | 1.0013 | 1.0013 | - | |
| 5 | 1.1661 | 1.1661 | - | |
| 6 | 1.2492 | 1.2492 | - | |
| 7 | 1.2492 | 1.2522 | 5 | 1.25 |
| 8 | 1.1708 | 1.1768 | 4 | |
| 9 | 1.0156 | 1.0244 | 3 | |
| 10 | 0.7850 | 0.7967 | 2 | |
| 11 | 0.4807 | 0.4952 | 1 | |
| 12 | 0.1041 | 0.1213 | 0 | |
| 13 | -0.3434 | -0.3235 | -1 | |
| 14 | -0.8604 | -0.8379 | -2 | |
| 15 | -1.4454 | -1.4203 | -3 | -1.4375 |
| 16 | -2.0971 | -2.0695 | -4 | |
| 17 | -2.8142 | -2.7841 | -5 | -2.8125 |
| 18 | -3.5953 | -3.5628 | -6 | |
| 19 | -4.4392 | -4.4044 | -7 | -4.4375 |
| 20 | -5.3446 | -5.3075 | -8 | -5.3125 |
| 21 | -6.3104 | -6.2709 | -9 | |
| 22 | -7.3352 | -7.2935 | -10 | -7.3125 |
| 23 | -8.4179 | -8.3740 | -11 | -8.375 |
| 24 | -9.5573 | -9.5114 | -12 | |
| 25 | -10.7524 | -10.7043 | -13 | -10.75 |
| 26 | -12.0020 | -11.9518 | -14 | -12.0 |
| 27 | -13.3050 | -13.2528 | -15 | |
| 28 | -14.6603 | -14.6062 | -16 | -14.625 |
| 29 | -16.0669 | -16.0108 | -17 | -16.0625 |
| 30 | -17.5238 | -17.4658 | -18 | -17.5 |
| 31 | -19.0299 | -18.9701 | -19 | -19.0 |
| 32 | -20.5843 | -20.5227 | -20 | -20.5625 |
| 33 | -22.1861 | -22.1226 | -21 | -22.125 |
| 34 | -23.8341 | -23.7690 | -22 | -23.8125 |
| 35 | -25.5277 | -25.4608 | -23 | -25.5 |
| 36 | -27.2657 | -27.1972 | -24 | -27.25 |
| 37 | -29.0474 | -28.9772 | -25 | -29.0 |
| 38 | -30.8719 | -30.8001 | -26 | -30.8125 |
| 39 | -32.7383 | -32.6649 | -27 | -32.6875 |
| 40 | -34.6457 | -34.5708 | -28 | -34.625 |
| 41 | -36.5934 | -36.5170 | -29 | -36.5625 |
| 42 | -38.5806 | -38.5026 | -30 | -38.5625 |
| 43 | -40.6064 | -40.5270 | -31 | -40.5625 |
| 44 | -42.6701 | -42.5893 | -32 | -42.625 |
| 45 | -44.7709 | -44.6887 | -33 | -44.75 |
| 46 | -46.9081 | -46.8245 | -34 | -46.875 |
| 47 | -49.0810 | -48.9960 | -35 | -49.0625, -49.0 |
| 48 | -51.2888 | -51.2025 | -36 | -51.25 |
| 49 | -53.5308 | -53.4432 | -37 | -53.5 |
| 50 | -55.8064 | -55.7176 | -38 | -55.75 |
| 51 | -58.1149 | -58.0248 | -39 | -58.0625 |
| 52 | -60.4556 | -60.3643 | -40 | -60.4375, -60.375 |
| 53 | -62.8279 | -62.7354 | -41 | -62.8125, -62.75 |
| 54 | -65.2312 | -65.1375 | -42 | -65.1875 |
| 55 | -67.6648 | -67.5700 | -43 | -67.625 |
| 56 | -70.1281 | -70.0322 | -44 | -70.125, -70.0625 |
| 57 | -72.6205 | -72.5235 | -45 | -72.5625 |
| 58 | -75.1416 | -75.0435 | -46 | -75.125, -75.0625 |
| 59 | -77.6905 | -77.5914 | -47 | -77.6875, -77.625 |
| 60 | -80.2670 | -80.1668 | -48 | -80.25, -80.1875 |
| 61 | -82.8702 | -82.7690 | -49 | -82.8125 |
| 62 | -85.4998 | -85.3977 | -50 | -85.4375 |
| 63 | -88.1553 | -88.0521 | -51 | -88.125, -88.0625 |
| 64 | -90.8360 | -90.7319 | -52 | -90.8125, -90.75 |
| 65 | -93.5415 | -93.4364 | -53 | -93.5, -93.4375 |
| 66 | -96.2713 | -96.1653 | -54 | -96.25, -96.1875 |
| 67 | -99.0249 | -98.9180 | -55 | -99.0, -98.9375 |
| 68 | -101.8018 | -101.6940 | -56 | -101.75 |
| 69 | -104.6016 | -104.4929 | -57 | -104.5625, -104.5 |
| 70 | -107.4237 | -107.3143 | -58 | -107.375 |
| 71 | -110.2679 | -110.1576 | -59 | -110.25, -110.1875 |
| 72 | -113.1336 | -113.0224 | -60 | -113.125, -113.0625 |
| 73 | -116.0203 | -115.9084 | -61 | -116.0, -115.9375 |
| 74 | -118.9277 | -118.8150 | -62 | -118.875 |
| 75 | -121.8554 | -121.7419 | -63 | -121.8125, -121.75 |
| 76 | -124.8029 | -124.6887 | -64 | -124.75 |
| 77 | -127.7698 | -127.6549 | -65 | -127.75, -127.6875 |
| 78 | -130.7559 | -130.6402 | -66 | -130.75, -130.6875 |
| 79 | -133.7606 | -133.6442 | -67 | -133.75, -133.6875 |
| 80 | -136.7836 | -136.6665 | -68 | -136.75, -136.6875 |
| 81 | -139.8245 | -139.7068 | -69 | -139.8125, -139.75 |
| 82 | -142.8831 | -142.7647 | -70 | -142.875, -142.8125 |
| 83 | -145.9588 | -145.8398 | -71 | -145.9375, -145.875 |
| 84 | -149.0515 | -148.9318 | -72 | -149.0, -148.9375 |
| 85 | -152.1606 | -152.0403 | -73 | -152.125, -152.0625 |
| 86 | -155.2861 | -155.1651 | -74 | -155.25, -155.1875 |
| 87 | -158.4273 | -158.3058 | -75 | -158.375, -158.3125 |
| 88 | -161.5842 | -161.4621 | -76 | -161.5625, -161.5 |
| 89 | -164.7564 | -164.6337 | -77 | -164.75, -164.6875 |
| 90 | -167.9434 | -167.8202 | -78 | -167.9375, -167.875 |
| 91 | -171.1452 | -171.0214 | -79 | -171.125, -171.0625 |
| 92 | -174.3613 | -174.2370 | -80 | -174.3125, -174.25 |
| 93 | -177.5915 | -177.4666 | -81 | -177.5625, -177.5 |
| 94 | -180.8355 | -180.7101 | -82 | -180.8125, -180.75 |
| 95 | -184.0930 | -183.9671 | -83 | -184.0625, -184.0 |
| 96 | -187.3638 | -187.2374 | -84 | -187.3125, -187.25 |
| 97 | -190.6475 | -190.5206 | -85 | -190.625, -190.5625 |
| 98 | -193.9440 | -193.8166 | -86 | -193.9375, -193.875 |
| 99 | -197.2529 | -197.1251 | -87 | -197.25, -197.1875 |
| 100 | -200.5741 | -200.4458 | -88 | -200.5625, -200.5 |
| 101 | -203.9072 | -203.7784 | -89 | -203.875, -203.8125 |
| 102 | -207.2521 | -207.1229 | -90 | -207.25, -207.1875, -207.125 |
| 103 | -210.6085 | -210.4788 | -91 | -210.5625, -210.5 |
| 104 | -213.9761 | -213.8460 | -92 | -213.9375, -213.875 |
| 105 | -217.3548 | -217.2243 | -93 | -217.3125, -217.25 |
| 106 | -220.7443 | -220.6134 | -94 | -220.6875, -220.625 |
| 107 | -224.1445 | -224.0132 | -95 | -224.125, -224.0625 |
| 108 | -227.5550 | -227.4233 | -96 | -227.5, -227.4375 |
| 109 | -230.9757 | -230.8436 | -97 | -230.9375, -230.875 |
| 110 | -234.4064 | -234.2740 | -98 | -234.375, -234.3125 |
| 111 | -237.8469 | -237.7141 | -99 | -237.8125, -237.75 |
| 112 | -241.2970 | -241.1638 | -100 | -241.25, -241.1875 |
| 113 | -244.7565 | -244.6229 | -101 | -244.75, -244.6875, -244.625 |
| 114 | -248.2252 | -248.0913 | -102 | -248.1875, -248.125 |
| 115 | -251.7029 | -251.5686 | -103 | -251.6875, -251.625 |
| 116 | -255.1895 | -255.0549 | -104 | -255.1875, -255.125, -255.0625 |

## Longest Jumps

The longest jump configurations for each tier. Momentum is assumed to use flat ground, 45 strafe, and an optimal half angle.

Formula to convert a jump's distance from block form to meters:

```
Dist(dx, dz) = sqrt( max(0, dx - 0.6)^2 + max(0, dz - 0.6)^2 )
```

Reminder: 1 pixel = 1/16 block = 0.0625b. Example: a configuration of (X:64 Z:32) maps to a 4x2 gap.

Configurations are given in pixels, with their margin (between their distance and the max jump distance for that tier). Often the true margin is a bit smaller because half angles are less efficient for diagonal jumps.

### Longest Jump per Tier with Flat Momentum

| Tier | Height Range | Max Distance | Longest Jump (px) | Margin | Verification |
| --- | --- | --- | --- | --- | --- |
| 5 | +1.171 to +1.249 | 2.6861854m | 51 x 21 | 0.002380 | |
| 4 | +1.016 to +1.170 | 3.0210507m | 57 x 19 | 0.000858 | |
| 3 | +0.786 to +1.015 | 3.3517794m | 62 x 21 | 0.000171 | 3.875 x 1.3125 + 1 |
| 2 | +0.481 to +0.785 | 3.6787438m | 60 x 40 | 0.000089 | 3.75 x 2.5 + 0.75 |
| 1 | +0.105 to +0.480 | 4.0022827m | 60 x 49 | 0.003982 | |
| 0 | -0.343 to +0.104 | 4.3227043m | 65 x 51 | 0.000198 | 4.0625 x 3.1875 |
| -1 | -0.860 to -0.344 | 4.6402892m | 82 x 26 | 0.000650 | |
| -2 | -1.445 to -0.861 | 4.9552927m | 81 x 44 | 0.001869 | |
| -3 | -2.097 to -1.446 | 5.2679471m | 83 x 51 | 0.001040 | |
| -4 | -2.814 to -2.098 | 5.5784640m | 87 x 54 | 0.001544 | |
| -5 | -3.595 to -2.815 | 5.8870355m | 100 x 36 | 0.001035 | |
| -104 | at most -255.190 | 34.6872685m | 550 x 136 | 0.000663 | 34.375 x 8.5 - 255.25 |

Notes: Tier -104 is debatable because there are 4 longer jumps technically within the margin but unbuildable (too diagonal, half angles less effective). More jumps can be built if walled jumps are considered, some longer than those listed.
