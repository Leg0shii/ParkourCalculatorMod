# Timings, Momentum, and Glitches

Mirrored from the Minecraft Parkour Wiki (mcpk.wiki); captured in-repo because the wiki is Cloudflare-gated. Reference, not implementation. Source pages: Timings, Tapping, Backward Momentum, Anvil/Chest Manipulation, Ceiling Hover.

## Timings

Timing is at the core of parkour: understanding how ticks work and how to use them for gaining momentum is crucial. Basic timings are elementary bricks for advanced strategies like Backward Momentum.

**Randomness:** Because Minecraft's physics is tick-based, there is inevitable luck in timing. Timing inputs perfectly (intervals of exactly 50ms) reduces but never fully negates randomness. Use timings that grant the most consistent results. Example: a simple timing for a 1bm headhitter jump is the hh timing, which has only a 1 tick opening and no failsafe. The pessi hh timing has a more lenient opening and offers recovery time.

**Notations:** t stands for tick. W, A, S, D refer to movement keys (W=Forward, A=Left, S=Backward, D=Right). Unless mentioned otherwise, the player is always sprinting.

### Basic Timings

| Name (and aliases) | Description | Notes |
| --- | --- | --- |
| 0t (jam) | Hold W, Jump on the same tick. | The most basic timing, used to initiate momentum. |
| 1t (hh) | Hold W, Jump one tick later. | hh refers to the most common use, headhitter jumps. Can be used when no momentum is given. |
| 2t | Hold W, Jump two ticks later. | Used on extremely short momentums, such as a backwalled ladder. |
| Jump 0t (jump jam) | Hold Jump (double tap if low ceiling). Press and hold W when landing. | Gives the same jump distance as a regular jam timing. |
| Jump 1t (jump hh, a7hh) | Hold Jump (double tap if low ceiling). Press and hold W 1 tick before landing. | Used on X-facing neos with very little momentum. With no ceiling, W is pressed 11 ticks after jumping. |
| -1t (fast pessi hh) | Hold Jump. Press and hold W a tick later. | Notably used for 1bm hh, can be used on short momentums. Could be called "Jump 11t" but that is not a good notation. |
| -2t (slow pessi hh) | Hold Jump. Press and hold W two ticks later. | -1t, -2t, and -3t will all clear a 1bm hh if performed from the back looking straight. Very consistent for headhitter jumps. |
| Burst 0t (burst jam, adv jam) | Hold Sneak and W. Release Sneak and press Jump on the same tick. | Sneaking on an edge does not reset the player's speed. Jump distance increases with how long you sneak forward (converges very quickly). Generally you want a burst 1t instead. |
| Burst 1t (burst hh, adv hh) | Hold Sneak and W. Release Sneak, press Jump one tick later. | Used on the edge of a block when no momentum is given. You gain ~41% more momentum when sneaking with 45 strafe. With this and 45, a no-momentum 3+1 becomes possible. |
| 1t run | When landing, run for 1 tick before jumping again. | Used for specific jumps where it's better than jumping immediately, or when the momentum doesn't allow jumping immediately. |

Note: "semi-hh" refers to the use of jam or jump jam in cases where accidentally performing them one tick late (hh or jump hh) is not detrimental.

### Essential Timings

| Name (Aliases) | Description | Notes |
| --- | --- | --- |
| Force Momentum (fmm) | Perform a jam or jump jam without sprinting. Press Sprint 1 tick later, keep holding Jump. | Used as a simple 1bm strat starting from the back. Can easily do jumps like 4b, very consistent. |
| Carpet 4.5 (c4.5) | Perform a jam or jump jam without sprinting. Press Sprint 4 ticks later, do a 1t run before jumping again. | More complicated than force momentum but goes further. Name refers to its original use: 1bm 4+0.4375. Depending on momentum, press Sprint sooner or later. |

