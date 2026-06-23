# Minecraft Movement Formulas (mcpk.wiki reference)

Mirrored from the Minecraft Parkour Wiki (mcpk.wiki); captured in-repo because the wiki is Cloudflare-gated. Reference, not implementation. Source pages: Player Movement, 45 Strafe, Horizontal Movement Formulas, Vertical Movement Formulas, Nonrecursive Movement Formulas.

## Player Movement

Every tick, the game checks for the player's inputs and translates them into motion.

Minecraft's moveset: on top of jumping, the player has three move speeds:

- Walking (default)
- Sprinting (faster, jump further)
- Sneaking (slower, can walk to block edge and grab ladders/vines)

Each of these three actions has a base acceleration added to the player's velocity every tick:

| Action | Base acceleration |
| --- | --- |
| Walking | 0.1 |
| Sprinting | 0.13 |
| Sneaking | 0.03 |

This base value is multiplied by 0.2 when mid-air, and varies depending on whether the player is Strafing.

## 45 Strafe

45 degree Strafe grants more speed than regular movement, done by strafing and turning 45 degrees. Performed by: turning 45 left and strafing right, OR turning 45 right and strafing left. The turning must be quick (less than 1 tick) and precise (at least +/-11.5 degrees, closer to 0 is better). Some jumps require it, such as 1bm 4.375b.

Effect on Movement: On every tick the player gains acceleration depending on inputs. When moving forward (without strafing), acceleration is scaled by 0.98. When strafing, acceleration is scaled by 1.0. Therefore 45 strafe is 1.0/0.98 times faster than regular movement (about 2% faster).

Special case 45 Sneak: when sneaking forward without strafing, acceleration scaled by 0.98. When strafing while sneaking, acceleration scaled by 0.98*sqrt(2) (approx 1.386). 45 Sneak used for no-momentum jumps, and for bridging (about 41% faster than regular sneaking).

Source code (simplified, from Entity and EntityLivingBase):

```java
public void onLivingUpdate()
{
    /* moveStrafing = 1.0 if moving left, -1.0 if moving right, else 0.0
       moveForward = 1.0 if moving forward, -1.0 if moving backward, else 0.0
       Furthermore, moveStrafing and moveForward *= 0.3 if the player is sneaking. */
    this.moveStrafing *= 0.98F;
    this.moveForward *= 0.98F;
    this.moveEntityWithHeading(this.moveStrafing, this.moveForward);
}
public void moveEntityWithHeading(float strafe, float forward)
{
    /* inertia determines how much speed is conserved onto the next tick */
    float mult = 0.91F;
    if (this.onGround)
    {
        /* Get slipperiness 1 block below the player */
        mult *= getBlockSlipperinessAt(this.posX, this.getEntityBoundingBox().minY - 1, this.posZ);
    }
    /* acceleration = (0.6*0.91)^3 / (slipperiness*0.91)^3) */
    float acceleration = 0.16277136F / (mult * mult * mult);
    float movementFactor;
    if (this.onGround)
        movementFactor = this.landMovementFactor * acceleration;
        /* base: 0.1; x1.3 if sprinting, affected by potion effects. */
    else
        movementFactor = this.airMovementFactor;
        /* base: 0.02; x1.3 if sprinting */
    this.updateMotionXZ(strafe, forward, movementFactor);
    this.moveEntity(this.motionX, this.motionY, this.motionZ);
    this.motionY -= 0.08D; /* gravity */
    this.motionY *= 0.98D; /* drag */
    this.motionX *= mult;
    this.motionZ *= mult;
}
public void updateMotionXZ(float strafe, float forward, float movementFactor)
{
    /* Sprint multiplier is contained within movementFactor; Sneak multiplier is contained within strafe and forward */
    float distance = strafe * strafe + forward * forward;
    if (distance >= 1.0E-4F)
    {
        distance = MathHelper.sqrt_float(distance);
        if (distance < 1.0F)
            distance = 1.0F;
        distance = movementFactor / distance;
        strafe = strafe * distance;
        forward = forward * distance;
        float sinYaw = MathHelper.sin(this.rotationYaw * Math.PI / 180.0F);
        float cosYaw = MathHelper.cos(this.rotationYaw * Math.PI / 180.0F);
        this.motionX += strafe * cosYaw - forward * sinYaw;
        this.motionZ += forward * cosYaw + strafe * sinYaw;
    }
}
```

## Horizontal Movement Formulas

On every tick:

