package xyz.omegaware.builder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.omegaware.builder.core.Config;
import xyz.omegaware.builder.core.Controller;
import xyz.omegaware.builder.step.TransferStep;
import xyz.omegaware.builder.storage.StorageIndex;
import xyz.omegaware.builder.util.Inv;
import xyz.omegaware.builder.util.Log;
import xyz.omegaware.builder.util.Storage;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class BuilderMod implements ClientModInitializer {
    private static boolean s_bLinkMode = false;
    private static BlockPos s_lastClicked = null;
    private static AbstractContainerMenu s_lastMenu = null;
    private static int s_iMenuTicks = 0;

    private static final String[][] s_aCommandHelp = {
        {"/autobuild build <file>", "Build a schematic from the schematics folder with the origin at your feet"},
        {"/autobuild litematica [n]", "Build the n-th placed Litematica schematic (default 1) at its placed position"},
        {"/autobuild stop", "Stop building and cancel all pathing"},
        {"/autobuild status", "Print the current state and blocks left"},
        {"/autobuild printer", "Toggle the printer"},
        {"/autobuild config [key] [value]", "List or change settings"},
        {"/autobuild link", "Toggle indexing of every storage you open"},
        {"/autobuild storages", "List indexed storages"},
        {"/autobuild forget", "Clear the storage index"},
        {"/autobuild sethome", "Set the position to return to when stuck"},
        {"/autobuild setdump", "Set the storage you are looking at as the dump chest for returned shulker boxes"},
        {"/autobuild cleardump", "Clear the dump chest"},
        {"/autobuild returnshulkers", "Return every shulker box you are carrying to where it came from (or the dump chest)"},
        {"/autobuild help", "Print this list and every setting"},
    };

    @Override
    public void onInitializeClient() {
        Config.load();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Controller.get().tick();
            tickLinkMode(mc);
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) s_lastClicked = hit.getBlockPos();
            return InteractionResult.PASS;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommands.literal("autobuild")
                .then(ClientCommands.literal("build")
                    .then(ClientCommands.argument("file", StringArgumentType.greedyString()).executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player == null) return 0;
                        File file = new File(new File(mc.gameDirectory, "schematics"), StringArgumentType.getString(context, "file"));
                        if (!file.exists()) {
                            Log.chat("File not found: " + file);
                            return 0;
                        }
                        Controller.get().start(file, mc.player.blockPosition());
                        return 1;
                    })))
                .then(ClientCommands.literal("litematica")
                    .executes(context -> Controller.get().startLitematica(0) ? 1 : 0)
                    .then(ClientCommands.argument("placement", IntegerArgumentType.integer(1)).executes(context ->
                        Controller.get().startLitematica(IntegerArgumentType.getInteger(context, "placement") - 1) ? 1 : 0)))
                .then(ClientCommands.literal("returnshulkers").executes(context -> Controller.get().returnShulkers() ? 1 : 0))
                .then(ClientCommands.literal("stop").executes(context -> {
                    Controller.get().stop();
                    Log.chat("Stopped.");
                    return 1;
                }))
                .then(ClientCommands.literal("status").executes(context -> {
                    Log.chat(Controller.get().status());
                    return 1;
                }))
                .then(ClientCommands.literal("link").executes(context -> {
                    s_bLinkMode = !s_bLinkMode;
                    Log.chat("Link mode " + (s_bLinkMode ? "on: open storages to index them." : "off."));
                    return 1;
                }))
                .then(ClientCommands.literal("storages").executes(context -> {
                    StorageIndex.get().all().forEach(entry -> Log.chat(entry.pos.toShortString() + ": " + entry.contents.size() + " item types"));
                    return 1;
                }))
                .then(ClientCommands.literal("forget").executes(context -> {
                    StorageIndex.get().clear();
                    Log.chat("Storage index cleared.");
                    return 1;
                }))
                .then(ClientCommands.literal("printer").executes(context -> {
                    Config.set("printer", String.valueOf(!Config.bPrinter));
                    Log.chat("Printer " + (Config.bPrinter ? "on" : "off"));
                    return 1;
                }))
                .then(ClientCommands.literal("config")
                    .executes(context -> {
                        Config.values().forEach((strKey, strValue) -> Log.chat(strKey + " = " + strValue));
                        return 1;
                    })
                    .then(ClientCommands.argument("key", StringArgumentType.word())
                        .then(ClientCommands.argument("value", StringArgumentType.word()).executes(context -> {
                            String strKey = StringArgumentType.getString(context, "key");
                            String strOld = Config.values().get(strKey);
                            String strError = Config.set(strKey, StringArgumentType.getString(context, "value"));
                            Log.chat(strError != null ? strError : "Set " + strKey + " to " + Config.values().get(strKey) + " | Was: " + strOld);
                            return strError == null ? 1 : 0;
                        }))))
                .then(ClientCommands.literal("help").executes(context -> {
                    Log.chat("Commands:");
                    for (String[] aCommand : s_aCommandHelp) Log.chat(aCommand[0] + " - " + aCommand[1]);
                    Log.chat("Settings (change with /autobuild config <key> <value>):");
                    Config.values().forEach((strKey, strValue) -> Log.chat(strKey + " = " + strValue));
                    return 1;
                }))
                .then(ClientCommands.literal("setdump").executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || mc.level == null) return 0;
                    if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                        || !Storage.isStorage(mc.level, hit.getBlockPos())) {
                        Log.chat("Look at a chest, barrel, shulker box, hopper, dropper, dispenser or ender chest first.");
                        return 0;
                    }
                    StorageIndex.get().setDump(hit.getBlockPos());
                    Log.chat("Dump chest set to " + hit.getBlockPos().toShortString() + ". Use /autobuild config shulker-return dump to send shulkers there.");
                    return 1;
                }))
                .then(ClientCommands.literal("cleardump").executes(context -> {
                    StorageIndex.get().setDump(null);
                    Log.chat("Dump chest cleared.");
                    return 1;
                }))
                .then(ClientCommands.literal("sethome").executes(context -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player == null) return 0;
                    StorageIndex.get().setHome(player.blockPosition());
                    Log.chat("Home set to " + player.blockPosition().toShortString());
                    return 1;
                }))
        ));
    }

    private static void tickLinkMode(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !s_bLinkMode || Controller.get().isRunning() || s_lastClicked == null) return;
        if (!Storage.isStorage(mc.level, s_lastClicked)) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) {
            s_lastMenu = null;
            return;
        }
        if (menu != s_lastMenu) {
            s_lastMenu = menu;
            s_iMenuTicks = 0;
            return;
        }
        if (++s_iMenuTicks != 5) return;

        Map<Item, Integer> contents = new HashMap<>();
        int iContainerSlots = menu.slots.size() - Inv.iMainSize;
        for (int i = 0; i < iContainerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        StorageIndex.get().record(s_lastClicked, contents, TransferStep.shulkerInfos(menu, iContainerSlots));
        Log.chat("Indexed storage at " + s_lastClicked.toShortString() + " (" + contents.size() + " item types)");
    }
}
