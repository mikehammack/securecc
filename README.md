# SecureCC

Secure diamond-tier upgrades for [CC:Tweaked](https://github.com/cc-tweaked/CC-Tweaked) and
[Plethora](https://github.com/SquidDev/plethora) on **Minecraft 1.12.2** (Forge 14.23.5.2860).

The secure tier sits above the basic (stone) and advanced (gold) tiers and adds real access
control to your in-game computers:

- **Owner-only access by default** — only the owner (or players the owner adds) can use a
  secure computer or turtle; only owner-approved players can use a secure monitor's
  touchscreen input.
- **Friend lists** — owners manage who may use their machines through a simple GUI:
  **shift-right-click** the block to open it. (`/securecc friend` still works.)
- **PIN access** — share a machine via a PIN without adding a friend. PINs are stored as
  salted SHA-256 hashes (never plaintext) and wrong guesses are throttled.
- **Offline policies** — per-machine behavior when the owner is offline:
  Stay Running, Lock, PIN Access, Friend Access, or Shutdown (default: Lock).
- **Management GUI** — shift-right-clicking a secure block opens a tabbed screen:
  **Friends** (add/remove), **PIN** (set/change/remove, lockout state), and **Policy**
  (pick the block's access policy). All changes are verified server-side.
  The **Security Key** opens the same screen without needing to shift-click, and can
  also manage the secure neural interface worn by a mob or player.
- **Protected blocks** — secure blocks are destructible with super-high strength and
  non-owners cannot break them; ops can override (configurable).
- **Owner persistence** — breaking and replacing a secure block keeps its owner, PIN,
  friends, and policy via the item's NBT.
- **Offline-mode friendly** — friends work on offline-mode servers too: friends added
  offline are matched by name (case-insensitive); on online-mode servers the mod looks
  up the player's UUID from Mojang's profile service when the player is not online.
- **`/securecc` command** — policy, pin, friend, and info management.

## Contents

| Block | Description |
|---|---|
| Secure Computer | Upgrade of the advanced computer |
| Secure Turtle | Upgrade of the advanced turtle, diamond-styled (tools/modems render) |
| Secure Monitor | Multi-block merging monitor, 6 per craft |
| Secure Manipulator | Plethora Mark II manipulator with access control |
| Secure Modem | Wired modem peripheral — `getNamesRemote`/`callRemote` reach peripherals on your own secure cable network |
| Secure Cable | Wired-network cable; links secure devices into an owner-private network |
| Secure Neural Interface | Plethora neural interface with owner/friend/PIN/policy gating — secure your own and your mobs' interfaces |
| Security Key | Handheld tool: opens the management GUI on any secure block, or on a mob/player wearing a secure neural interface |

The Security Key is crafted from a CC:Tweaked floppy disk surrounded by diamonds:

```
DDD
DFD
DDD
```

The Secure Neural Interface is crafted from a Plethora neural interface in the
center, a diamond block above it, and diamonds in the remaining slots:

```
DBD
DND
DDD
```

Upgrade recipes are CC:Tweaked-style shaped recipes: advanced device in the center, diamond
block above, loose diamonds in the other seven slots. NBT (computer ID/label, turtle
upgrades/fuel) is preserved through the upgrade.

## What's new in 1.4.8

- **Secure wired networking** — new **Secure Cable** and **Secure Modem** blocks.
  Cables link your secure devices into an owner-private wired network, and the
  modem exposes the vanilla remote-peripheral calls (`getNamesRemote`,
  `isPresentRemote`, `getTypeRemote`, `getMethodsRemote`, `callRemote`) against
  peripherals on that network — from any secure computer on your cables.
- **Peripheral confinement** — vanilla computers, turtles, and wired modems can no
  longer `peripheral.wrap` secure computers, turtles, monitors, or modems. Your
  machines simply don't exist to anyone else's devices.
- **Reliability fixes** — one `transmit` now delivers exactly one `modem_message`
  (duplicate wireless+wired delivery eliminated), and attach-gated Plethora
  peripherals (e.g. lasers in the secure manipulator) now work over `callRemote`.

## What's new in 1.3.1

- **Management GUI actually opens** — the Friends/PIN/Policy screen now opens
  via the Security Key's plain right-click and via shift-right-click on all
  four secure block types (previously the server-side GUI handler returned
  null, which made Forge skip sending the open-GUI packet entirely).
- **Secure Neural Interface recipe** — now craftable: Plethora neural interface
  in the center, diamond block above it, diamonds in the remaining slots
  (previously referenced a wrong Plethora item ID).
- **Block placement against secure blocks** — sneak-right-clicking with an item
  in hand now performs the normal vanilla action (e.g. placing a monitor on
  top of the computer) instead of opening the management GUI; empty-hand sneak
  still opens the GUI. Terminal access checks are unchanged.
- **Neural interface in the creative tab** — the Secure Neural Interface now
  appears in the ComputerCraft creative tab alongside the Security Key.

## What's new in 1.3.0

- **Security Key** — new item (floppy disk surrounded by diamonds). Right-click a
  secure computer, turtle, monitor, or manipulator to open its Friends/PIN/Policy
  management screen; right-click a mob or player wearing a secure neural interface
  to manage that interface.
- **Manipulator display name** — the secure manipulator item now shows "Secure
  Manipulator" consistently (it previously read off the block's translation key).
- **Turtle render size** — the secure turtle body now renders at the correct size and
  position (it previously appeared quarter-size and offset into a block corner).

## Building

No Gradle here — the build uses a manual `javac` toolchain:

```bash
./build.sh
```

Output: `build/libs/securecc-1.4.8.5-forge1122.jar`

Requirements:
- Java 25 JDK (compiles with `--release 8`)
- The Market Blocks 1.12.2 toolchain artifacts at `~/workspace/market-blocks/forge1122/build/`
  (MCP-named Minecraft + Forge jars and `mcp2srg.srg`) — the build reobfuscates MCP → SRG
  with SpecialSource.
- Pinned dependency jars are committed under `libs/`:
  `cc-tweaked-1.12.2-1.89.2.jar`, `plethora-1.12.2-1.2.3.jar`, `authlib-1.5.25.jar`
  (authlib is Mojang's; the rest are SquidDev's CC:Tweaked/Plethora), plus
  compile-only `commons-lang3-3.5.jar` and `vecmath-1.5.2.jar` from Maven Central
  (needed for the turtle renderer's upgrade models; never packaged).
- `src/stubs/` holds compile-only mapping/API stubs that are never packaged into the jar
  (a Forge-added `Block` method, plus the Baubles/Botania API types Plethora's
  neural interface references behind `@Optional`).

## Config

All under the `security` category of `securecc.cfg`:

- `allowOpBypass` (default `true`) — ops bypass all access checks.
- `pinMaxAttempts` (default `5`) — wrong PIN entries before PIN entry locks out.
- `pinLockoutSeconds` (default `300`) — lockout duration in seconds (0 disables).

## License

© Maxim Arcana. All rights reserved — no license has been chosen yet.
