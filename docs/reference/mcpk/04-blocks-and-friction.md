# Blocks and Friction (mcpk.wiki reference)

Mirrored from the Minecraft Parkour Wiki (mcpk.wiki); captured in-repo because the wiki is Cloudflare-gated. Reference, not implementation. Source pages: Blocks, Slipperiness, Soulsand, Cobweb, Ladders and Vines. All block data is for Minecraft 1.8.

## Blocks

A Block is a basic unit of structure occupying space in the world. A Collision Box is a solid volume of space the player is not meant to pass through, consisting of one or multiple Bounding Boxes (axis-aligned cuboids). Entities have their own bounding box but are not treated as solid space (except boats). Not to be confused with a Hitbox (a volume the player can interact with: attack, mine, right-click) or a Model (graphical representation). Some blocks have their own "effect box" (fluids, ladders, cacti, pressure plates) coded and applied differently. Example: a button has no collision box (player can walk through) but has a hitbox. A barrier block has overlapping bounding box and hitbox but is hard-coded to have no model. This lists collision boxes and properties of all blocks in 1.8.

### Simple Collision Boxes (single bounding box)

| Block | Widths (b) | Height (b) | Comments |
| --- | --- | --- | --- |
| Wall (4-sided) | 1 x 1 | 1.5 | Adjacent blocks must be solid, walls, or fencegates. |
| Default | 1 x 1 | 1 | |
| Soulsand | 1 x 1 | 0.875 | Looks like a full block but is 2px lower. Slows entities walking on top. |
| End Portal Frame | 1 x 1 | 0.8125 | |
| Enchantment Table | 1 x 1 | 0.75 | |
| Bed block | 1 x 1 | 0.5625 | The bottom doesn't look tangible but it is. |
| Slab | 1 x 1 | 0.5 | Inversible. |
| Daylight Sensor | 1 x 1 | 0.375 | |
| Trapdoor (horizontal) | 1 x 1 | 0.1875 | Inversible. Can be flipped to its vertical variant. |
| Repeater | 1 x 1 | 0.125 | |
| Carpet | 1 x 1 | 0.0625 | |
| Lily Pad | 1 x 1 | 0.015625 | Is 1/4 of a pixel in height. |
| Snow Layer | 1 x 1 | 0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875 | Snow layers look 2px higher than their real height. Despite having no height, a single layer of snow is tangible. |
| Wall (3-sided) | 1 x 0.75 | 1.5 | Orientable (4 variants). Adjacent blocks must be solid, walls, or fencegates. |
| Anvil | 1 x 0.75 | 1 | Centered. Orientable (2 variants). Looks thinner than it is. |
| Piston Base (Powered) | 1 x 0.75 | 1 | Orientable (4 variants). |
| Wall (2-opposite) | 1 x 0.375 | 1.5 | Centered. Orientable (2 variants). Adjacent blocks must be solid, walls, or fencegates. Placing a block on top doesn't change its collision box. |
| Fence (2-opposite) | 1 x 0.25 | 1.5 | Centered. Orientable (2 variants). Adjacent blocks must be solid, fences, or fencegates. |
| Fencegate | 1 x 0.25 | 1.5 | Centered. Orientable (2 variants). Can be flipped to a variant with no collision box. |
| Trapdoor (vertical) | 1 x 0.1875 | 1 | Orientable, Inversible (8 variants). Can be flipped to its horizontal variant. |
| Door | 1 x 0.1875 | 1 | Orientable (4 variants). Can be flipped to another variant. |
| Ladder | 1 x 0.125 | 1 | Orientable (4 variants). Can be climbed by entities. |
| Pane (2-opposite) | 1 x 0.125 | 1 | Centered. Orientable (2 variants). Adjacent blocks must be solid, or panes/bars. |
| Chest (long) | 0.9375 x 0.875 | 0.875 | Must be next to another chest. |
| Dragon Egg | 0.875 x 0.875 | 1 | Centered. Looks drastically different from its collision box. |
| Cactus | 0.875 x 0.875 | 0.9375 | Centered. Hurts entities close to it. |
| Chest | 0.875 x 0.875 | 0.875 | Centered. |
| Cake | 0.875 x 0.875, 0.875 x 0.75, 0.875 x 0.625, 0.875 x 0.5, 0.875 x 0.375, 0.875 x 0.25, 0.875 x 0.125 | 0.5 | The full cake is centered. Each bite takes away 0.125b from the West (-X). |
| Wall (2-adjacent) | 0.75 x 0.75 | 1.5 | Orientable (4 variants). Adjacent blocks must be solid, walls, or fencegates. |
| Wall (1-sided) | 0.75 x 0.5 | 1.5 | Orientable (4 variants). Adjacent block must be solid, a wall, or a fencegate. |
| Fence (1-sided) | 0.625 x 0.25 | 1.5 | Orientable (4 variants). Adjacent block must be solid, a fence, or a fencegate. |
| Wall (default) | 0.5 x 0.5 | 1.5 | Centered. |
| Cocoa (big) | 0.5 x 0.5 | Low: 0.1875, Top: 0.75 | Orientable (4 variants). 1px away from the wall it's attached to. Top texture is only 7px wide. |
| Head (walled) | 0.5 x 0.5 | Low: 0.25, Top: 0.75 | Orientable (4 variants). |
| Head (default) | 0.5 x 0.5 | 0.5 | Centered. A head can be placed diagonally (16 variants total) but the collision box doesn't change. |
| Pane (1-sided) | 0.5 x 0.125 | 1 | Orientable (4 variants). Appears 1px longer than it is. Adjacent block must be solid, or a pane/bar. |
| Cocoa (medium) | 0.375 x 0.375 | Low: 0.3125, Top: 0.75 | Orientable (4 variants). 1px away from the wall it's attached to. |
| Flowerpot | 0.375 x 0.375 | 0.375 | Centered. |
| Fence (default) | 0.25 x 0.25 | 1.5 | Centered. |
| Cocoa (small) | 0.25 x 0.25 | Low: 0.4375, Top: 0.75 | Orientable (4 variants). 1px away from the wall it's attached to. |

