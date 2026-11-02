# Troubleshooting

Problems found while testing the builder in game, what caused them and how they were fixed. If you see the same symptom, start here.

## Printer and block direction

### A directional block ends up facing the wrong way, Baritone breaks it and says it is missing materials

Symptom: a stair, glazed terracotta, observer or similar block is placed with the wrong facing. Baritone treats it as wrong, breaks it by hand and the item is lost or left on the ground.

Causes and fixes:

- The printer used to skip every block with a facing property and leave them to Baritone. It now simulates vanilla placement (`getStateForPlacement`) for each candidate click point and player rotation, picks the first one that produces the wanted state, turns the player and then clicks.
- Blocks differ in what they read. Glazed terracotta, stairs and logs read the body yaw. Observers, dispensers, droppers and pistons read the head rotation, which the server only updates a couple of ticks after the rotation packet. The printer therefore sends the rotation, waits 3 ticks and clicks on a later tick. If you see single oriented blocks wrong on a laggy server, keep `printer-rotate` on and do not lower the wait.
- Keep `printer-rotate` set to `true`. With it off the printer cannot turn the player and can only place blocks that happen to come out right.
- The printer verifies every placement and stops retrying a position after two wrong results, so it cannot loop placing the same wrong block.

### Oriented blocks never get placed when `printer-blocks-per-tick` is above 1

Cause: with several placements per tick each oriented block turned the player to its own rotation and overwrote the previous one, so the player never held the right rotation at click time.

Fix: the tick stops as soon as the printer has turned the player to place an oriented block. If you ever see the printer busy but not placing, set `printer-blocks-per-tick` back to 1 and report it.

### Baritone says "Missing materials for at least ... facing=north" but the item is in the inventory

Cause: Baritone only counts an oriented block as placeable if it can produce that exact state from where the player stands right now. It then pauses with "Unable to do it".

Fix with the printer on: when Baritone is paused while we hold the item, the controller walks to a spot within reach of the nearest such block that is not inside the block's own cell or the cell above it, and lets the printer place it (the "placing it with the printer" chat line). It tries each position 3 times. Standing in the target cell used to leave the printer unable to place the block, so the player has to be moved out of it.

With the printer off the mod never turns it on and never uses it. It moves the player a few blocks away, one side of the block at a time for up to 4 tries, and restarts the build so Baritone can try from a different angle. If that also fails the normal stuck handling applies.

### The player broke the block under itself and fell into a hole

Cause: Baritone replaced a wrongly oriented block it was standing on. The block above the hole could then no longer be placed from where the player ended up.

Fix: the same assist path walks the player back out and places the block with the printer.

## Baritone fighting the mod

### PlaceShulker fails over and over, or the held item keeps changing

Cause: while `allowInventory` is on, Baritone's inventory behavior runs every tick and swaps a pickaxe (or throwaway block) into the hotbar. It undid the swap that put the shulker box in the hand.

Fix: the controller turns `allowInventory` off whenever it runs its own steps (resupply, shulker place and pick up, printer assist) and on only while Baritone is building.

### Mining a shulker box never finishes

Vanilla resets block breaking every tick unless the attack key is held. The break step holds the attack key and aims at the box. You still need a tool that can break it in reasonable time; a shulker box breaks by hand but slowly.

## Resupply

- Shortage is detected as: Baritone paused or not active and an item still needed has a count of 0. Oriented blocks that Baritone reports as missing while the count is above 0 are not a shortage, see the assist above.
- Quick move takes whole stacks. A `max-stacks-per-item` of 1 means one full stack per visit.
- The player always keeps at least one free slot so a placed shulker box can be picked up again. Raise `reserved-slots` if the inventory is crowded.
- A placed shulker box spot that rejects the click three times is remembered as bad and another spot is tried.
- The hotbar swap for the shulker box is retried up to 6 times before the step fails.
- The failure counter resets every time the build makes progress, so unrelated hiccups over a long build do not add up to an abort.
- `stuck-seconds` defaults to 20. An old saved `config/baritone-automated-builder/config.json` keeps whatever value was saved before, so edit that file or use `/autobuild config stuck-seconds 20`.

## Storage

- Double chests are indexed once. Both halves resolve to the half with the lower position, so linking either half updates the same entry.
- Recognised storage: chests, trapped chests, barrels, shulker boxes, hoppers, droppers, dispensers and ender chests. Ender chests share one inventory per player, so indexing two of them shows the same contents twice.
- Link mode and `/autobuild setdump` ignore blocks that are not storage (furnaces and similar).
- `/autobuild config shulker-return dump` sends returned shulker boxes to the dump chest. If no dump chest is set it falls back to the chests they came from and says so in chat.

## Meteor Client printer interop

- With Meteor Client and the meteor-litematica-printer addon installed, `printer-backend auto` hands Litematica builds to the Meteor printer module and turns it off again at the end. Use `printer-backend builtin` to always use the built-in printer.
- The addon's `printing-range` defaults to 2 blocks. If the Meteor printer is on but places nothing, the blocks are out of that range. Raise it in Meteor's module settings. The built-in printer uses `printer-range` instead.
- The addon turns itself off when no Litematica schematic is loaded.
- kkllffaa's addon had no 26.3 release when this was tested. It was ported locally (Minecraft 26.3, the same Litematica and malilib as this mod, `Items.PINK_CARPET` replaced, `ServerboundSwingPacket` and `Tuple` replaced, `new BlockPos(pos)` replaced with `pos.immutable()`).
- The Meteor printer placed 12 of 16 blocks per layer on its own in the test because of its range and line of sight rules, and the full build with the Meteor backend completed all 32 blocks.

## Development and gametests

- To run the Meteor scenario pass `-Pmeteor` to Gradle, and add `-PmeteorPrinterJar=<path to the addon jar>` for the printer addon, then set `BUILDER_TEST=meteor`.

- The gametest task ends with exit code -8 and a "Watchdog (Client shutdown from post-main)" report after the tests finish. That is the game shutting down slowly, not a failure. Judge the run by the `[Test] PASS` and `ALL PASSED` lines.
- Do not start a second gametest while the first game window is still open. The new run cannot open `run/gametest.log` and never starts.
- Settings persist between scenarios in one run (for example `printer-blocks-per-tick`). Bugs that only show when scenarios run together usually come from that.
- `/fill` refuses regions over 32768 blocks. A large platform has to be built in slices or the player falls into the void and dies.
- Ice and slippery blocks can carry the player off a platform in tests. Build the platform bigger than the build.
- Use `BUILDER_TEST` to pick scenarios, for example `BUILDER_TEST=orient,cycle`.