Examples: 1bm 3+1 is possible with only running from the back (7t timing). 1bm 4b is possible with only running from the back (7t timing) if you first do a tap (preferably 4). Backwalled 1bm 4b is possible with only running from the back (5t timing) if you first do a tap (preferably 3). Performing a jam on 2bm overjumps the momentum slightly; to correct, start jumping at a ~10 degree angle, or sneak backward, release S for 3 ticks, then release sneak and jam forward. When gaining momentum under a 2-block ceiling (headhitter momentum) the ideal is to press jump every 3 ticks; with a trapdoor added (1.8125bc) the ideal is every 2 ticks (10 times per second).

## Tapping

Tapping involves pressing keys for very short amounts of time (usually 1 tick). It covers short distances, useful to set the player at an optimal position. The most common taps are the Shift tap, the Walk tap, and the Air Shift tap. The Sprint tap is mostly unused.

- **Shift tap:** While sneaking (shifting), press the forward key for 1 tick.
- **Walk tap:** Press the forward key for 1 tick.
- **Sprint tap:** While sprinting, press the forward key for 1 tick.
- **Air shift tap:** Jump while shifting, and press the forward key for 1 tick while mid-air.

**Applications:** A backwalled 1bm 4b requires 1-3 taps from the back to be possible with only running momentum. An easy setup for 3bc 1bm backward momentum is to Walk tap then Shift tap from the front. A good strat for 3bm jumps is to perform 2 Shift taps from the back and run for 2 ticks before jumping.

**Analysis:** When the forward key is released, the player continues moving for a short time, then stops due to Momentum Threshold: their X and Z speed decrease until their absolute value is smaller than 0.005, at which point they are set to 0. In 1.9+ the momentum threshold was lowered to 0.003 (down from 0.005), which makes all common taps longer by 1 tick, except the air shift tap which is extended by a whole 6 ticks. So tap strats are radically different in 1.9+.

**Nomenclature:** abbreviate the 4 taps: S=Sprint tap, W=Walk tap, s=Shift tap, a=Air shift tap. Syntax: [Forwards taps]-[Backwards taps]. Example: "1 walk tap backwards, 1 shift tap, 2 air shift tap" becomes "s2a-W". Order does not matter as long as you don't do them in too quick succession.

## Backward Momentum

Backward Momentum (bwmm) maximizes the potential of a given momentum. The principle: maximize the player's forward speed at the end of the provided momentum, achieved by walking off the back of the momentum then jumping forward. On some setups, repeated executions (alternating back and forth) may be needed. It's possible to calculate an upper bound for the jump distance by maximizing the initial speed such that the player doesn't overjump the momentum. When a bwmm strat involves turning back and forth it is called Loop Momentum. For real-time parkour, no-turn strats are preferred for consistency.

**Considerations:** whether to "delay" the final jump (running 1 tick off the edge instead of jumping immediately). Delaying conserves less speed but allows a small headstart. On short momentums (less than 0.4375bm), delaying is always better. On longer momentums, the choice depends on the height of the jump. Momentum Threshold is also a consideration; it can be helpful or detrimental depending on how velocity is affected.

**Common 1bm Strats:** bwmm was originally created to solve the 1bm 4.375b jump (first with 3bc momentum, then with no ceiling). The most well-known 1bm strats were force momentum and c4.5 timing, but their efficiencies pale compared to bwmm strats (though force momentum and c4.5 are still useful and easier to set up). Common bwmm strats for 1bm: 3bc 1bm strat, Rex bwmm, Cyn bwmm, 1bm 5-1 strat.

## Anvil/Chest Manipulation

Anvils don't have a fixed collision box: there is a single collision box for all variants (North, East, South, West), updated whenever the player looks at one (or by other actions). Normal, slightly damaged, and very damaged anvils are not independent. Chests behave the same, though only Double Chests have a different collision box; Chests and Trapped Chests are independent. By standing next to an anvil/chest and looking at another variant, the player can update the collision box so they are considered "inside the block", or artificially extend the length to stand one or two pixels further than intended. Do not look back at the original block, as that resets the collision box.