### Composite Collision Boxes (two or more bounding boxes)

"Except" represents an intangible zone rather than a tangible one.

| Block | Widths (b) | Height (b) | Comments |
| --- | --- | --- | --- |
| Stair (normal) | Base: 1 x 1, Top: 1 x 0.5 | Base: 0.5, Top: 1 | Orientable, Inversible (8 variants). |
| Stair (outer) | Base: 1 x 1, Top: 0.5 x 0.5 | Base: 0.5, Top: 1 | Orientable, Inversible (8 variants). Depends on adjacent stair blocks. |
| Stair (inner) | Base: 1 x 1, Except: -0.5 x -0.5 | Base: 1, Except: -0.5 | Orientable, Inversible (8 variants). Depends on adjacent stair blocks. |
| End Portal Frame (eye) | Base: 1 x 1, Eye: 0.375 x 0.375 | Base: 0.8125, Eye: 1 | The eye appears 8px wide but is actually 6px wide. |
| Hopper | Base: 1 x 1, Except: -0.75 x -0.75 | Base: 1, Except: -0.375 | The bottom is tangible. Interior floor is 1px lower than it looks. |
| Cauldron | Base: 1 x 1, Except: -0.75 x -0.75 | Base: 1, Except: -0.6875 | The bottom is tangible. Interior floor is 1px higher than it looks. |
| Brewing Stand | Base: 1 x 1, Rod: 0.125 x 0.125 | Base: 0.125, Rod: 0.875 | |
| Piston Head (Vertical) | Head: 1 x 1, Arm: 0.25 x 0.25 | Head: 0.25, Top: 1 | Centered. Inversible. Arm shorter than it looks (model extended by 4px). |
| Piston Head (N/S/E) | Head: 1 x 0.25, Arm: 0.75 x 0.5 | Head: 1, Arm-Low: 0.375, Arm-Top: 0.625 | Orientable (3 variants). Arm wider than it looks (0.5m). Arm shorter than it looks (model extended 4px). |
| Piston Head (West) | Head: 1 x 0.25, Arm: 0.75 x 0.25 | Head: 1, Arm-Low: 0.25, Arm-Top: 0.75 | Bugged (fixed in 1.9). The player can walk through a west extended piston. In 1.9 piston heads match their model. |
| Fence (4-sided) | Post: 0.25 x 0.25, Sides: 0.375 x 0.25 | 1.5 | Adjacent blocks must be solid, fences, or fencegates. |
| Fence (3-sided) | Post: 0.25 x 0.25, Sides: 0.375 x 0.25 | 1.5 | Orientable (4 variants). |
| Fence (2-adjacent) | Post: 0.25 x 0.25, Sides: 0.375 x 0.25 | 1.5 | Orientable (4 variants). |
| Pane (Default / 4-sided) | Sides: 0.5 x 0.125 | 1 | Adjacent blocks must be solid, or panes/bars. |
| Pane (3-sided) | Sides: 0.5 x 0.125 | 1 | Orientable (4 variants). |
| Pane (2-adjacent) | Sides: 0.5 x 0.125 | 1 | Orientable (4 variants). The exterior corner has a 1px wide opening. |

### Other

| Block | Widths (b) | Height (b) | Comments |
| --- | --- | --- | --- |
| Boat | 1.5 x 1.5 | 0.6 | Entity. |

## Slipperiness

Each block has a Slipperiness factor S. The greater S, the slipperier. By default S = 0.6 (includes air, soulsand, cobwebs, fluids). In 1.8 the only blocks with different slipperiness are: Ice and Packed Ice S = 0.98; Slime Blocks S = 0.8. In 1.13 Blue Ice was added, S = 0.989 (most slippery in the game).

Effect on Movement: when moving, the player loses some speed between ticks (drag) and gains acceleration. On ground: the amount of speed conserved on ground is scaled by 0.91 * S. The acceleration gained on ground is proportional to (0.6/S)^3. When airborne, slipperiness is ignored.

| Block | Slipperiness S |
| --- | --- |
| Default | 0.6 |
| Slime | 0.8 |
| Ice / Packed Ice | 0.98 |
| Blue Ice [1.13+] | 0.989 |

