# STEMCraft Geyser Extension

[![Build](https://github.com/STEMMechanics/STEMCraft-Geyser-Extension/actions/workflows/build.yml/badge.svg)](https://github.com/STEMMechanics/STEMCraft-Geyser-Extension/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/STEMMechanics/STEMCraft-Geyser-Extension)](https://github.com/STEMMechanics/STEMCraft-Geyser-Extension/releases)
[![License](https://img.shields.io/github/license/STEMMechanics/STEMCraft-Geyser-Extension)](LICENSE)

STEMCraft's collection of Geyser compatibility features, beginning with
resource-pack-backed rendering of Java block display entities for Bedrock players.

The extension preserves native displays for Java clients while translating them
for Bedrock clients connected through Geyser. Item display support and additional
cross-platform visual features are planned.

## Features

- Displays vanilla Java block display entities to Bedrock players.
- Supports translation, independent XYZ scale, left/right rotation and movement.
- Bundles and automatically serves the required Bedrock resource pack.
- Provides live global and per-block calibration commands.
- Supports per-block scale and XYZ offset profiles for special Bedrock models.
- Includes a reference rig for aligning displays against real blocks.
- Migrates legacy properties and split YAML override formats.

## Requirements

- Java 21 or newer.
- A compatible Geyser 2.11.x build.
- Bedrock 26.x clients with server resource packs enabled.

This project compiles against Geyser core because entity factories and Java
metadata translation are not currently exposed by the public extension API.
Nearby Geyser development builds can introduce binary incompatibilities; use the
version declared in `gradle.properties` when building.

## Installation

1. Download the latest JAR from [GitHub Releases](https://github.com/STEMMechanics/STEMCraft-Geyser-Extension/releases).
2. Place it in Geyser's `extensions` directory.
3. Remove older copies of this extension.
4. Restart Geyser or the Minecraft server.
5. Fully reconnect Bedrock clients and accept the generated resource pack.

The extension creates `extensions/stemcraftge/config.yml` on first start.

## Configuration

```yaml
held-item-scale: 2.7

rotation:
  x: -20.0
  y: -135.0
  z: 0.0

offset:
  x: 0.25
  y: 0.575
  z: 0.25

block-overrides:
  minecraft:chest:
    scale: 2.6
    x: -0.4
    y: 0.1
    z: 0.9
  minecraft:trapped_chest:
    scale: 2.6
    x: -0.4
    y: 0.1
    z: 0.9
  minecraft:ender_chest:
    scale: 2.6
    x: -0.4
    y: 0.1
    z: 0.9
```

Missing per-block values default to scale `1.0` and offset `0.0`. Calibration
values are rounded to three decimal places when adjusted or saved.

## Commands

The permission `stemcraftge.command.calibrate` controls calibration commands and
defaults to allowed. Restrict it to trusted administrators in production.

```text
/stemcraftge calibrate status
/stemcraftge calibrate reload
/stemcraftge calibrate reset
/stemcraftge calibrate save
/stemcraftge calibrate debug
```

Adjust global calibration live:

```text
/stemcraftge calibrate add scale 0.05
/stemcraftge calibrate set ry -135
/stemcraftge calibrate add x 0.01
```

Create and tune a per-block reference display:

```text
/stemcraftge calibrate test minecraft:chest
/stemcraftge calibrate block minecraft:chest status
/stemcraftge calibrate block minecraft:chest set scale 2.6
/stemcraftge calibrate block minecraft:chest add x 0.01
/stemcraftge calibrate block minecraft:chest clear
/stemcraftge calibrate save
```

The test command overlays a display on a real diamond block three blocks east
of the player. It runs `setblock` and `summon` as the player, so the player must
have permission to use those commands.

Remove calibration displays with:

```mcfunction
/kill @e[type=minecraft:block_display,tag=stemcraftge_calibration]
```

## Building

```shell
./gradlew clean build
```

The release JAR is written to `build/libs/`.

## Limitations

- Bedrock renders blocks as held-item attachments rather than native Java block geometry.
- Special models such as chests can require per-block scale and offset profiles.
- Billboarding, brightness, shadows, glow and interpolation are not implemented.
- Multipart and 2D block items can differ visually from Java Edition.
- Quaternion rotations are converted to Euler bone rotations and can differ near gimbal lock.
- The translated entities have no Bedrock collision.

## Contributing

Bug reports, documentation improvements and pull requests are welcome. Read
[CONTRIBUTING.md](CONTRIBUTING.md) before beginning a substantial change.

Please report security issues privately as described in [SECURITY.md](SECURITY.md).

## Related projects

- [STEMCraft](https://github.com/STEMMechanics/STEMCraft)
- [STEMCraft Console](https://github.com/STEMMechanics/STEMCraft-Console)
- [Geyser](https://github.com/GeyserMC/Geyser)

## License

Released under the [MIT License](LICENSE).