**Collision boxes:** Anvils are a simple 1 x 0.75 bounding box (16 x 12 pixels), oriented along X or Z. Single chests are a simple 0.875 x 0.875 bounding box (14 x 14 pixels); the double chest variant extends one side by 1 pixel. Darker colors in the graphic indicate collision areas common to all variants (stand there to avoid falling during manipulation); lighter colors indicate areas specific to one variant (stand there to clip inside the block).

```java
/* In BlockAnvil.java */
public void setBlockBoundsBasedOnState(BlockPos pos)
{
    EnumFacing enumfacing = getBlockState(pos).getValue(FACING);
    if (enumfacing.getAxis() == EnumFacing.Axis.X)
        this.setBlockBounds(0.0, 0.0, 0.125, 1.0, 1.0, 0.875);
    else
        this.setBlockBounds(0.125, 0.0, 0.0, 0.875, 1.0, 1.0);
}
/* In BlockChest.java */
public void setBlockBoundsBasedOnState(BlockPos pos)
{
    if (getBlockState(pos.north()).getBlock() == this)
        this.setBlockBounds(0.0625, 0.0, 0.0, 0.9375, 0.875, 0.9375);
    else if (getBlockState(pos.south()).getBlock() == this)
        this.setBlockBounds(0.0625, 0.0, 0.0625, 0.9375, 0.875, 1.0);
    else if (getBlockState(pos.west()).getBlock() == this)
        this.setBlockBounds(0.0, 0.0, 0.0625, 0.9375, 0.875, 0.9375);
    else if (getBlockState(pos.east()).getBlock() == this)
        this.setBlockBounds(0.0625, 0.0, 0.0625, 1.0, 0.875, 0.9375);
    else //single chest
        this.setBlockBounds(0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375);
}
```

**Manipulation actions:** simply looking at the block; shooting an arrow at it (persistent effect); causing it to re-render by updating surroundings; using rain to update it (randomly).

**Limitations:** On Singleplayer the player can usually walk through an anvil or chest once clipped in. On Multiplayer the anticheat typically prevents walking through completely. In 1.9 this mechanic was patched (each variant now has its own collision box).

## Ceiling Hover

In 1.8 the only setup for a Ceiling Hover is 1.8125bc with slime underneath. Ceiling Hover makes the player "hover" between a ceiling and a bouncy block (slime, or beds since 1.12). It is performed by jumping under a ceiling such that the block 2.001b below the ceiling has bouncing properties.

**Explanation:** when the player collides vertically with a block (floor or ceiling), the game applies collision physics. For almost every block that just means setting vertical speed to 0:

```java
/* in class Block */
public void onVerticalCollision(Entity entityIn)
{
    entityIn.motionY = 0.0D;
}
```

Exception: Slime Blocks (and Beds in 1.12+):

```java
/* in class BlockSlime */
public void onVerticalCollision(Entity entityIn)
{
    if (entityIn.isSneaking())
        super.onVerticalCollision(entityIn);
    else if (entityIn.motionY < 0.0D)
        entityIn.motionY = -entityIn.motionY;
}
```

If the player is sneaking it's a regular collision (vertical motion set to 0). Otherwise it checks if speed is negative, then inverts it. Nothing happens if the player is moving at a positive speed and not sneaking, which is exactly what this glitch uses. When the game detects a vertical collision, it considers the block 0.2m under the player's position to apply collision physics (even for ceiling collisions). Steps for the 1.8125bc Ceiling Hover: (1) jumping under 1.8125bc applies vertical collision with the block 0.2m below the player (at ground level); (2) if that block is a slime block, vertical speed won't be set to 0 and the player remains suspended under the ceiling; (3) repeat until the player's vertical speed becomes negative due to gravity. With Jump Boost it takes longer. Ceiling hover can be interrupted at any time by sneaking. This glitch is insignificant and requires specific setups.
