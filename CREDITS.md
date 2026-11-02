# Credits

This project is licensed under GPL-3.0 (see `LICENSE`). It builds on the work below.

## Code and design used as reference

- **Meteor Litematica Printer** by kkllffaa, GPL-3.0, https://github.com/kkllffaa/meteor-litematica-printer. The printer feature (`core/Printer.java`) reimplements its ideas: placing missing blocks in reach against a neighbouring face, axis and slab or half handling through the click position, air placing against the target position, sort modes, creative slot filling and the setting names. The code was rewritten to work from this mod's build session without Litematica or Meteor classes.
- **BetterBaritoneBuild** from OmegaWare-Addons by Me(Omega172), https://github.com/Omega172/OmegaWare-Addons. The idea of linked storage, home position and resupply came from this earlier module, which this repository replaces.

## Libraries and mods used

- **Baritone** by leijurv and the Baritone contributors, https://github.com/cabaletta/baritone, and the Meteor Development build at https://github.com/MeteorDevelopment/baritone. Used through its API for pathing and building. See its repository for licence terms.
- **Litematica** by masa and sakura-ryoko, https://github.com/sakura-ryoko/litematica, and **malilib**, https://github.com/sakura-ryoko/malilib. Used to read placed schematics. See their repositories for licence terms.
- **Fabric Loader, Fabric API and Loom** by FabricMC, https://fabricmc.net.
- **Meteor Client** by the Meteor Development team, https://github.com/MeteorDevelopment/meteor-client. Not a dependency. Its printer module is toggled by reflection when present.
- **Minecraft** by Mojang Studios.
