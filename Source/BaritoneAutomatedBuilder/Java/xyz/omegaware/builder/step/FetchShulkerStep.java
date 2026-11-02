package xyz.omegaware.builder.step;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.omegaware.builder.storage.ShulkerLedger;
import xyz.omegaware.builder.storage.StorageIndex;
import xyz.omegaware.builder.util.Inv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class FetchShulkerStep implements Step {
    private final Supplier<BlockPos> m_chestPos;
    private final List<Item> m_aItems;
    private final int m_iClickDelay;
    private final List<Map<Item, Integer>> m_aTaken = new ArrayList<>();
    private int m_iCooldown = 0;
    private int m_iPendingSlot = -1;
    private int m_iPendingTicks = 0;
    private int m_iSettleTicks = 0;

    public FetchShulkerStep(Supplier<BlockPos> chestPos, List<Item> aItems, int iClickDelay) {
        m_chestPos = chestPos;
        m_aItems = List.copyOf(aItems);
        m_iClickDelay = Math.max(1, iClickDelay);
    }

    @Override
    public String name() {
        return "FetchShulker";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 30;
    }

    private boolean satisfied(Item item) {
        for (Map<Item, Integer> contents : m_aTaken) {
            if (contents.getOrDefault(item, 0) > 0) return true;
        }
        return Inv.findShulkerWith(item) >= 0;
    }

    private Result finish(AbstractContainerMenu menu, int iContainerSlots) {
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < iContainerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        StorageIndex.get().record(m_chestPos.get(), contents, TransferStep.shulkerInfos(menu, iContainerSlots));
        return Result.DONE;
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return Result.FAILED;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) return Result.FAILED;
        int iContainerSlots = menu.slots.size() - Inv.iMainSize;

        if (m_iSettleTicks++ < 4) return Result.RUNNING;

        if (m_iPendingSlot >= 0) {
            if (!menu.getSlot(m_iPendingSlot).hasItem() || ++m_iPendingTicks > 10) m_iPendingSlot = -1;
            else return Result.RUNNING;
        }
        if (m_iCooldown-- > 0) return Result.RUNNING;
        if (Inv.freeSlots() <= 1) return finish(menu, iContainerSlots);

        for (Item item : m_aItems) {
            if (satisfied(item)) continue;
            for (int i = 0; i < iContainerSlots; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (!Inv.isShulker(stack)) continue;
                Map<Item, Integer> contents = Inv.shulkerContents(stack);
                if (contents.getOrDefault(item, 0) <= 0) continue;

                ShulkerLedger.get().add(m_chestPos.get(), i, contents);
                m_aTaken.add(contents);
                mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
                m_iPendingSlot = i;
                m_iPendingTicks = 0;
                m_iCooldown = m_iClickDelay;
                return Result.RUNNING;
            }
        }
        return finish(menu, iContainerSlots);
    }
}
