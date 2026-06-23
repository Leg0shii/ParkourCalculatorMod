# Angles and Mouse Movement

Mirrored from the Minecraft Parkour Wiki (mcpk.wiki); captured in-repo because the wiki is Cloudflare-gated. Reference, not implementation. Source pages: Ticks, Mouse Movement, Angles.

## Ticks

Ticks are the standard unit of time in Minecraft, with one tick being equal to 50 milliseconds.

**Tickrate:** The physics engine runs at 20 ticks per second, meaning the game's physics are updated every 50ms. This includes the player's position and speed, the environment, and entities. In-game actions are performed at the end of each tick, regardless of the time or order in which they were called. Due to this, inputs may take up to 50ms of delay between the button press and their activation.

**Turn Tick:** Mouse Movement is not inherently tied to tickrate, but rather the framerate. However, the game keeps a copy of the player's rotation for movement calculations, which it updates once every tick. The moment the player's rotation is copied is called the turn tick. From the player's perspective there is no way to control when the turn tick happens. This has a severe impact on turn-based jumps, which become partially luck-based: given the same smooth turning sequence, the resulting movement could be quite different. One solution is to turn instantly every 50ms to land precisely on the wanted angle. This is feasible for simple turn strats such as 45 degree strafes, but not for more complex strats that require smooth turning.

## Mouse Movement

Mouse movement represents the instant displacement of the cursor on the screen in pixels `(dx, dy)`. In Minecraft it represents the instant rotation of the camera in degrees `(delta_x, delta_y)`.

**Sensitivity** `s` is a parameter that changes how fast the camera turns, set in the Controls menu.

| Sensitivity | Value of s |
| --- | --- |
| Default "100%" | s = 0.5 |
| Lowest vanilla "0%" | s = 0.0 |
| Highest vanilla "200%" | s = 1.0 |

In 1.8, `delta_x` is calculated as:

```
delta_x = 1.2 * dx * (0.6*s + 0.2)^3
```

`delta_y` is obtained the same way, multiplied by -1 if "Invert Mouse" is ON.

With default sensitivity (s = 0.5), one pixel of mouse movement translates into 0.15 degrees of rotation. So the camera moves in increments of 0.15 degrees: to turn 45 degrees you would move your mouse by 300px.

`s` is not technically bounded by `[0, 1]` and can take any value (even negative). You can manually edit `mouseSensitivity` in `options.txt`.

Inverse formula for the required sensitivity for a desired increment `delta`:

```
s = ( cbrt(delta / 1.2) - 0.2 ) / 0.6
```

Remarkable values table:

| delta (deg) | s |
| --- | --- |
| 0 | -0.3333333 |
| 0.1 | 0.3946504 |
| 0.15 | 0.5 |
| 0.25 | 0.6546926 |
| 0.5 | 0.9115013 |
| 1 | 1.2350600 |
| 45 | 5.2452746 |
| 180 | 8.5221547 |

**Yaw and Pitch:** Yaw (horizontal rotation) and pitch (vertical rotation) are floats that keep track of an entity's head rotation. Facing is the restriction of the yaw to `[-180, 180]`, as represented in F3. Pitch is naturally clamped between `[-90, 90]`. Mouse movement directly modifies yaw and pitch:

```java
/* In EntityRenderer.java */
public void updateMouseMovement(...)
{
    float f = this.mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
    float mult = f * f * f * 8.0F;
    float dX = (float)this.mc.mouseHelper.deltaX * mult;
    float dY = (float)this.mc.mouseHelper.deltaY * mult;
    int i = 1;
    if (this.mc.gameSettings.invertMouse) i = -1;
    this.mc.thePlayer.rotateEntity(dX, dY*i);
}
/* In Entity.java */
public void rotateEntity(float dX, float dY)
{
    this.rotationYaw = this.rotationYaw + dX*0.15);
    this.rotationPitch = this.rotationPitch - dY*0.15;
    this.rotationPitch = MathHelper.clamp(this.rotationPitch, -90.0, 90.0);
}
```

Pitch is clamped between -90 and 90 degrees, but yaw is NOT restricted to `[-180, 180]`; yaw is unbounded. Consequences of deliberately turning one direction long enough: by nature of being a float, precision worsens the bigger it gets.

