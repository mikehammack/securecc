# SecureCC

Secure diamond-tier upgrades for [CC:Tweaked](https://github.com/cc-tweaked/CC-Tweaked) and
[Plethora](https://github.com/SquidDev/plethora) on **Minecraft 1.12.2** (Forge 14.23.5.2860).

The secure tier sits above the basic (stone) and advanced (gold) tiers and adds real access
control to your in-game computers:

- **Owner-only access by default** — only the owner (or players the owner adds) can use a
  secure computer or turtle; only owner-approved players can use a secure monitor's
  touchscreen input.
- **Friend lists** — owners manage who may use their machines.
- **PIN access** — share a machine via a PIN without adding a friend.
- **Offline policies** — per-machine behavior when the owner is offline:
  Shutdown, PinAccess, FriendAccess, Lock, or Stay Running (default: Lock).
- **Protected blocks** — secure blocks are destructible with super-high strength and
  non-owners cannot break them; ops can override.
- **`/securecc` command** — policy, pin, friend, and info management.

## Contents

| Block | Description |
|---|---|
| Secure Computer | Upgrade of the advanced computer |
| Secure Turtle | Upgrade of the advanced turtle, diamond-styled |
| Secure Monitor | Multi-block merging monitor, 6 per craft |
| Secure Manipulator | Plethora neural-interface style manipulator |

Upgrade recipes are CC:Tweaked-style shaped recipes: advanced device in the center, diamond
block above, loose diamonds in the other seven slots. NBT (computer ID/label, turtle
upgrades/fuel) is preserved through the upgrade.

## Building

No Gradle here — the build uses a manual `javac` toolchain:

```bash
./build.sh
```

Output: `build/libs/securecc-1.0.0-forge1122.jar`

Requirements:
- Java 25 JDK (compiles with `--release 8`)
- The Market Blocks 1.12.2 toolchain artifacts at `~/workspace/market-blocks/forge1122/build/`
  (MCP-named Minecraft + Forge jars and `mcp2srg.srg`) — the build reobfuscates MCP → SRG
  with SpecialSource.
- Pinned dependency jars are committed under `libs/`:
  `cc-tweaked-1.12.2-1.89.2.jar`, `plethora-1.12.2-1.2.3.jar`, `authlib-1.5.25.jar`
  (authlib is Mojang's; the rest are SquidDev's CC:Tweaked/Plethora).
- `src/stubs/` holds compile-only mapping stubs that are never packaged into the jar.

## Known limitations

- Adding/removing friends requires the target player to be online.
- PINs are stored in plaintext NBT.
- Breaking and replacing a secure block resets its owner to the placer.

## License

© Maxim Arcana. All rights reserved — no license has been chosen yet.
