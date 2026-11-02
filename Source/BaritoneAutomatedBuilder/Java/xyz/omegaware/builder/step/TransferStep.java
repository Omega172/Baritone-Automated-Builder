package xyz.omegaware.builder.step;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.omegaware.builder.storage.StorageIndex;
import xyz.omegaware.builder.util.Inv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class TransferStep implements Step {
    private final Map<Item, Integer> m_aWanted;
    private final Supplier<BlockPos> m_indexPos;
    private final int m_iClickDelay;
    private final Blackboard m_blackboard;
    private final int m_iKeepFree;
    private final Map<Item, Integer> m_aMoved = new HashMap<>();
    private int m_iCooldown = 0;
    private int m_iPendingSlot = -1;
    private int m_iPendingTicks = 0;
    private int m_iSettleTicks = 0;
    private boolean m_bIndexed = false;

    public TransferStep(Map<Item, Integer> aWanted, Supplier<BlockPos> indexPos, int iClickDelay, Blackboard blackboard, int iKeepFree) {
        m_aWanted = new HashMap<>(aWanted);
        m_indexPos = indexPos;
        m_iClickDelay = Math.max(1, iClickDelay);
        m_blackboard = blackboard;
        m_iKeepFree = Math.max(0, iKeepFree);
    }

    @Override
    public String name() {
        return "Transfer";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 60;
    }

    private int remaining(Item item) {
        return m_aWanted.getOrDefault(item, 0) - m_aMoved.getOrDefault(item, 0);
    }

    private static Map<Item, Integer> contentsOf(AbstractContainerMenu menu, int iContainerSlots) {
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < iContainerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return contents;
    }

    private Result finish(AbstractContainerMenu menu, int iContainerSlots) {
        if (m_blackboard != null) m_blackboard.shulkerAfter = contentsOf(menu, iContainerSlots);
        BlockPos indexPos = m_indexPos.get();
        if (indexPos != null) StorageIndex.get().record(indexPos, contentsOf(menu, iContainerSlots), shulkerInfos(menu, iContainerSlots));
        return Result.DONE;
    }

    public static List<StorageIndex.ShulkerInfo> shulkerInfos(AbstractContainerMenu menu, int iContainerSlots) {
        List<StorageIndex.ShulkerInfo> aInfos = new java.util.ArrayList<>();
        for (int i = 0; i < iContainerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (Inv.isShulker(stack)) aInfos.add(new StorageIndex.ShulkerInfo(i, Inv.shulkerContents(stack)));
        }
        return aInfos;
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return Result.FAILED;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) return Result.FAILED;

        int iContainerSlots = menu.slots.size() - Inv.iMainSize;

        if (!m_bIndexed) {
            if (m_iSettleTicks++ < 4) return Result.RUNNING;
            m_bIndexed = true;
            if (m_blackboard != null) m_blackboard.shulkerBefore = contentsOf(menu, iContainerSlots);
            BlockPos indexPos = m_indexPos.get();
            if (indexPos != null) StorageIndex.get().record(indexPos, contentsOf(menu, iContainerSlots), shulkerInfos(menu, iContainerSlots));
        }

        if (m_iPendingSlot >= 0) {
            if (!menu.getSlot(m_iPendingSlot).hasItem() || ++m_iPendingTicks > 10) m_iPendingSlot = -1;
            else return Result.RUNNING;
        }

        if (m_iCooldown-- > 0) return Result.RUNNING;
        if (Inv.freeSlots() <= m_iKeepFree) return finish(menu, iContainerSlots);

        for (int i = 0; i < iContainerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty() || remaining(stack.getItem()) <= 0) continue;

            m_aMoved.merge(stack.getItem(), stack.getCount(), Integer::sum);
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
            m_iPendingSlot = i;
            m_iPendingTicks = 0;
            m_iCooldown = m_iClickDelay;
            return Result.RUNNING;
        }
        return finish(menu, iContainerSlots);
    }
}