| Condition | Effect |
| --- | --- |
| `|yaw| > 4194304` (2^22) | yaw can only increase in increments of 0.5 deg |
| `|yaw| > 8388608` (2^23) | yaw can only increase in increments of 1 deg |
| `|yaw| >= 11796480` (= 360 * 2^15) | yaw gets too large to convert to a proper angle; movement stops the player in place |
| `|yaw| > 8.59e9` | turning further can crash the game |

## Angles

The player's yaw is a float tracking horizontal rotation in degrees; it is unbounded. The player's facing is the restriction of the yaw to `[-180, 180]` degrees, as shown in F3. A significant angle, or simply angle, is an integer from 0 to 2^16 - 1.

**Significant Angles:** Minecraft relies on significant angles for its trigonometry, so the player's yaw has to be converted to an angle. This conversion induces imprecision: a significant angle spans across ~0.0055 degrees.

`Sin()` and `Cos()` source code (from class `MathHelper`):

```java
private static final float[] SIN_TABLE = new float[65536];
public static float sin(float value) //in radians
{
    return SIN_TABLE[(int)(value * 10430.378F) & 65535];
}
public static float cos(float value) //in radians
{
    return SIN_TABLE[(int)(value * 10430.378F + 16384.0F) & 65535];
}
static
{
    for (int i = 0; i < 65536; ++i)
    {
        SIN_TABLE[i] = (float)Math.sin((double)i * Math.PI * 2.0D / 65536.0D);
    }
}
```

Note: `& 65535` gives the positive remainder of a division by 65536 (2^16). To convert the player's yaw into radians (when calling sin and cos), the game uses two formulas:

```java
// formula used in general
f = this.rotationYaw * (float)Math.PI / 180.0F
// formula used when adding sprintjump boost
f = this.rotationYaw * 0.017453292F
```

Both have the same intent, but for large values the result may differ. In that case, sprintjumping moves the player slightly to the side, which may be useful for no-turn strats.

**Half Angles:** `(int)(value * 10430.378F)` and `(int)(value * 10430.378F + 16384.0F)` should be 16384 units apart (90 deg), but because of floating point imprecision some values end up 1 or more units further, causing a slight shift from the intended calculation. Half angles are such values, found "between" consecutive angles. Their existence is entirely due to the `+ 16384` term being inside the parentheses. Each half-angle has an associated Multiplier representing its effectiveness, corresponding to the norm of the unit vector obtained from `cos(f)` and `sin(f)`; mathematically it should always equal 1, but it does not here. "Increasing" half angles have a norm greater than 1 (increase movement speed); "Decreasing" half angles have a norm lesser than 1 (decrease movement speed).

```
norm = sqrt( cos(f)^2 + sin(f)^2 )
```

When multiplied with a given jump distance, it gives an upper bound for the improved jump distance with its corresponding half-angle.

**Large Half Angles:** On July 27 2021, kemytz discovered that certain yaws grant more speed when using Optifine Fast Math (a feature reducing significant angles down to 4096). This is not specific to fast math; vanilla "large half angles" were discovered soon after. Large half angles are more effective due to the low precision floats can work with at that range: instead of being shifted by a single unit, angles can be shifted by up to 64 units.

Example: yaw 5898195 deg gives a multiplier of 1.003, huge compared to small half angles (135.0055 deg gives 1.00005). This is the most effective half angle that exists, making jumps like 2.125bm 4+0.5 possible. Reaching such values may require macros or mods.

With Fast Math, half angles are further amplified and the player can move while their yaw is less than `360 * 2^19`, compared to `360 * 2^15` in vanilla. Example: yaw 121000000 deg gives a multiplier of 1.09, making flat momentum 5b possible; this relies on a mod and should not be considered official. New Optifine versions changed fast math, eliminating many FM half angles (since version U L5, December 2019).

**Characterization:** Decreasing half angles are abundant between -90 and 0 degrees, because negative floats are truncated up while positive floats are truncated down. Decreasing half angles exist rarely between 0 and 90 degrees. Increasing half angles exist rarely between 90 and 180 degrees. Large half angles are of the form `360 * 2^n - theta`, with `theta` in `[0, 90]` and `n` in `{0, ..., 14}`. They are all increasing until `n >= 8`, at which point they may be increasing or decreasing.
