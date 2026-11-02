package xyz.omegaware.builder.test;

import baritone.api.schematic.IStaticSchematic;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class StripeSchematic implements IStaticSchematic {
    private final int m_iLength;
    private final int m_iStripeWidth;
    private final BlockState[] m_aStripes;

    public StripeSchematic(int iLength, int iStripeWidth, BlockState... aStripes) {
        m_iLength = iLength;
        m_iStripeWidth = iStripeWidth;
        m_aStripes = aStripes;
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        return m_aStripes[x / m_iStripeWidth];
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return getDirect(x, y, z);
    }

    @Override
    public int widthX() {
        return m_iStripeWidth * m_aStripes.length;
    }

    @Override
    public int heightY() {
        return 1;
    }

    @Override
    public int lengthZ() {
        return m_iLength;
    }
}