1. Acceleration is added to the player's velocity.
2. The player is moved (new position = position + velocity).
3. The player's velocity is reduced to simulate drag.

### Multipliers

Movement Multiplier M_t = (movement state) times (strafe state):

Movement state factor:

| State | Factor |
| --- | --- |
| Sprinting | 1.3 |
| Walking | 1.0 |
| Sneaking | 0.3 |
| Stopping | 0.0 |

Strafe state factor:

| State | Factor |
| --- | --- |
| Default | 0.98 |
| 45 degree Strafe | 1.0 |
| 45 degree Sneak | 0.98*sqrt(2) |

Effects Multiplier:

```
E_t = (1 + 0.2*Speed) * (1 - 0.15*Slowness)
```

This increases by 20% per level of Speed and decreases by 15% per level of Slowness, and is >= 0.

Slipperiness Multiplier S_t:

| Surface | Factor |
| --- | --- |
| Default | 0.6 |
| Slime | 0.8 |
| Ice | 0.98 |
| Airborne | 1.0 |

### Linear Formulas

Linear movement, no change in direction. V_{H,0} is initial speed (default 0); V_{H,t} is speed on tick t.

Ground Speed:

```
V_{H,t} = V_{H,t-1} * S_{t-1} * 0.91            (Momentum)
        + 0.1 * M_t * E_t * (0.6/S_t)^3         (Acceleration)
```

Jump Speed:

```
V_{H,t} = V_{H,t-1} * S_{t-1} * 0.91
        + 0.1 * M_t * E_t * (0.6/S_t)^3
        + { 0.2 if Sprinting, 0.0 else }        (Sprintjump Boost)
```

Air Speed:

```
V_{H,t} = V_{H,t-1} * S_{t-1} * 0.91
        + 0.02 * M_t
```

### Complete Formulas

D_t is the player's Direction in degrees (defined by inputs and rotation); F_t is the player's Facing in degrees (defined by rotation only).

Ground Velocity:

```
V_{X,t} = V_{X,t-1}*S_{t-1}*0.91 + 0.1*M_t*E_t*(0.6/S_t)^3 * sin(D_t)
V_{Z,t} = V_{Z,t-1}*S_{t-1}*0.91 + 0.1*M_t*E_t*(0.6/S_t)^3 * cos(D_t)
```

Jump Velocity:

```
V_{X,t} = V_{X,t-1}*S_{t-1}*0.91 + 0.1*M_t*E_t*(0.6/S_t)^3 * sin(D_t) + { 0.2 if Sprinting, 0.0 else } * sin(F_t)
V_{Z,t} = V_{Z,t-1}*S_{t-1}*0.91 + 0.1*M_t*E_t*(0.6/S_t)^3 * cos(D_t) + { 0.2 if Sprinting, 0.0 else } * cos(F_t)
```

Air Velocity:

```
V_{X,t} = V_{X,t-1}*S_{t-1}*0.91 + 0.02*M_t * sin(D_t)
V_{Z,t} = V_{Z,t-1}*S_{t-1}*0.91 + 0.02*M_t * cos(D_t)
```

### Stopping Conditions

Wall Collision: if the player hits an X-facing wall, momentum is cancelled and V_{X,t} only includes acceleration. If the player hits a Z-facing wall, momentum is cancelled and V_{Z,t} only includes acceleration. In either case the player stops sprinting.

Negligible Speed Threshold: if |V_{X,t-1}*S_{t-1}*0.91| < 0.005, momentum is cancelled and only acceleration is left (same for Z). In 1.9+, compared to 0.003 instead.

Note: Minecraft's coordinate system is oriented differently: 0 degrees points towards positive Z, 90 degrees points towards negative X. The wiki uses the standard coordinate system to make calculations more intuitive.

## Vertical Movement Formulas

Jump Formula:

```
V_{Y,1} = 0.42
V_{Y,t} = (V_{Y,t-1} - 0.08 gravity) * 0.98 drag
```

If |V_{Y,t}| < 0.005, V_{Y,t} is set to 0 instead (the player's height doesn't change for that tick). In 1.9+ compared to 0.003 instead.

Notes:

- V_{Y,0} has no importance (0th tick = initial velocity before jumping).
- V_{Y,1} = initial jump motion, increased by 0.1 per level of Jump Boost.
- Jump height slightly higher in 1.9 due to momentum threshold reduced (old=1.249, new=1.252).
- Terminal velocity is -3.92 m/t.
- When the player collides vertically with a block, vertical momentum is cancelled and only the acceleration is left.

