package xyz.omegaware.builder.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

public final class Inv {
    public static final int iMainSize = 36;

    private Inv() {}

    public static int count(Item item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int iTotal = 0;
        for (int i = 0; i < iMainSize; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) iTotal += stack.getCount();
        }
        return iTotal;
    }

    public static int freeSlots() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        int iFree = 0;
        for (int i = 0; i < iMainSize; i++) {
            if (player.getInventory().getItem(i).isEmpty()) iFree++;
        }
        return iFree;
    }

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    public static Map<Item, Integer> shulkerContents(ItemStack stack) {
        Map<Item, Integer> contents = new HashMap<>();
        ItemContainerContents component = stack.get(DataComponents.CONTAINER);
        if (component == null) return contents;
        component.nonEmptyItemCopyStream().forEach(inner -> contents.merge(inner.getItem(), inner.getCount(), Integer::sum));
        return contents;
    }

    public static int findShulkerWith(Item item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return -1;
        for (int i = 0; i < iMainSize; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isShulker(stack) && shulkerContents(stack).getOrDefault(item, 0) > 0) return i;
        }
        return -1;
    }

    public static int menuSlotOfPlayerIndex(AbstractContainerMenu menu, int iPlayerIndex) {
        int iContainerSlots = menu.slots.size() - iMainSize;
        return iPlayerIndex < 9 ? iContainerSlots + 27 + iPlayerIndex : iContainerSlots + (iPlayerIndex - 9);
    }

    public static int findShulkerWithExactContents(Map<Item, Integer> contents) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return -1;
        for (int i = 0; i < iMainSize; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isShulker(stack) && shulkerContents(stack).equals(contents)) return i;
        }
        return -1;
    }

    public static int menuSlotForInventoryIndex(int iInventoryIndex) {
        return iInventoryIndex < 9 ? 36 + iInventoryIndex : iInventoryIndex;
    }

    public static boolean selectInHotbar(int iInventoryIndex) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return false;
        if (iInventoryIndex < 9) {
            player.getInventory().setSelectedSlot(iInventoryIndex);
            return true;
        }
        int iHotbar = player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlotForInventoryIndex(iInventoryIndex), iHotbar, ContainerInput.SWAP, player);
        return true;
    }
}
