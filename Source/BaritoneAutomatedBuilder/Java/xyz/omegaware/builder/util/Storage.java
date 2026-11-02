package xyz.omegaware.builder.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

public final class Storage {
    public static final double dBlockEntityViewDistance = 64.0;

    private Storage() {}

    public static boolean canCheck(Level level, Player player, BlockPos pos) {
        if (level == null || player == null || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        return player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= dBlockEntityViewDistance * dBlockEntityViewDistance;
    }

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
