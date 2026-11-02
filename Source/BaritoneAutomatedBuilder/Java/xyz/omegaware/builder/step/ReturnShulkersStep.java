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

public class ReturnShulkersStep implements Step {
    private final Supplier<BlockPos> m_chestPos;
    private final List<ShulkerLedger.Entry> m_aEntries;
    private final boolean m_bKeepSlots;
    private int m_iIndex = 0;
    private int m_iPhase = 0;
    private int m_iWait = 0;
    private int m_iSettleTicks = 0;
    private int m_iTargetSlot = -1;

    public ReturnShulkersStep(Supplier<BlockPos> chestPos, List<ShulkerLedger.Entry> aEntries, boolean bKeepSlots) {
        m_chestPos = chestPos;
        m_bKeepSlots = bKeepSlots;
        m_aEntries = new ArrayList<>(aEntries);
    }

    @Override
    public String name() {
        return "ReturnShulkers";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 30;
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
        if (m_iWait-- > 0) return Result.RUNNING;
        if (m_iIndex >= m_aEntries.size()) return finish(menu, iContainerSlots);

        ShulkerLedger.Entry entry = m_aEntries.get(m_iIndex);

        if (m_iPhase == 0) {
            int iInventory = Inv.findShulkerWithExactContents(entry.contents);
            if (iInventory < 0) {
                ShulkerLedger.get().remove(entry);
                m_iIndex++;
                return Result.RUNNING;
            }
            m_iTargetSlot = -1;
            if (m_bKeepSlots && entry.iOriginSlot < iContainerSlots && menu.getSlot(entry.iOriginSlot).getItem().isEmpty()) m_iTargetSlot = entry.iOriginSlot;
            else {
                for (int i = 0; i < iContainerSlots; i++) {
                    if (menu.getSlot(i).getItem().isEmpty()) {
                        m_iTargetSlot = i;
                        break;
                    }
                }
            }
            if (m_iTargetSlot < 0) {
                m_iIndex++;
                return Result.RUNNING;
            }
            mc.gameMode.handleContainerInput(menu.containerId, Inv.menuSlotOfPlayerIndex(menu, iInventory), 0, ContainerInput.PICKUP, player);
            m_iPhase = 1;
            m_iWait = 2;
            return Result.RUNNING;
        }

        mc.gameMode.handleContainerInput(menu.containerId, m_iTargetSlot, 0, ContainerInput.PICKUP, player);
        ShulkerLedger.get().remove(entry);
        m_iIndex++;
        m_iPhase = 0;
        m_iWait = 4;
        return Result.RUNNING;
    }
}
