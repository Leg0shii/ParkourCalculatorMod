# Collisions and Stepping

Mirrored from the Minecraft Parkour Wiki (mcpk.wiki); captured in-repo because the wiki is Cloudflare-gated. Reference, not implementation. Source pages: Collisions, Stepping.

## Collisions

Minecraft's collision physics is very simplistic: instead of ray-tracing collisions, the game moves the player sequentially along each axis. Physics is updated 20 ticks per second; movement and collisions are updated once per tick.

### Collision Box

A collision box consists of one or multiple bounding boxes, which are cuboids defined by minimum and maximum X/Y/Z coordinates. Collisions usually involve an Entity and a Block; entities and blocks typically don't collide among themselves. The player has only one bounding box, 0.6 x 1.8 x 0.6 m^3; their position (as shown in F3) is at the bottom center. Blocks have more complicated collision boxes.

Note: a "hitbox" is what the player can click on (attack an entity, press a button, open a door); it may or may not overlap with the collision box.

### Collision Order

Every tick, after the player's velocity has been updated, the game does these steps to check collisions:

1. Move the player along the Y axis. If a vertical collision is detected while moving downward, the player is now considered to be on ground.
2. Move the player along the X axis.
3. Move the player along the Z axis.
4. If the player is on ground and collided with a wall, they are able to step over it if it's less than 0.6m tall (see Stepping). This mechanic is responsible for Blips and Jump-Cancel, and their glitched variants.

### Vertical Collisions (Y)

Vertical movement is processed before horizontal movement. Due to this: the player is able to jump one tick after running off a block (this is why headhitter timing works). To land on a block, the player's bounding box must overlap its surface on the final tick of the jump. When the player hits a floor or ceiling, their vertical speed is reset to 0.

### Horizontal Collisions (X/Z)

The X axis is processed before the Z axis, so corner collisions don't behave the same depending on direction; this is especially noticeable with more speed. Distinguish "X-facing" jumps from "Z-facing":

- X-facing: jumps pointing towards East/West. The corner is difficult to avoid, and the player may have to start jumping further back than expected.
- Z-facing: jumps pointing towards North/South. The corner is easier to avoid, and it's possible to do a hh-timing from the front.

The axis of a jump can be checked with F3. Players tend to find Z-facing neos more intuitive, but X-facing neos can be more lenient:

- Z-facing neos are very similar to linear jumps (assuming optimal movement). To convert a neo, add 1.2 to its distance and increase its tier by one. For example a triple neo is equivalent to a "4.2+0.25" (which cannot be built, but is useful for analysis).
- X-facing neos don't have a linear equivalent. Compared to Z-facing neos they are "shifted" by 1 tick (wall collision begins and ends 1 tick earlier). This shift reduces the momentum but makes it more efficient, as the player typically has more speed at the end of a jump than at the start.

Some jumps are only possible facing one axis (for example, a 2bm triple neo is only possible X-facing).

### 1.14+

Collision physics were updated. The collision order now depends on the player's velocity: if the player has more Z speed than X speed (in absolute value), the order is Y-X-Z; otherwise it is Y-Z-X. In most cases all collisions now resemble X-facing. Some jumps involving cutting corners may be very different compared to pre-1.14.

## Stepping

Stepping, or Step-Assist, is a mechanic that assists the player in walking up obstacles of low height without jumping. The player's maximum step height is 0.6b, so they can step up blocks like carpets, slabs, and even beds. While simple in concept, the implementation is messy and introduces a wide range of collision-related glitches abused in parkour (Blips and Jump-Cancelling).

### Summary

The player's bounding box (0.6 x 1.8 x 0.6) is used to detect collisions (collision order is Y-X-Z). When a wall is detected while the player is on ground, the game attempts to make them "hop" over it. In the end, the game chooses the method that yields the longest horizontal distance.

### Notes

Prior to 1.8, the player couldn't step onto some blocks which had a ceiling above; to fix that, an alternative method was added to the stepping procedure. Prior to 1.8.1, stepping could be used to glitch into the floor, because the floor below the player's bounding box is never considered for collisions during step-assist, but the procedure could force the player below floor level anyway (the bounding box was lowered by 0.6b regardless of the amount it was elevated by); easier in 1.8.0 as you don't need a ceiling directly overhead. The current implementation of stepping is still somewhat flawed.

### Related Glitches

Stepping is intended for ground movement and works fine for walking up slabs and stairs. However, the game also tries to apply stepping at the start and end of any vertical motion, causing unintended mechanics:

- Blipping can happen at the end of a jump, used to jump higher by landing above ground level.
- Jump-Cancelling can happen at the start of a jump, used to gain momentum by staying at ground level.
