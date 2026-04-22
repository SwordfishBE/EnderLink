# EnderLink

[![GitHub Release](https://img.shields.io/github/v/release/SwordfishBE/EnderLink?display_name=release&logo=github)](https://github.com/SwordfishBE/EnderLink/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/SwordfishBE/EnderLink/total?logo=github)](https://github.com/SwordfishBE/EnderLink/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/1EdUpHRe?logo=modrinth&logoColor=white&label=Modrinth%20downloads)](https://modrinth.com/mod/enderlinktp)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1521703?logo=curseforge&logoColor=white&label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/enderlink)


**Two blocks. One link. Infinite distance.**

EnderLink is a Fabric teleportation mod for Minecraft that lets players register teleport pads, link them together, and travel between them by sneaking on the block.

It is designed to be simple for players, configurable for server owners, and fully functional on dedicated servers.

---

## What EnderLink Does

EnderLink turns normal configured blocks into teleport pads.

Typical flow:

1. Place a configured teleport block.
2. Stand on it and run `/enderlink add`.
3. Place a second configured teleport block somewhere else.
4. Stand on it and run `/enderlink add`.
5. Link both pads with `/enderlink link TP1 TP2`.
6. Sneak on either linked pad to teleport to the other one.

Pads are stored permanently on the server, can be listed by command, and are automatically removed when the block is broken or becomes invalid.

---

## ✨ Features

- Configurable teleport blocks
- Two-way linked teleport pads
- Per-player pad and link limits
- UUID-based ownership
- Cross-dimension teleports with a configurable dimension allow-list
- Warmup countdown before teleporting
- Teleport cancels if the player moves during the warmup
- Configurable particles and arrival sound
- Carpet support on teleport blocks
- Safety checks for blocked, fluid-filled, and invalid destinations
- World border protection
- Optional LuckPerms support through `fabric-permissions-api`
- Optional Mod Menu + Cloth Config integration
- Compatibility guard for the `Elevator` mod
- Modrinth update check

---

## ‼️ How Teleporting Works

- The server owner configures which blocks may act as teleport pads
- A player stands on a configured block and runs `/enderlink add`
- The pad is stored as a unique name such as `TP1`, `TP2`, `TP3`, and so on
- Two pads can be linked with `/enderlink link TP1 TP2`
- A linked pad works in both directions
- The player must sneak on the pad to begin teleporting
- A warmup countdown starts
- The player must remain still during the warmup
- If the player moves, the teleport is canceled
- If the destination is safe and allowed, the teleport completes

---

## ✅ Carpet Support

You can place a carpet on top of a teleport block to hide it.

The pad still works for:

- adding the pad
- teleporting to it
- teleporting from it


---

## 🛑 Safety Rules

EnderLink blocks unsafe teleports.

Examples of blocked destinations:

- destination has no space for the player
- destination is underwater
- destination is inside lava or another fluid
- destination is outside the world border
- destination block is missing or no longer valid
- destination dimension is not allowed by config

---

## 🎮 Commands

### Player / Admin Commands

- `/enderlink add`
  Register the teleport block you are currently standing on.

- `/enderlink link <first> <second>`
  Link two teleport pads together. Links are always two-way.

- `/enderlink unlink <first> <second>`
  Remove the link between two pads.

- `/enderlink remove <name>`
  Remove a teleport pad completely.

- `/enderlink rename <name> <newName>`
  Rename a teleport pad.

- `/enderlink list`
  Show all stored pads and links.

- `/enderlink info`
  Show info about the pad you are currently standing on.

- `/enderlink info <name>`
  Show info about a specific teleport pad.

- `/enderlink reload`
  Reload the config from disk.

### Command Examples

```text
/enderlink add
/enderlink add
/enderlink link TP1 TP2
/enderlink list
/enderlink info TP1
/enderlink rename TP1 BASE
/enderlink unlink BASE TP2
/enderlink remove TP2
```

---

## 🔄 Permissions

EnderLink supports two permission modes:

1. Config-based access control
2. LuckPerms-based access control

### Without LuckPerms

If `luckPerms` is `false`, EnderLink uses the config access settings:

- `EVERYONE`
- `OPS_ONLY`

Config-controlled command access:

- `addAccess`
- `linkAccess`
- `unlinkAccess`
- `removeAccess`
- `renameAccess`
- `listAccess`
- `infoAccess`

`/enderlink reload` always remains operator/admin only.

### With LuckPerms

If `luckPerms` is `true` and LuckPerms is installed, EnderLink uses permission nodes.

Core nodes:

- `enderlink.use`
- `enderlink.admin`
- `enderlink.add`
- `enderlink.link`
- `enderlink.unlink`
- `enderlink.remove`
- `enderlink.rename`
- `enderlink.list`
- `enderlink.info`
- `enderlink.reload`

Limit nodes:

- `enderlink.limit.blocks.<n>`
- `enderlink.limit.links.<n>`
- `enderlink.limit.blocks.unlimited`
- `enderlink.limit.links.unlimited`

How limits work:

- The config value is the default limit
- A higher permission node can raise that limit
- Lower permission nodes do not reduce the config fallback
- `unlimited` removes the limit entirely

Example:

- Config says `maxPadsPerPlayer = 20`
- Player has `enderlink.limit.blocks.10`
- Effective limit is still `20`

Example:

- Config says `maxPadsPerPlayer = 20`
- Player has `enderlink.limit.blocks.50`
- Effective limit is `50`

---

## ☝🏻 Ownership

- Each pad stores the owner's UUID, not the player's name
- Player names can change safely
- Players can use each other's teleports
- Non-admin management actions are ownership-aware
- Admins can manage all pads

---

## 📃 Data Files

EnderLink stores everything under:

```text
config/enderlink/
```

Files:

- `config/enderlink/enderlink.json`
  Main mod configuration

- `config/enderlink/teleports.json`
  Stored teleport pad and link data


---

## ⚙️ Configuration

EnderLink writes a commented config file to:

```text
config/enderlink/enderlink.json
```

### Config Options

- `teleportBlocks`
  List of block ids that can act as teleport pads.
  Default: `minecraft:gold_block`, `minecraft:lapis_block`

- `chargeTicks`
  Warmup time before teleporting.
  `20 ticks = 1 second`

- `safetyEnabled`
  If `true`, EnderLink blocks unsafe destinations.

- `particlesEnabled`
  Enables teleport particles.

- `particleType`
  Particle id used during warmup and arrival.

- `warmupParticleCount`
  How many particles spawn while charging.

- `arrivalBurstParticleCount`
  How many particles spawn immediately on arrival.

- `arrivalTrailParticleCount`
  How many particles spawn each tick during the short arrival particle trail.

- `arrivalParticleDurationTicks`
  How long the extra arrival particle trail lasts after teleporting.

- `soundEnabled`
  Enables arrival sound.

- `soundEvent`
  Sound event id played on arrival.

- `soundVolume`
  Volume for the arrival sound.

- `soundPitch`
  Pitch for the arrival sound.

- `allowedDimensions`
  List of dimension ids allowed to participate in teleports.
  You can use shorthand like `overworld` or full ids like `minecraft:overworld`.

- `luckPerms`
  Enables LuckPerms integration if the LuckPerms mod is installed.

- `maxPadsPerPlayer`
  Default maximum amount of teleport pads per player.

- `maxLinksPerPlayer`
  Default maximum amount of links per player.

- `addAccess`
- `linkAccess`
- `unlinkAccess`
- `removeAccess`
- `renameAccess`
- `listAccess`
- `infoAccess`
  Command access mode when LuckPerms is disabled.
  Valid values: `EVERYONE`, `OPS_ONLY`

### Example Config

```json
{
  "teleportBlocks": [
    "minecraft:gold_block",
    "minecraft:lapis_block"
  ],
  "chargeTicks": 60,
  "safetyEnabled": true,
  "particlesEnabled": true,
  "particleType": "minecraft:witch",
  "warmupParticleCount": 8,
  "arrivalBurstParticleCount": 24,
  "arrivalTrailParticleCount": 6,
  "arrivalParticleDurationTicks": 60,
  "soundEnabled": true,
  "soundEvent": "minecraft:entity.illusioner.mirror_move",
  "soundVolume": 0.8,
  "soundPitch": 1.0,
  "allowedDimensions": [
    "minecraft:overworld",
    "minecraft:the_nether",
    "minecraft:the_end"
  ],
  "luckPerms": false,
  "maxPadsPerPlayer": 10,
  "maxLinksPerPlayer": 5,
  "addAccess": "OPS_ONLY",
  "linkAccess": "OPS_ONLY",
  "unlinkAccess": "OPS_ONLY",
  "removeAccess": "OPS_ONLY",
  "renameAccess": "OPS_ONLY",
  "listAccess": "EVERYONE",
  "infoAccess": "EVERYONE"
}
```

---

## ❗Compatibility With Elevator

EnderLink includes a compatibility guard for the `Elevator` mod.

If the same block is configured for both Elevator and EnderLink:

- EnderLink logs a warning
- EnderLink ignores that overlapping block
- `/enderlink add` is blocked on that block
- existing EnderLink pads on that overlapping block are treated as invalid and removed

This prevents the same block from acting as both an Elevator block and an EnderLink pad.

---

## ❌ Troubleshooting

### "Stand on a configured EnderLink block first."

Possible causes:

- the block is not in `teleportBlocks`
- the config was changed but not reloaded
- the block is in conflict with Elevator and EnderLink is ignoring it

### "This block is configured for Elevator too..."

The same block appears in both EnderLink and Elevator configs.
Remove the overlap from one of the mods, then reload or restart.

### "This teleport is blocked by the allowedDimensions config."

The source or destination dimension is not allowed.
Check `allowedDimensions`.

### "Teleport failed because the destination is blocked."

The arrival location is not safe enough for the player.
Make sure the destination has enough space above the teleport block.

### Existing pads disappeared after reload

EnderLink automatically removes invalid stored pads, for example when:

- the block was broken
- the block is no longer a configured teleport block
- the block now conflicts with Elevator
- the stored destination data became invalid

---

## 📦 Installation

| Platform | Link |
|----------|------|
| GitHub | [Releases](https://github.com/SwordfishBE/EnderLink/releases) |
| Modrinth | [EnderLink](https://modrinth.com/mod/enderlinktp) |
| CurseForge | [EnderLink](https://www.curseforge.com/minecraft/mc-mods/enderlink) |

1. Download the latest `EnderLink` JAR from your preferred platform above.
2. Download the latest compatible `Fabric API` version.
3. Place both JARs in your server's `mods/` folder.
4. Start Minecraft.

---

## 🧱 Building from Source

```bash
git clone https://github.com/SwordfishBE/EnderLink.git
cd EnderLink
chmod +x gradlew
./gradlew build
# Output: build/libs/enderlink-<version>.jar
```

---

## 📄 License

Released under the [AGPL-3.0 License](LICENSE).
