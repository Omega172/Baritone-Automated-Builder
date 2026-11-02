package xyz.omegaware.builder.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import xyz.omegaware.builder.core.BuildSession;
import xyz.omegaware.builder.core.Config;
import xyz.omegaware.builder.core.Controller;
import xyz.omegaware.builder.core.Printer;
import xyz.omegaware.builder.storage.ShulkerLedger;
import xyz.omegaware.builder.storage.StorageIndex;
import xyz.omegaware.builder.util.Inv;

import java.util.Map;

public class BuilderGameTest implements FabricClientGameTest {
    private static final BlockPos s_origin = new BlockPos(-10, 100, -10);
    private static final int iWidth = 6;
    private static final int iLength = 6;
    private static final int iTimeoutTicks = 20 * 240;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            try {
                prepareArena(ctx, world);
                if (selected("chest")) chestResupply(ctx, world);
                if (selected("shulker")) shulkerResupply(ctx, world);
                if (selected("mixed")) mixedResupply(ctx, world);
                if (selected("printer")) printerPlacement(ctx, world);
                if (selected("air")) airPlacement(ctx, world);
                if (selected("litematica")) litematicaBuild(ctx, world);
                if (selected("meteor")) meteorInterop(ctx, world);
            if (selected("storage")) storageTypes(ctx, world);
                if (selected("orient")) orientation(ctx, world);
                if (selected("airorient")) airOrientation(ctx, world);
                if (selected("cycle")) shulkerCycle(ctx, world, false);
                if (selected("dump")) shulkerCycle(ctx, world, true);
            if (selected("noreturn")) shulkerNoReturn(ctx, world);
            if (selected("multishulker")) multiItemShulker(ctx, world);
            if (selected("help")) helpCommand(ctx, world);
                if (selected("kit")) omegaLogoKit(ctx, world);
            if (selected("omegalogo")) litematicaFile(ctx, world);
                System.out.println("[Test] ALL PASSED");
            } finally {
                world.getServer().runCommand("tellraw @a {\"text\":\"Test finished. Closing in a few seconds.\",\"color\":\"green\"}");
                ctx.waitTicks(20 * 6);
            }
        }
    }

    private static boolean selected(String strName) {
        String strFilter = System.getenv("BUILDER_TEST");
        return strFilter == null || strFilter.isBlank() || java.util.Arrays.asList(strFilter.split(",")).contains(strName);
    }

    private static void mixedResupply(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos chestA = new BlockPos(4, 100, -5);
        BlockPos chestB = new BlockPos(-4, 100, -5);
        BlockPos chestC = new BlockPos(0, 100, -8);
        BlockPos origin = new BlockPos(-6, 100, 2);

        for (BlockPos chest : new BlockPos[] {chestA, chestB, chestC}) {
            server.runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " minecraft:chest");
        }
        server.runCommand("item replace block 4 100 -5 container.0 with minecraft:stone 64");
        server.runCommand("item replace block -4 100 -5 container.5 with minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:oak_planks\",count:64}},{slot:1,item:{id:\"minecraft:oak_planks\",count:64}},{slot:2,item:{id:\"minecraft:cobblestone\",count:64}},{slot:3,item:{id:\"minecraft:cobblestone\",count:64}}]]");
        server.runCommand("item replace block 0 100 -8 container.2 with minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:spruce_planks\",count:18}}]]");
        server.runCommand("give @p minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:bricks\",count:18}}]]");
        ctx.waitTicks(20);

        var shulkerItem = net.minecraft.world.item.Items.SHULKER_BOX;
        ctx.runOnClient(mc -> {
            ShulkerLedger.get().clear();
            Config.set("max-stacks-per-item", "1");
            StorageIndex.get().record(chestA, Map.of(Items.STONE, 64));
            StorageIndex.get().record(chestB, Map.of(shulkerItem, 1), java.util.List.of(new StorageIndex.ShulkerInfo(5, Map.of(Items.OAK_PLANKS, 128, Items.COBBLESTONE, 128))));
            StorageIndex.get().record(chestC, Map.of(shulkerItem, 1), java.util.List.of(new StorageIndex.ShulkerInfo(2, Map.of(Items.SPRUCE_PLANKS, 18))));
        });

        var schematic = new StripeSchematic(6, 3,
            Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState());
        ctx.runOnClient(mc -> Controller.get().start("mixed", schematic, origin));
        int iWaited = ctx.waitFor(mc -> !Controller.get().isRunning(), iTimeoutTicks);
        ctx.runOnClient(mc -> Config.set("max-stacks-per-item", "9"));
        System.out.println("[Test] mixed stopped after " + iWaited + " ticks, state " + Controller.get().state());
        ctx.takeScreenshot("mixed-resupply-end");

        assertTrue(Controller.get().state() == Controller.State.FINISHED, "mixed scenario ended in " + Controller.get().state());

        int[] aCounts = server.computeOnServer(srv -> {
            int[] counts = new int[5];
            net.minecraft.world.level.block.Block[] aBlocks = {Blocks.STONE, Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.BRICKS};
            for (int i = 0; i < 5; i++) {
                for (int dx = 0; dx < 3; dx++) {
                    for (int z = 0; z < 6; z++) {
                        if (srv.overworld().getBlockState(origin.offset(i * 3 + dx, 0, z)).is(aBlocks[i])) counts[i]++;
                    }
                }
            }
            return counts;
        });
        for (int i = 0; i < 5; i++) assertTrue(aCounts[i] == 18, "mixed scenario stripe " + i + " has " + aCounts[i] + " of 18");

        var chestBStack = server.computeOnServer(srv -> ((net.minecraft.world.Container) srv.overworld().getBlockEntity(chestB)).getItem(5).copy());
        var chestBContents = Inv.shulkerContents(chestBStack);
        System.out.println("[Test] chest B slot 5 contents " + chestBContents);
        assertTrue(Inv.isShulker(chestBStack), "shulker was not returned to chest B slot 5");
        assertTrue(chestBContents.getOrDefault(Items.OAK_PLANKS, 0) == 64 && chestBContents.getOrDefault(Items.COBBLESTONE, 0) == 64, "chest B shulker has wrong contents " + chestBContents);

        var chestCStack = server.computeOnServer(srv -> ((net.minecraft.world.Container) srv.overworld().getBlockEntity(chestC)).getItem(2).copy());
        assertTrue(Inv.isShulker(chestCStack) && Inv.shulkerContents(chestCStack).isEmpty(), "empty shulker was not returned to chest C slot 2");

        int iShulkersInInventory = ctx.computeOnClient(mc -> {
            int iCount = 0;
            for (int i = 0; i < Inv.iMainSize; i++) if (Inv.isShulker(mc.player.getInventory().getItem(i))) iCount++;
            return iCount;
        });
        assertTrue(iShulkersInInventory == 1, "expected only the player own shulker in the inventory, found " + iShulkersInInventory);
        assertTrue(ShulkerLedger.get().all().isEmpty(), "ledger still tracks " + ShulkerLedger.get().all().size() + " shulkers");
        System.out.println("[Test] PASS mixed");
    }

    private static void bigArena(TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("forceload add -64 -64 64 64");
        server.runCommand("fill -60 99 -60 60 99 60 minecraft:dirt");
        for (int z = -60; z < 60; z += 30) {
            server.runCommand("fill -60 100 " + z + " 60 108 " + (z + 29) + " minecraft:air");
        }
    }

    private static void airPlacement(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        BlockPos target = new BlockPos(1, 104, 1);
        world.getServer().runCommand("give @p minecraft:stone 64");
        ctx.waitTicks(10);

        ctx.runOnClient(mc -> {
            Config.set("printer", "true");
            Config.set("printer-delay", "0");
            Config.set("printer-air-place", "auto");
        });
        var session = ctx.computeOnClient(mc -> new BuildSession("air", new CubeSchematic(1, 1, 1, Blocks.STONE.defaultBlockState()), target));
        ctx.getInput().lookAt(target);
        ctx.takeScreenshot("air-place-before");
        for (int i = 0; i < 30; i++) {
            ctx.runOnClient(mc -> {
                session.tick(mc.level);
                Printer.tick(session);
            });
            ctx.waitTick();
        }
        ctx.takeScreenshot("air-place-after");
        ctx.runOnClient(mc -> Config.set("printer", "false"));

        boolean bPlaced = world.getServer().computeOnServer(server -> server.overworld().getBlockState(target).is(Blocks.STONE));
        System.out.println("[Test] air place placed=" + bPlaced + " active=" + Printer.airPlaceActive());
        assertTrue(bPlaced, "air place did not place a floating block");
        System.out.println("[Test] PASS air place");
    }

    private static void litematicaFile(ClientGameTestContext ctx, TestSingleplayerContext world) {
        java.nio.file.Path dir = java.nio.file.Path.of("..", "..", "..", "run").toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(dir.resolve("OmegaLogo.litematic"))) {
            System.out.println("[Test] SKIP litematica file, no OmegaLogo.litematic in " + dir);
            return;
        }
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos origin = new BlockPos(-6, 100, 6);
        bigArena(world);
        ctx.waitTicks(40);

        ctx.runOnClient(mc -> {
            DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().forEach(p -> DataManager.getSchematicPlacementManager().removeSchematicPlacement(p));
            LitematicaSchematic schematic = LitematicaSchematic.createFromFile(dir, "OmegaLogo.litematic");
            assertTrue(schematic != null, "OmegaLogo.litematic did not load");
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, origin, "OmegaLogo", true, true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
        });
        server.runCommand("tp @p -6 101 2");
        ctx.runOnClient(mc -> mc.player.setXRot(35.0f));
        ctx.waitTicks(120);
        ctx.getInput().lookAt(origin.offset(8, 0, 8));
        ctx.waitTicks(10);
        ctx.takeScreenshot("omegalogo-placement-rendered");

        server.runCommand("clear @p");
        server.runCommand("effect give @p minecraft:speed infinite 2 true");
        server.runCommand("effect give @p minecraft:haste infinite 1 true");
        boolean bStarted = ctx.computeOnClient(mc -> Controller.get().startLitematica(0));
        assertTrue(bStarted, "OmegaLogo build did not start");
        ctx.waitFor(mc -> Controller.get().isResolved(), 20 * 60);
        var totals = ctx.computeOnClient(mc -> Controller.get().needed());
        System.out.println("[Test] OmegaLogo needs " + totals);
        assertTrue(totals.getOrDefault(Items.COAL_BLOCK, 0) == 81, "coal block count " + totals.get(Items.COAL_BLOCK));
        assertTrue(totals.getOrDefault(Items.ICE, 0) == 1134, "ice count " + totals.get(Items.ICE));
        assertTrue(totals.getOrDefault(byId("minecraft:black_wool"), 0) == 40, "black wool count " + totals.get(byId("minecraft:black_wool")));
        assertTrue(totals.getOrDefault(Items.BLACKSTONE, 0) == 27 * 64 + 12 * 64 + 48, "blackstone count " + totals.get(Items.BLACKSTONE));
        assertTrue(totals.getOrDefault(byId("minecraft:light_blue_glazed_terracotta"), 0) == 30, "light blue glazed terracotta count");

        var loaded = ctx.computeOnClient(mc -> xyz.omegaware.builder.core.LitematicaSource.load(0));
        var aBlocks = new java.util.TreeMap<String, Integer>();
        var schem = loaded.schematic();
        for (int x = 0; x < schem.widthX(); x++) {
            for (int y = 0; y < schem.heightY(); y++) {
                for (int z = 0; z < schem.lengthZ(); z++) {
                    var state = schem.getDirect(x, y, z);
                    if (state == null || state.isAir()) continue;
                    aBlocks.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() + (state.getBlock().asItem() == Items.AIR ? " (NO ITEM)" : ""), 1, Integer::sum);
                }
            }
        }
        System.out.println("[Test] OmegaLogo schematic size " + schem.widthX() + "x" + schem.heightY() + "x" + schem.lengthZ() + ", blocks " + aBlocks);
        if (System.getenv("BUILDER_TOTALS_ONLY") != null) {
            Controller.get().stop();
            return;
        }

        Controller.get().stop();
        server.runCommand("give @p minecraft:netherite_pickaxe[enchantments={efficiency:5},unbreakable={}]");
        int iSlot = 0;
        int iChest = 0;
        BlockPos[] aChests = {new BlockPos(0, 100, -8), new BlockPos(3, 100, -8), new BlockPos(6, 100, -8), new BlockPos(9, 100, -8)};
        Map<BlockPos, java.util.List<StorageIndex.ShulkerInfo>> aShulkers = new java.util.HashMap<>();
        for (BlockPos chest : aChests) {
            server.runCommand("setblock " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " minecraft:chest");
            aShulkers.put(chest, new java.util.ArrayList<>());
        }
        for (var entry : totals.entrySet()) {
            int iLeft = entry.getValue() + Math.max(16, entry.getValue() / 5);
            int iMax = new net.minecraft.world.item.ItemStack(entry.getKey()).getMaxStackSize();
            String strId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
            while (iLeft > 0) {
                StringBuilder strContainer = new StringBuilder();
                int iInBox = 0;
                for (int iStack = 0; iStack < 27 && iLeft > 0; iStack++) {
                    int iCount = Math.min(iLeft, iMax);
                    if (iStack > 0) strContainer.append(',');
                    strContainer.append("{slot:").append(iStack).append(",item:{id:\"").append(strId).append("\",count:").append(iCount).append("}}");
                    iInBox += iCount;
                    iLeft -= iCount;
                }
                BlockPos chest = aChests[iChest];
                server.runCommand("item replace block " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " container." + iSlot + " with minecraft:shulker_box[container=[" + strContainer + "]]");
                aShulkers.get(chest).add(new StorageIndex.ShulkerInfo(iSlot, Map.of(entry.getKey(), iInBox)));
                if (++iSlot == 27) {
                    iSlot = 0;
                    iChest++;
                }
            }
        }
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> aShulkers.forEach((pos, infos) -> {
            if (!infos.isEmpty()) StorageIndex.get().record(pos, Map.of(net.minecraft.world.item.Items.SHULKER_BOX, infos.size()), infos);
        }));

        ctx.runOnClient(mc -> {
            Config.set("printer", "true");
            Config.set("printer-delay", "0");
            Config.set("printer-blocks-per-tick", "4");
            Config.set("printer-air-place", "auto");
        });
        server.runCommand("tp @p -6 101 2");
        ctx.runOnClient(mc -> {
            mc.player.setXRot(35.0f);
            Controller.get().startLitematica(0);
        });
        int iWaited;
        try {
            iWaited = ctx.waitFor(mc -> !Controller.get().isRunning(), 20 * 60 * 12);
        } catch (AssertionError e) {
            iWaited = -1;
        }
        ctx.runOnClient(mc -> Config.set("printer", "false"));
        int iLeftOver = ctx.computeOnClient(mc -> Controller.get().needed().values().stream().mapToInt(Integer::intValue).sum());
        System.out.println("[Test] OmegaLogo stopped after " + iWaited + " ticks, state " + Controller.get().state() + ", blocks left " + iLeftOver);
        ctx.takeScreenshot("omegalogo-build-end");
        assertTrue(Controller.get().state() != Controller.State.ABORTED, "OmegaLogo aborted");
        assertTrue(Controller.get().state() == Controller.State.FINISHED || iLeftOver < 3000, "OmegaLogo made too little progress, left " + iLeftOver);
        System.out.println("[Test] PASS omegalogo");
    }

    private static void litematicaBuild(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos a = new BlockPos(-8, 100, 5);
        BlockPos b = new BlockPos(-5, 101, 8);
        server.runCommand("fill -8 100 5 -5 100 8 minecraft:stone");
        server.runCommand("fill -8 101 5 -5 101 8 minecraft:oak_planks");
        ctx.waitTicks(20);

        ctx.runOnClient(mc -> {
            AreaSelection selection = new AreaSelection();
            selection.addSubRegionBox(new Box(a, b, "area"), false);
            LitematicaSchematic schematic = LitematicaSchematic.createFromWorld(mc.level, selection, new LitematicaSchematic.SchematicSaveInfo(false, false), "Test", string -> {});
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, selection.getEffectiveOrigin(), "test", true, true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
        });
        server.runCommand("fill -8 100 5 -5 101 8 minecraft:air");
        ctx.waitTicks(80);
        ctx.takeScreenshot("litematica-placement-rendered");

        server.runCommand("give @p minecraft:stone 64");
        server.runCommand("give @p minecraft:oak_planks 64");
        ctx.waitTicks(10);
        boolean bStarted = ctx.computeOnClient(mc -> Controller.get().startLitematica(0));
        assertTrue(bStarted, "litematica build did not start");
        int iWaited = ctx.waitFor(mc -> !Controller.get().isRunning(), iTimeoutTicks);
        System.out.println("[Test] litematica stopped after " + iWaited + " ticks, state " + Controller.get().state());
        ctx.takeScreenshot("litematica-build-end");

        assertTrue(Controller.get().state() == Controller.State.FINISHED, "litematica scenario ended in " + Controller.get().state());
        int[] aCounts = server.computeOnServer(srv -> {
            int iStone = 0;
            int iPlanks = 0;
            for (int x = -8; x <= -5; x++) {
                for (int z = 5; z <= 8; z++) {
                    if (srv.overworld().getBlockState(new BlockPos(x, 100, z)).is(Blocks.STONE)) iStone++;
                    if (srv.overworld().getBlockState(new BlockPos(x, 101, z)).is(Blocks.OAK_PLANKS)) iPlanks++;
                }
            }
            return new int[] {iStone, iPlanks};
        });
        assertTrue(aCounts[0] == 16 && aCounts[1] == 16, "litematica scenario rebuilt " + aCounts[0] + " stone and " + aCounts[1] + " planks of 16 each");
        System.out.println("[Test] PASS litematica");
    }

    private static void printerPlacement(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        BlockPos origin = new BlockPos(1, 100, -1);
        world.getServer().runCommand("give @p minecraft:stone 64");
        ctx.waitTicks(10);

        ctx.runOnClient(mc -> {
            Config.set("printer", "true");
            Config.set("printer-delay", "0");
            Config.set("printer-blocks-per-tick", "2");
        });
        var session = ctx.computeOnClient(mc -> new BuildSession("printer", new CubeSchematic(3, 1, 3, Blocks.STONE.defaultBlockState()), origin));
        ctx.getInput().lookAt(origin.offset(1, 0, 1));
        for (int i = 0; i < 40; i++) {
            ctx.runOnClient(mc -> {
                session.tick(mc.level);
                Printer.tick(session);
            });
            ctx.waitTick();
            if (i == 3) ctx.takeScreenshot("printer-placing");
        }
        ctx.runOnClient(mc -> Config.set("printer", "false"));
        ctx.takeScreenshot("printer-end");

        int iBlocks = world.getServer().computeOnServer(server -> {
            int iCount = 0;
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if (server.overworld().getBlockState(origin.offset(x, 0, z)).is(Blocks.STONE)) iCount++;
                }
            }
            return iCount;
        });
        assertTrue(iBlocks == 9, "printer placed " + iBlocks + " of 9");
        System.out.println("[Test] PASS printer");
    }

    private static net.minecraft.world.level.block.state.BlockState blockState(String strId) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(strId)).defaultBlockState();
    }

    private static net.minecraft.world.level.block.state.BlockState facing(String strId, net.minecraft.core.Direction direction) {
        var state = blockState(strId);
        var property = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
            ? net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING
            : net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        return state.setValue(property, direction);
    }

    private static boolean sameOrientation(net.minecraft.world.level.block.state.BlockState wanted, net.minecraft.world.level.block.state.BlockState got) {
        if (wanted.getBlock() != got.getBlock()) return false;
        for (var property : java.util.List.of(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            if (wanted.hasProperty(property) && wanted.getValue(property) != got.getValue(property)) return false;
        }
        for (var property : java.util.List.of(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF,
            net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)) {
            if (wanted.hasProperty(property) && wanted.getValue(property) != got.getValue(property)) return false;
        }
        return true;
    }

    private static void helpCommand(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("autobuild help"));
        ctx.waitTicks(10);
        ctx.takeScreenshot("help-command");
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("autobuild config printer-range 5"));
        ctx.waitTicks(10);
        ctx.takeScreenshot("config-set-message");
        ctx.runOnClient(mc -> Config.set("printer-range", "4.4"));
        System.out.println("[Test] PASS help command ran");
    }

    private static void multiItemShulker(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos chest = new BlockPos(4, 100, -5);
        BlockPos origin = new BlockPos(-4, 100, 2);

        server.runCommand("setblock 4 100 -5 minecraft:chest");
        server.runCommand("item replace block 4 100 -5 container.5 with minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:oak_planks\",count:64}},{slot:1,item:{id:\"minecraft:cobblestone\",count:64}}]]");
        ctx.waitTicks(20);

        var shulkerItem = net.minecraft.world.item.Items.SHULKER_BOX;
        ctx.runOnClient(mc -> {
            ShulkerLedger.get().clear();
            ShulkerLedger.iUseCycles = 0;
            StorageIndex.get().record(chest, Map.of(shulkerItem, 1), java.util.List.of(new StorageIndex.ShulkerInfo(5, Map.of(Items.OAK_PLANKS, 64, Items.COBBLESTONE, 64))));
        });
        var schematic = new StripeSchematic(16, 4, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        ctx.runOnClient(mc -> Controller.get().start("multishulker", schematic, origin));
        ctx.waitFor(mc -> !Controller.get().isRunning(), iTimeoutTicks);
        System.out.println("[Test] multishulker stopped in state " + Controller.get().state() + ", use cycles " + ShulkerLedger.iUseCycles);

        assertTrue(Controller.get().state() == Controller.State.FINISHED, "multishulker scenario ended in " + Controller.get().state());
        assertTrue(ShulkerLedger.iUseCycles == 1, "one shulker holding both items should be placed once, got " + ShulkerLedger.iUseCycles + " cycles");
        int[] aCounts = server.computeOnServer(srv -> {
            int[] counts = new int[2];
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 16; z++) {
                    var state = srv.overworld().getBlockState(origin.offset(x, 0, z));
                    if (state.is(Blocks.OAK_PLANKS)) counts[0]++;
                    if (state.is(Blocks.COBBLESTONE)) counts[1]++;
                }
            }
            return counts;
        });
        assertTrue(aCounts[0] == 64 && aCounts[1] == 64, "multishulker placed planks " + aCounts[0] + " cobblestone " + aCounts[1]);
        System.out.println("[Test] PASS multishulker");
    }

    private static void shulkerNoReturn(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos chest = new BlockPos(4, 100, -5);
        BlockPos origin = new BlockPos(-4, 100, 2);

        server.runCommand("setblock 4 100 -5 minecraft:chest");
        server.runCommand("item replace block 4 100 -5 container.5 with minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:oak_planks\",count:64}},{slot:1,item:{id:\"minecraft:oak_planks\",count:64}}]]");
        ctx.waitTicks(20);

        var shulkerItem = net.minecraft.world.item.Items.SHULKER_BOX;
        ctx.runOnClient(mc -> {
            ShulkerLedger.get().clear();
            Config.set("max-stacks-per-item", "1");
            Config.set("return-empty-shulkers", "false");
            Config.set("return-shulkers-at-end", "false");
            StorageIndex.get().record(chest, Map.of(shulkerItem, 1), java.util.List.of(new StorageIndex.ShulkerInfo(5, Map.of(Items.OAK_PLANKS, 128))));
        });
        var schematic = new StripeSchematic(16, 8, Blocks.OAK_PLANKS.defaultBlockState());
        ctx.runOnClient(mc -> Controller.get().start("noreturn", schematic, origin));
        ctx.waitFor(mc -> !Controller.get().isRunning(), iTimeoutTicks);
        ctx.runOnClient(mc -> {
            Config.set("max-stacks-per-item", "9");
            Config.set("return-empty-shulkers", "true");
            Config.set("return-shulkers-at-end", "true");
        });
        System.out.println("[Test] noreturn stopped in state " + Controller.get().state() + ", ledger " + ShulkerLedger.get().all().size());

        assertTrue(Controller.get().state() == Controller.State.FINISHED, "noreturn scenario ended in " + Controller.get().state());
        boolean bChestEmpty = server.computeOnServer(srv -> ((net.minecraft.world.Container) srv.overworld().getBlockEntity(chest)).getItem(5).isEmpty());
        assertTrue(bChestEmpty, "the shulker box was returned even though both return settings were off");
        int iCarried = ctx.computeOnClient(mc -> {
            int iCount = 0;
            for (int i = 0; i < Inv.iMainSize; i++) if (Inv.isShulker(mc.player.getInventory().getItem(i))) iCount++;
            return iCount;
        });
        assertTrue(iCarried == 1, "expected the shulker box to still be carried, found " + iCarried);
        assertTrue(ShulkerLedger.get().all().size() == 1, "ledger should still track the carried shulker box");
        System.out.println("[Test] PASS noreturn");
    }

    private static void omegaLogoKit(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        world.getServer().runCommand("execute as @p run function baritone_automated_builder:omegalogo_kit");
        ctx.waitTicks(20);
        var counts = ctx.computeOnClient(mc -> {
            Map<net.minecraft.world.item.Item, Integer> aTotals = new java.util.HashMap<>();
            int iShulkers = 0;
            for (int i = 0; i < Inv.iMainSize; i++) {
                var stack = mc.player.getInventory().getItem(i);
                if (!Inv.isShulker(stack)) continue;
                iShulkers++;
                Inv.shulkerContents(stack).forEach((item, iCount) -> aTotals.merge(item, iCount, Integer::sum));
            }
            aTotals.put(Items.SHULKER_BOX, iShulkers);
            return aTotals;
        });
        System.out.println("[Test] kit contents " + counts);
        assertTrue(counts.getOrDefault(Items.SHULKER_BOX, 0) == 4, "kit should give 4 shulker boxes");
        assertTrue(counts.getOrDefault(Items.BLACKSTONE, 0) >= 2544, "kit blackstone " + counts.get(Items.BLACKSTONE));
        assertTrue(counts.getOrDefault(Items.ICE, 0) >= 1134, "kit ice " + counts.get(Items.ICE));
        assertTrue(counts.getOrDefault(Items.COAL_BLOCK, 0) >= 81, "kit coal blocks " + counts.get(Items.COAL_BLOCK));
        assertTrue(counts.getOrDefault(byId("minecraft:black_wool"), 0) >= 40, "kit black wool");
        assertTrue(counts.getOrDefault(byId("minecraft:light_blue_glazed_terracotta"), 0) >= 30, "kit light blue glazed terracotta");
        assertTrue(counts.getOrDefault(byId("minecraft:gray_glazed_terracotta"), 0) >= 8, "kit gray glazed terracotta");
        assertTrue(counts.getOrDefault(byId("minecraft:gray_concrete"), 0) >= 3, "kit gray concrete");
        assertTrue(counts.getOrDefault(byId("minecraft:light_blue_terracotta"), 0) >= 2, "kit light blue terracotta");
        assertTrue(counts.getOrDefault(byId("minecraft:polished_blackstone_bricks"), 0) >= 1, "kit polished blackstone bricks");
        assertTrue(counts.getOrDefault(byId("minecraft:cracked_polished_blackstone_bricks"), 0) >= 1, "kit cracked polished blackstone bricks");
        System.out.println("[Test] PASS omegalogo kit");
    }

    private static void setMeteorPrinterSetting(String strName, Object value) {
        try {
            Class<?> modules = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
            Object module = modules.getMethod("get", String.class).invoke(modules.getMethod("get").invoke(null), "printer");
            Object settings = module.getClass().getField("settings").get(module);
            Object setting = settings.getClass().getMethod("get", String.class).invoke(settings, strName);
            setting.getClass().getMethod("set", Object.class).invoke(setting, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not set the Meteor printer setting " + strName, e);
        }
    }

    private static void meteorInterop(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        boolean bMeteor = loader.isModLoaded("meteor-client");
        boolean bAddon = loader.isModLoaded("meteor-litematica-printer");
        boolean bDetected = ctx.computeOnClient(mc -> xyz.omegaware.builder.core.MeteorInterop.hasMeteorPrinter());
        System.out.println("[Test] meteor interop: meteor-client loaded " + bMeteor + ", printer addon loaded " + bAddon + ", printer module detected " + bDetected);
        if (!bMeteor) {
            System.out.println("[Test] SKIP meteor interop, Meteor Client is not in this run (pass -Pmeteor)");
            return;
        }
        assertTrue(bMeteor, "Meteor Client should be loaded");
        ctx.takeScreenshot("meteor-loaded");
        if (!bAddon) {
            System.out.println("[Test] SKIP meteor printer, the meteor-litematica-printer addon is not in this run");
            return;
        }
        assertTrue(bDetected, "the Meteor printer module was not detected through MeteorInterop");
        System.out.println("[Test] PASS meteor detection");

        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos a = new BlockPos(-3, 100, 3);
        BlockPos b = new BlockPos(0, 101, 6);
        server.runCommand("fill -3 100 3 0 100 6 minecraft:stone");
        server.runCommand("fill -3 101 3 0 101 6 minecraft:oak_planks");
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> {
            DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().forEach(p -> DataManager.getSchematicPlacementManager().removeSchematicPlacement(p));
            AreaSelection selection = new AreaSelection();
            selection.addSubRegionBox(new Box(a, b, "area"), false);
            LitematicaSchematic schematic = LitematicaSchematic.createFromWorld(mc.level, selection, new LitematicaSchematic.SchematicSaveInfo(false, false), "MeteorTest", string -> {});
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, selection.getEffectiveOrigin(), "meteor-test", true, true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
        });
        server.runCommand("fill -3 100 3 0 101 6 minecraft:air");
        ctx.waitTicks(80);
        server.runCommand("give @p minecraft:stone 64");
        server.runCommand("give @p minecraft:oak_planks 64");
        ctx.waitTicks(10);

        ctx.runOnClient(mc -> {
            setMeteorPrinterSetting("printing-range", 6);
            setMeteorPrinterSetting("printing-delay", 0);
            xyz.omegaware.builder.core.MeteorInterop.setPrinterActive(true);
        });
        ctx.waitTicks(5);
        boolean bActive = ctx.computeOnClient(mc -> xyz.omegaware.builder.core.MeteorInterop.isPrinterActive());
        System.out.println("[Test] Meteor printer module active after toggle: " + bActive);
        assertTrue(bActive, "MeteorInterop could not turn the Meteor printer module on");
        ctx.getInput().lookAt(a.offset(2, 0, 2));
        ctx.takeScreenshot("meteor-printer-start");
        ctx.waitTicks(200);
        System.out.println("[Test] Meteor printer still active after 200 ticks: " + ctx.computeOnClient(mc -> xyz.omegaware.builder.core.MeteorInterop.isPrinterActive()));
        ctx.takeScreenshot("meteor-printer-direct");
        ctx.runOnClient(mc -> xyz.omegaware.builder.core.MeteorInterop.release());
        ctx.runOnClient(mc -> xyz.omegaware.builder.core.MeteorInterop.setPrinterActive(false));

        int[] aDirect = server.computeOnServer(srv -> {
            int iStone = 0;
            int iPlanks = 0;
            for (int x = -3; x <= 0; x++) {
                for (int z = 3; z <= 6; z++) {
                    if (srv.overworld().getBlockState(new BlockPos(x, 100, z)).is(Blocks.STONE)) iStone++;
                    if (srv.overworld().getBlockState(new BlockPos(x, 101, z)).is(Blocks.OAK_PLANKS)) iPlanks++;
                }
            }
            return new int[] {iStone, iPlanks};
        });
        System.out.println("[Test] Meteor printer alone placed " + aDirect[0] + " stone and " + aDirect[1] + " planks of 16 each");
        assertTrue(aDirect[0] + aDirect[1] > 0, "the Meteor printer placed nothing on its own");
        System.out.println("[Test] PASS meteor printer direct");

        server.runCommand("fill -3 100 3 0 101 6 minecraft:air");
        server.runCommand("clear @p");
        server.runCommand("give @p minecraft:stone 64");
        server.runCommand("give @p minecraft:oak_planks 64");
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> {
            Config.set("printer", "true");
            Config.set("printer-backend", "meteor");
            Config.set("printer-delay", "0");
        });
        boolean[] aSeenActive = {false};
        boolean bStarted = ctx.computeOnClient(mc -> Controller.get().startLitematica(0));
        assertTrue(bStarted, "the Litematica build did not start for the Meteor scenario");
        int iWaited = ctx.waitFor(mc -> {
            if (xyz.omegaware.builder.core.MeteorInterop.isPrinterActive()) aSeenActive[0] = true;
            return !Controller.get().isRunning();
        }, iTimeoutTicks);
        ctx.runOnClient(mc -> {
            Config.set("printer", "false");
            Config.set("printer-backend", "auto");
        });
        System.out.println("[Test] Meteor backend build stopped after " + iWaited + " ticks, state " + Controller.get().state() + ", Meteor printer was active " + aSeenActive[0]);
        ctx.takeScreenshot("meteor-backend-end");
        assertTrue(aSeenActive[0], "the controller never turned the Meteor printer on");
        assertTrue(Controller.get().state() == Controller.State.FINISHED, "Meteor backend build ended in " + Controller.get().state());
        int[] aBackend = server.computeOnServer(srv -> {
            int iStone = 0;
            int iPlanks = 0;
            for (int x = -3; x <= 0; x++) {
                for (int z = 3; z <= 6; z++) {
                    if (srv.overworld().getBlockState(new BlockPos(x, 100, z)).is(Blocks.STONE)) iStone++;
                    if (srv.overworld().getBlockState(new BlockPos(x, 101, z)).is(Blocks.OAK_PLANKS)) iPlanks++;
                }
            }
            return new int[] {iStone, iPlanks};
        });
        System.out.println("[Test] Meteor backend build placed " + aBackend[0] + " stone and " + aBackend[1] + " planks of 16 each");
        assertTrue(aBackend[0] == 16 && aBackend[1] == 16, "Meteor backend build placed " + aBackend[0] + " stone and " + aBackend[1] + " planks");
        System.out.println("[Test] PASS meteor backend");
    }

    private static void storageTypes(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos right = new BlockPos(4, 100, -5);
        BlockPos left = new BlockPos(5, 100, -5);
        BlockPos barrel = new BlockPos(-4, 100, -5);
        BlockPos origin = new BlockPos(-4, 100, 2);

        server.runCommand("setblock 4 100 -5 minecraft:chest[facing=north,type=left]");
        server.runCommand("setblock 5 100 -5 minecraft:chest[facing=north,type=right]");
        server.runCommand("setblock -4 100 -5 minecraft:barrel[facing=up]");
        server.runCommand("item replace block 4 100 -5 container.3 with minecraft:oak_planks 16");
        server.runCommand("item replace block -4 100 -5 container.7 with minecraft:cobblestone 16");
        ctx.waitTicks(20);

        ctx.runOnClient(mc -> {
            ShulkerLedger.get().clear();
            StorageIndex.get().record(right, Map.of(Items.OAK_PLANKS, 16));
            StorageIndex.get().record(left, Map.of(Items.OAK_PLANKS, 16));
            StorageIndex.get().record(barrel, Map.of(Items.COBBLESTONE, 16));
        });
        int iIndexed = ctx.computeOnClient(mc -> StorageIndex.get().all().size());
        assertTrue(iIndexed == 2, "a double chest and a barrel should index as 2 storages, got " + iIndexed);

        var schematic = new StripeSchematic(4, 4, Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        ctx.runOnClient(mc -> Controller.get().start("storage", schematic, origin));
        int iWaited = ctx.waitFor(mc -> !Controller.get().isRunning(), iTimeoutTicks);
        System.out.println("[Test] storage stopped after " + iWaited + " ticks, state " + Controller.get().state());
        ctx.takeScreenshot("storage-end");
        assertTrue(Controller.get().state() == Controller.State.FINISHED, "storage scenario ended in " + Controller.get().state());

        int[] aCounts = server.computeOnServer(srv -> {
            int[] counts = new int[2];
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 4; z++) {
                    var state = srv.overworld().getBlockState(origin.offset(x, 0, z));
                    if (state.is(Blocks.OAK_PLANKS)) counts[0]++;
                    if (state.is(Blocks.COBBLESTONE)) counts[1]++;
                }
            }
            return counts;
        });
        assertTrue(aCounts[0] == 16 && aCounts[1] == 16, "storage scenario placed planks " + aCounts[0] + " cobblestone " + aCounts[1]);
        System.out.println("[Test] PASS storage types");
    }

    private static void orientation(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos origin = new BlockPos(-3, 100, 2);
        var N = net.minecraft.core.Direction.NORTH;
        var S = net.minecraft.core.Direction.SOUTH;
        var E = net.minecraft.core.Direction.EAST;
        var W = net.minecraft.core.Direction.WEST;
        var aStates = new net.minecraft.world.level.block.state.BlockState[] {
            facing("minecraft:gray_glazed_terracotta", N), facing("minecraft:gray_glazed_terracotta", S),
            facing("minecraft:gray_glazed_terracotta", E), facing("minecraft:gray_glazed_terracotta", W),
            blockState("minecraft:oak_log").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X),
            blockState("minecraft:oak_log").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Z),
            facing("minecraft:oak_stairs", E).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP),
            facing("minecraft:oak_stairs", W),
            facing("minecraft:observer", N), facing("minecraft:dispenser", net.minecraft.core.Direction.UP),
            blockState("minecraft:oak_slab").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP),
            blockState("minecraft:oak_slab")
        };
        for (String strGive : new String[] {"gray_glazed_terracotta 8", "oak_log 4", "oak_stairs 4", "observer 2", "dispenser 2", "oak_slab 4"}) {
            server.runCommand("give @p minecraft:" + strGive);
        }
        ctx.waitTicks(10);

        ctx.runOnClient(mc -> {
            Config.set("printer", "true");
            Config.set("printer-delay", "0");
            Config.set("printer-range", "6");
            Config.set("printer-rotate", "true");
        });
        var session = ctx.computeOnClient(mc -> new BuildSession("orient", new StatesSchematic(6, 1, 2, aStates), origin));
        ctx.getInput().lookAt(origin.offset(3, 0, 1));
        for (int i = 0; i < 100; i++) {
            ctx.runOnClient(mc -> {
                session.tick(mc.level);
                Printer.tick(session);
            });
            ctx.waitTick();
            if (i == 20 || i == 60) ctx.takeScreenshot("orient-" + i);
        }
        ctx.runOnClient(mc -> Config.set("printer", "false"));
        ctx.takeScreenshot("orient-end");

        int iBad = server.computeOnServer(srv -> {
            int iCount = 0;
            for (int i = 0; i < aStates.length; i++) {
                var got = srv.overworld().getBlockState(origin.offset(i % 6, 0, i / 6));
                if (!sameOrientation(aStates[i], got)) {
                    iCount++;
                    System.out.println("[Test] orientation mismatch at " + i + " wanted " + aStates[i] + " got " + got);
                }
            }
            return iCount;
        });
        assertTrue(iBad == 0, iBad + " of " + aStates.length + " oriented blocks were placed wrong");
        System.out.println("[Test] PASS orientation");
    }

    private static void airOrientation(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos target = new BlockPos(1, 104, 1);
        var wanted = facing("minecraft:gray_glazed_terracotta", net.minecraft.core.Direction.WEST);
        server.runCommand("give @p minecraft:gray_glazed_terracotta 4");
        ctx.waitTicks(10);

        ctx.runOnClient(mc -> {
            Config.set("printer", "true");
            Config.set("printer-delay", "0");
            Config.set("printer-air-place", "on");
            Config.set("printer-rotate", "true");
        });
        var session = ctx.computeOnClient(mc -> new BuildSession("airorient", new StatesSchematic(1, 1, 1, wanted), target));
        ctx.getInput().lookAt(target);
        ctx.takeScreenshot("air-orient-before");
        for (int i = 0; i < 30; i++) {
            ctx.runOnClient(mc -> {
                session.tick(mc.level);
                Printer.tick(session);
            });
            ctx.waitTick();
        }
        ctx.takeScreenshot("air-orient-after");
        ctx.runOnClient(mc -> {
            Config.set("printer", "false");
            Config.set("printer-air-place", "auto");
        });
        var got = server.computeOnServer(srv -> srv.overworld().getBlockState(target));
        System.out.println("[Test] air orientation wanted " + wanted + " got " + got);
        assertTrue(sameOrientation(wanted, got), "air placed block has the wrong orientation: " + got);
        System.out.println("[Test] PASS air orientation");
    }

    private static void shulkerCycle(ClientGameTestContext ctx, TestSingleplayerContext world, boolean bDump) {
        resetArena(ctx, world);
        var server = world.getServer();
        BlockPos chest = new BlockPos(4, 100, -5);
        BlockPos dump = new BlockPos(-4, 100, -5);
        BlockPos origin = new BlockPos(-4, 100, 2);

        server.runCommand("setblock 4 100 -5 minecraft:chest");
        server.runCommand("setblock -4 100 -5 minecraft:chest");
        server.runCommand("item replace block 4 100 -5 container.5 with minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:oak_planks\",count:64}},{slot:1,item:{id:\"minecraft:oak_planks\",count:64}}]]");
        ctx.waitTicks(20);

        var shulkerItem = net.minecraft.world.item.Items.SHULKER_BOX;
        ctx.runOnClient(mc -> {
            ShulkerLedger.get().clear();
            ShulkerLedger.iUseCycles = 0;
            Config.set("max-stacks-per-item", "1");
            Config.set("shulker-return", bDump ? "dump" : "origin");
            StorageIndex.get().record(chest, Map.of(shulkerItem, 1), java.util.List.of(new StorageIndex.ShulkerInfo(5, Map.of(Items.OAK_PLANKS, 128))));
            StorageIndex.get().setDump(bDump ? dump : null);
        });

        var schematic = new StripeSchematic(16, 8, Blocks.OAK_PLANKS.defaultBlockState());
        ctx.runOnClient(mc -> Controller.get().start("cycle", schematic, origin));
        int iWaited = ctx.waitFor(mc -> !Controller.get().isRunning(), iTimeoutTicks);
        ctx.runOnClient(mc -> {
            Config.set("max-stacks-per-item", "9");
            Config.set("shulker-return", "origin");
            StorageIndex.get().setDump(null);
        });
        System.out.println("[Test] cycle (dump=" + bDump + ") stopped after " + iWaited + " ticks, state " + Controller.get().state() + ", use cycles " + ShulkerLedger.iUseCycles);
        ctx.takeScreenshot(bDump ? "dump-end" : "cycle-end");

        assertTrue(Controller.get().state() == Controller.State.FINISHED, "cycle scenario ended in " + Controller.get().state());
        assertTrue(ShulkerLedger.iUseCycles >= 2, "expected the one shulker to be placed and emptied at least twice, got " + ShulkerLedger.iUseCycles);

        int iPlaced = server.computeOnServer(srv -> {
            int iCount = 0;
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 16; z++) {
                    if (srv.overworld().getBlockState(origin.offset(x, 0, z)).is(Blocks.OAK_PLANKS)) iCount++;
                }
            }
            return iCount;
        });
        assertTrue(iPlaced == 128, "cycle scenario placed " + iPlaced + " of 128 planks");

        BlockPos back = bDump ? dump : chest;
        boolean bReturned = server.computeOnServer(srv -> {
            var container = (net.minecraft.world.Container) srv.overworld().getBlockEntity(back);
            for (int i = 0; i < container.getContainerSize(); i++) {
                var stack = container.getItem(i);
                if (Inv.isShulker(stack) && Inv.shulkerContents(stack).isEmpty() && (bDump || i == 5)) return true;
            }
            return false;
        });
        assertTrue(bReturned, "empty shulker was not returned to " + (bDump ? "the dump chest" : "its origin slot 5"));
        if (bDump) {
            boolean bOriginEmpty = server.computeOnServer(srv -> ((net.minecraft.world.Container) srv.overworld().getBlockEntity(chest)).getItem(5).isEmpty());
            assertTrue(bOriginEmpty, "origin chest slot 5 should be empty after sending the shulker to the dump chest");
        }
        assertTrue(ShulkerLedger.get().all().isEmpty(), "ledger still tracks " + ShulkerLedger.get().all().size() + " shulkers");
        System.out.println("[Test] PASS " + (bDump ? "dump" : "cycle"));
    }

    private static void prepareArena(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server = world.getServer();
        announce(world);
        server.runCommand("difficulty peaceful");
        server.runCommand("gamerule advance_time false");
        server.runCommand("gamemode creative @p");
        server.runCommand("forceload add -32 -32 32 32");
        ctx.waitTicks(60);
        server.runCommand("fill -20 99 -20 20 99 20 minecraft:dirt");
        clearAir(ctx, world);
        int iWaited = server.waitFor(srv -> srv.overworld().getBlockState(new BlockPos(0, 99, 0)).is(Blocks.DIRT), 20 * 30);
        System.out.println("[Test] Platform ready after " + iWaited + " ticks");
        assertTrue(world.getServer().computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(0, 99, 0)).is(Blocks.DIRT)), "arena platform was not built");
        server.runCommand("tp @p 0 100 0");
        ctx.waitTicks(20);
        server.runCommand("gamemode survival @p");
        ctx.waitTicks(20);
    }

    private static void clearAir(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("fill -20 100 -20 20 106 20 minecraft:air");
    }

    private static void announce(TestSingleplayerContext world) {
        world.getServer().runCommand("tellraw @a {\"text\":\"https://github.com/Omega172 | This is an automated test,  with no human interaction.\",\"color\":\"yellow\"}");
    }

    private static void resetArena(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server = world.getServer();
        announce(world);
        Controller.get().stop();
        server.runCommand("clear @p");
        clearAir(ctx, world);
        server.runCommand("tp @p 0 100 0");
        ctx.runOnClient(mc -> StorageIndex.get().clear());
        ctx.waitTicks(20);
    }

    private static void startBuild(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> Controller.get().start("test", new CubeSchematic(iWidth, 1, iLength, Blocks.STONE.defaultBlockState()), s_origin));
        int iWaited = ctx.waitFor(mc -> !Controller.get().isRunning(), iTimeoutTicks);
        System.out.println("[Test] Controller stopped after " + iWaited + " ticks, state " + Controller.get().state());
    }

    private static int stoneBlocks(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            int iCount = 0;
            for (int x = 0; x < iWidth; x++) {
                for (int z = 0; z < iLength; z++) {
                    if (server.overworld().getBlockState(s_origin.offset(x, 0, z)).is(Blocks.STONE)) iCount++;
                }
            }
            return iCount;
        });
    }

    private static net.minecraft.world.item.Item byId(String strId) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(strId));
    }

    private static void assertTrue(boolean bCondition, String strMessage) {
        if (!bCondition) throw new AssertionError(strMessage);
    }

    private static void chestResupply(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        server.runCommand("setblock 5 100 5 minecraft:chest");
        server.runCommand("item replace block 5 100 5 container.0 with minecraft:stone 64");
        server.runCommand("item replace block 5 100 5 container.1 with minecraft:stone 64");
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> StorageIndex.get().record(new BlockPos(5, 100, 5), Map.of(Items.STONE, 128)));

        startBuild(ctx);
        ctx.takeScreenshot("chest-resupply-end");

        assertTrue(Controller.get().state() == Controller.State.FINISHED, "chest scenario ended in " + Controller.get().state());
        int iBlocks = stoneBlocks(world);
        assertTrue(iBlocks == iWidth * iLength, "chest scenario placed " + iBlocks + " of " + iWidth * iLength);
        System.out.println("[Test] PASS chest");
    }

    private static void shulkerResupply(ClientGameTestContext ctx, TestSingleplayerContext world) {
        resetArena(ctx, world);
        var server = world.getServer();
        server.runCommand("give @p minecraft:diamond_pickaxe");
        server.runCommand("give @p minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:stone\",count:64}},{slot:1,item:{id:\"minecraft:stone\",count:64}}]]");
        ctx.waitTicks(10);

        startBuild(ctx);
        ctx.takeScreenshot("shulker-resupply-end");

        assertTrue(Controller.get().state() == Controller.State.FINISHED, "shulker scenario ended in " + Controller.get().state());
        int iBlocks = stoneBlocks(world);
        assertTrue(iBlocks == iWidth * iLength, "shulker scenario placed " + iBlocks + " of " + iWidth * iLength);

        boolean bShulkerInInventory = ctx.computeOnClient(mc -> {
            for (int i = 0; i < Inv.iMainSize; i++) {
                if (Inv.isShulker(mc.player.getInventory().getItem(i))) return true;
            }
            return false;
        });
        assertTrue(bShulkerInInventory, "shulker was not picked back up");

        boolean bShulkerInWorld = world.getServer().computeOnServer(srv -> {
            for (int x = -10; x <= 10; x++) {
                for (int y = 100; y <= 102; y++) {
                    for (int z = -10; z <= 10; z++) {
                        if (srv.overworld().getBlockState(new BlockPos(x, y, z)).getBlock() instanceof ShulkerBoxBlock) return true;
                    }
                }
            }
            return false;
        });
        assertTrue(!bShulkerInWorld, "a placed shulker box was left in the world");
        System.out.println("[Test] PASS shulker");
    }
}
