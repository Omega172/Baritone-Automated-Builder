# Baritone Automated Builder

Standalone Fabric client mod for Minecraft that drives Baritone's builder fully autonomously, including resupply from chests, barrels and shulker boxes, with an optional printer that places blocks in reach without pathing.

## Demo

Watch the basics and a full build here: https://youtu.be/NUockEhBvDw

## Requirements

- The Minecraft version the release was built for, shown at the end of the jar name (`+<Minecraft version>`), with Fabric Loader and [Fabric API](https://modrinth.com/mod/fabric-api)
- [Baritone](https://github.com/cabaletta/baritone) for that same Minecraft version (the [Meteor Client](https://meteorclient.com/) build or the matching cabaletta branch)
- Only to build from source: a JDK new enough for that Minecraft version
- Optional: [Litematica](https://modrinth.com/mod/litematica) and [malilib](https://modrinth.com/mod/malilib) to build from a placed schematic
- Optional: [Meteor Client](https://meteorclient.com/) with the [Meteor Litematica Printer](https://github.com/kkllffaa/meteor-litematica-printer) addon to use its printer instead. The mod has its own built-in printer, so neither is needed

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
| `/autobuild unlink` | Remove the storage you are looking at from the index |
| `/autobuild forget` | Clear the whole storage index |
| `/autobuild sethome` | Set the position to return to when stuck |
| `/autobuild setdump` | Set the storage you are looking at as the dump chest for returned shulker boxes |
| `/autobuild cleardump` | Clear the dump chest |
| `/autobuild returnshulkers` | Return every shulker box you are carrying to where it came from (or the dump chest) |
| `/autobuild help` | Print this list and every setting |

Storages stay up to date on their own. Opening an already linked storage refreshes it with its final contents when you close it, even with link mode off (link mode only controls whether new storages are added). Before a resupply trip the mod checks every loaded storage and removes any that no longer exist, and when a storage is outside the loaded area it first walks until the chunk loads and checks it before going on.

Changing a setting prints `Set <key> to <value> | Was: <old value>`.

Shulker box return is automatic and configurable. `return-empty-shulkers` (default on) returns empty boxes during each resupply trip, `return-shulkers-at-end` (default on) returns everything you carry when the build finishes, and `shulker-return` chooses `origin` (the slot each box came from) or `dump`. Turn both return settings off to keep carrying boxes and return them yourself with `/autobuild returnshulkers`.

For testing with the included OmegaLogo schematic, run `/function baritone_automated_builder:omegalogo_kit` in a world with cheats to get four shulker boxes holding every material the build needs.

Set `/autobuild config shulker-return dump` to send returned shulker boxes to the dump chest instead of their origin.

## Printer

The printer places missing blocks that are within reach while Baritone keeps pathing and handling everything else. It reproduces block direction by simulating vanilla placement and turning the player before it clicks, so stairs, glazed terracotta, logs, slabs, observers and similar blocks come out facing the right way. With the printer on, blocks Baritone reports as missing while the item is in the inventory are placed by the printer after the controller walks next to them. With the printer off the mod never turns it on by itself. It moves the player a few blocks away so Baritone can try again from another angle.

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
