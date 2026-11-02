package xyz.omegaware.builder.step;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Map;

public class Blackboard {
    public BlockPos placedShulker = null;
    public Map<Item, Integer> shulkerBefore = null;
    public Map<Item, Integer> shulkerAfter = null;
}
