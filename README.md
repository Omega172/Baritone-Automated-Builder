# Baritone Automated Builder

Standalone Fabric client mod for Minecraft 26.3 that drives Baritone's builder fully autonomously, including resupply from chests, barrels and shulker boxes, with an optional printer that places blocks in reach without pathing.

## Requirements

- Minecraft 26.3 with Fabric Loader and Fabric API
- Baritone for 26.3 (Meteor's build or the cabaletta 26.3 branch)
- JDK 25 or newer
- Optional: Litematica and malilib to build from a placed schematic
- Optional: Meteor Client with the Meteor Litematica Printer addon to use its printer

## Commands

| Command | Purpose |
|---|---|
| `/autobuild build <file>` | Build a schematic from the `schematics` folder with the origin at your feet |
| `/autobuild litematica [n]` | Build the n-th placed Litematica schematic (default 1) at its placed position |
| `/autobuild stop` | Stop building and cancel all pathing |
| `/autobuild status` | Print the current state and blocks left |
| `/autobuild printer` | Toggle the printer |
| `/autobuild config [key] [value]` | List or change settings |
| `/autobuild link` | Toggle indexing of every storage you open |
| `/autobuild storages` | List indexed storages |
| `/autobuild forget` | Clear the storage index |
| `/autobuild sethome` | Set the position to return to when stuck |
| `/autobuild setdump` | Set the storage you are looking at as the dump chest for returned shulker boxes |
| `/autobuild cleardump` | Clear the dump chest |
| `/autobuild returnshulkers` | Return every shulker box you are carrying to where it came from (or the dump chest) |
| `/autobuild help` | Print this list and every setting |

Changing a setting prints `Set <key> to <value> | Was: <old value>`.

Shulker box return is automatic and configurable. `return-empty-shulkers` (default on) returns empty boxes during each resupply trip, `return-shulkers-at-end` (default on) returns everything you carry when the build finishes, and `shulker-return` chooses `origin` (the slot each box came from) or `dump`. Turn both return settings off to keep carrying boxes and return them yourself with `/autobuild returnshulkers`.

For testing with the included OmegaLogo schematic, run `/function baritone_automated_builder:omegalogo_kit` in a world with cheats to get four shulker boxes holding every material the build needs.

Set `/autobuild config shulker-return dump` to send returned shulker boxes to the dump chest instead of their origin.

## Printer

The printer places missing blocks that are within reach while Baritone keeps pathing and handling everything else. It reproduces block direction by simulating vanilla placement and turning the player before it clicks, so stairs, glazed terracotta, logs, slabs, observers and similar blocks come out facing the right way. Blocks Baritone reports as missing while the item is in the inventory are placed by the printer after the controller walks next to them.

| Setting | Values | Default |
|---|---|---|
| `printer` | on, off | off |
| `printer-delay` | ticks between placements | 2 |
| `printer-blocks-per-tick` | 1 to 100 | 1 |
| `printer-range` | 1.0 to 6.0 | 4.4 |
| `printer-sort` | none, nearest, furthest, top_down, down_top | nearest |
| `printer-air-place` | off, auto, on | auto |
| `printer-backend` | auto, builtin, meteor | auto |
| `printer-rotate`, `printer-swing`, `printer-dirt-as-grass` | on, off | on, off, on |

Air place targets the air position itself when no neighbouring block can be clicked. In `auto` mode the printer checks that the server accepted each air placement and switches it off for the build after three rejections. `on` always tries and `off` never does.

With the `auto` backend, a Litematica build uses the Meteor Litematica Printer module when Meteor Client and that addon are loaded. The module is toggled by reflection, so Meteor is not a dependency.

## How it works

The controller loads the schematic itself, tracks which blocks are still missing, and starts Baritone's builder through its API. When an item runs out it cancels the build, runs a queue of timed steps (path, open, transfer, close, and for shulkers place, open, transfer, break, pick up), then restarts the build. Every step has a timeout and a failure path.

## Building

Run `Build.bat` or `Build.sh`. The jar lands in `Build/Release`. `Run.bat` starts a dev client with Litematica loaded.

## Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for placement direction, resupply and Baritone problems and their fixes.

## Credits

See [CREDITS.md](CREDITS.md).