Jump duration (number of ticks between jumping and landing; also the period of the jump's cycle when repeated):

| Jump | Duration (ticks) |
| --- | --- |
| Flat Jump | 12 |
| 3bc Jump | 11 |
| +0.5 Jump | 10 |
| +1 Jump | 9 |
| 2.5bc Jump | 6 |
| 2bc Jump | 3 |
| 1.8125bc Jump | 2 |

Source code (from EntityLivingBase):

```java
    protected float getJumpUpwardsMotion(){ return 0.42F; }
    protected void jump()
    {
        this.motionY = this.getJumpUpwardsMotion();
        if (this.isPotionActive(Potion.jump))
        {
            this.motionY += (this.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F;
        }
        this.isAirBorne = true;
    }
    public void moveEntityWithHeading(float strafe, float forward)
    {
        ... /* also moves the player horizontally */
        this.motionY -= 0.08;
        this.motionY *= 0.98;
    }
    public void onLivingUpdate()
    {
        if (this.jumpTicks > 0) --this.jumpTicks;
        if (Math.abs(this.motionY) < 0.005D) this.motionY = 0.0D;
        if (this.isJumping)
        {
            if (this.onGround && this.jumpTicks == 0)
            {
                this.jump();
                this.jumpTicks = 10; //activate autojump cooldown (0.5s)
            }
        }
        else { this.jumpTicks = 0; }
        this.moveEntityWithHeading(this.moveStrafing, this.moveForward);
    }
```

## Nonrecursive Movement Formulas

Definitions:

- v_0 is the player's initial speed (speed on t_0, before jumping).
- t is the number of ticks considered (e.g. flat ground jump duration is t=12).
- J is the "jump bonus": 0.3274 for sprintjump, 0.291924 for strafed sprintjump, 0.1 for 45 degree no-sprint jump.
- M is the movement multiplier after jumping: 1.3 for 45 degree sprint, 1.274 for normal sprint, 1.0 for no-sprint 45 degree.

### Vertical Movement (jump) [1.8], for t >= 6

```
V_Y(t)   = 4 * 0.98^(t-5) - 3.92
Y_rel(t) = (197.4 - 217*0.98^5) [jumppeak] + 200*(0.98 - 0.98^(t-4)) - 3.92*(t-5)
```

### Vertical Movement (jump) [1.9+], for t >= 1

```
V_Y(t)   = 0.42*0.98^(t-1) + 4*0.98^t - 3.92
Y_rel(t) = 217*(1 - 0.98^t) - 3.92*t        (for t >= 0)
```

### Horizontal Movement (instant jump)

Assuming airborne before jumping, t >= 2:

```
V_H(v_0, t)  = 0.02M/0.09 + 0.6*0.91^t * ( v_0 + J/0.91 - 0.02M/(0.6*0.91*0.09) )
Dist(v_0, t) = 1.91*v_0 + J + (0.02M/0.09)*(t-2) + (0.6*0.91^2/0.09) * (1 - 0.91^(t-2)) * ( v_0 + J/0.91 - 0.02M/(0.6*0.91*0.09) )
```

### Horizontal Movement (delayed jump)

Assuming on ground at least 1 tick before jumping, t >= 2:

```
V_H*(v_0, t)  = 0.02M/0.09 + 0.6*0.91^t * ( 0.6*v_0 + J/0.91 - 0.02M/(0.6*0.91*0.09) )
Dist*(v_0, t) = 1.546*v_0 + J + (0.02M/0.09)*(t-2) + (0.6*0.91^2/0.09) * (1 - 0.91^(t-2)) * ( 0.6*v_0 + J/0.91 - 0.02M/(0.6*0.91*0.09) )
```

Note: these formulas are accurate for most values of v_0, but some negative values can activate the speed threshold and reset the player's speed, rendering them inaccurate.

### Advanced

Horizontal speed after n consecutive sprintjumps on a momentum of period T (n >= 0, T >= 2):

```
V_H^n(v_0, T, n) = (0.6*0.91^T)^n * v_0
                 + ( 0.6*0.91^(T-1)*J + 0.02M*(1 - 0.91^(T-1))/0.09 ) * (1 - (0.6*0.91^T)^n)/(1 - 0.6*0.91^T)
```

If the first sprintjump is delayed, multiply v_0 by 0.6.

General note (Movement Formulas page): these formulas are not exact due to how floats are computed; only the first 4 to 6 decimals should be considered accurate. For a completely accurate simulation, you must replicate the source code.
