package xyz.omegaware.builder.test;

import baritone.api.schematic.IStaticSchematic;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SliceSchematic implements IStaticSchematic {
    private final IStaticSchematic m_base;
    private final int m_iZOffset;
    private final int m_iZLength;

    public SliceSchematic(IStaticSchematic base, int iZOffset, int iZLength) {
        m_base = base;
        m_iZOffset = iZOffset;
        m_iZLength = iZLength;
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        return m_base.getDirect(x, y, z + m_iZOffset);
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return m_base.desiredState(x, y, z + m_iZOffset, current, approxPlaceable);
    }

    @Override
    public int widthX() {
        return m_base.widthX();
    }

    @Override
    public int heightY() {
        return m_base.heightY();
    }

    @Override
    public int lengthZ() {
        return m_iZLength;
    }
}
