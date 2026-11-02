package xyz.omegaware.builder.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class Storage {
    private Storage() {}

    public static boolean isStorage(Level level, BlockPos pos) {
        if (level == null) return false;
        if (level.getBlockState(pos).getBlock() instanceof EnderChestBlock) return true;
        return level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity;
    }

    public static BlockPos canonical(Level level, BlockPos pos) {
        if (level == null) return pos;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return pos;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        return other.asLong() < pos.asLong() ? other : pos;
    }
}