Application: every tick, if on ground, the game checks the block directly 1b below the player's position to get slipperiness. Partial blocks are affected by the slipperiness of the block below them (e.g. a slab above Ice has the same slipperiness as the Ice). Soulsand is a non-full block (14px tall) so it gets its slipperiness from the block below it. Two effects: slippery blocks grant less acceleration; soulsand reduces speed conservation; combined they create a net negative impact, so walking on soulsand with ice below is noticeably slower.

Changes: In 1.15, slipperiness is taken 0.5m below the player (instead of 1.0m). Soulsand is no longer affected by slipperiness, among other blocks. A slab (0.5b height) is still affected; a bed (0.5625b height) is no longer affected.

## Soulsand

Soulsand is a special block that slows down entities walking on it. Soulsand is 0.875b tall, so it's affected by the slipperiness of ice and slime blocks; walking on soulsand with ice or slime below is slower than regular soulsand. Soulsand slows down entities by multiplying their horizontal velocity by 0.4 at the end of each tick. This can be applied even when airborne, notably on the last tick of a jump. The only requirement is that the player's bounding box collides with the voxel occupied by the soulsand (minus 0.001m on each side). The effect is cumulative: the 0.4 multiplier is applied for each soulsand block the player is standing on, so it's slightly slower to walk on the seam between two soulsand blocks. The player can stand on the outer edge of soulsand (up to 0.001m) without being slowed.

Code:

```java
/* In Entity.java, called at the end of each tick (from Entity.moveEntity) */
protected void doBlockCollisions()
{
    BlockPos posMin = new BlockPos(this.minX+0.001, this.minY+0.001, this.minZ+0.001);
    BlockPos posMax = new BlockPos(this.maxX-0.001, this.maxY-0.001, this.maxZ-0.001);
    for (int i = posMin.getX(); i <= posMax.getX(); ++i)
        for (int j = posMin.getY(); j <= posMax.getY(); ++j)
            for (int k = posMin.getZ(); k <= posMax.getZ(); ++k)
            {
                BlockPos pos = new BlockPos(i, j, k);
                Block block = getBlockState(pos);
                block.onEntityCollidedWithBlock(this);
            }
}
/* In BlockSoulSand.java */
public void onEntityCollidedWithBlock(Entity entityIn)
{
    entityIn.motionX *= 0.4;
    entityIn.motionZ *= 0.4;
    //NOTE: each soulsand block applies this effect individually.
}
```

Version Differences: 1.13 generates upward bubble columns when placed underwater. 1.15 major changes: no longer affected by slipperiness (as are all blocks of height > 0.5b); slowdown now only applies to the inner surface (the 0.3m perimeter acts as a normal block); slowdown applies when the player is standing less than 0.5b above it (includes standing on a slab); inside a soulsand block there are intermediate floors at heights 0.125, 0.25, 0.375, 0.5, 0.625, 0.75 (the first four affected by slipperiness).

## Cobweb

The Cobweb slows down entities moving through it. Cobwebs are intangible (player can move through, no collision box) but have an "effect box" 0.001b smaller than a full block. If the player's bounding box intersects this effect box, their velocity is set to 0 and movement is hindered (effect applies if overlap is more than 0.001m). The player can stand next to a cobweb if at most 0.001m away from the edge.

Vertical Movement: Vertical speed is slowed by a factor of 20 and is reset every tick. Jumping is severely limited, reaching just 0.021m in height. The player then immediately falls at a constant speed of -0.00392 m/t.

Horizontal Movement: Horizontal speed is slowed by a factor of 4 and reset every tick. The player moves at a constant 0.03185 m/t when sprinting on ground, or 0.00637 m/t while airborne. With 45 strafe: 0.0325 m/t sprinting on ground, 0.0065 m/t airborne.

Calculations: 0.42 / 20 = 0.021; (-0.08*0.98) / 20 = -0.00392; (0.098 * 1.3 * 0.2) / 4 = 0.03185; (0.1 * 1.3 * 0.2) / 4 = 0.0325.

## Ladders and Vines

Ladders are special blocks that allow entities to climb them. Vines (added Beta 1.8) share the same properties but have no collision.

Strategy: when attempting a ladder jump, always sneak at the end of the jump. Don't sneak too late (won't catch the ladder) or too early (slows you down before reaching the ladder).

Collision Box: a ladder's collision box is 2 pixels wide. Ladders are 2px wide (0.125b), 1 block high, 1 block long, resting 1px away from the wall. Vines are intangible (player moves through).

Properties: while the player's coordinates are within a ladder's voxel, vertical movement is affected: colliding with any wall makes the player climb at a constant rate of 0.1176 b/t (0.12 * 0.98); sneaking prevents the player from falling. Horizontal movement: horizontal speeds (X/Z) are capped at 0.15 b/t; vertical speed (Y) has a lower bound of -0.15 b/t.

Changes (1.9+): In 1.9, ladders were made larger by 1px, new bounding box comparable to trapdoors (3px wide, 0.1875b). In 1.14, the player can climb ladders and vines by pressing jump, making ladder jumps feel easier.
