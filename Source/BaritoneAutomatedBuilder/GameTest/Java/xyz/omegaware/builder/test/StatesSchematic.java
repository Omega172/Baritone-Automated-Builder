package xyz.omegaware.builder.test;

import baritone.api.schematic.IStaticSchematic;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class StatesSchematic implements IStaticSchematic {
    private final int m_iWidth;
    private final int m_iHeight;
    private final int m_iLength;
    private final BlockState[] m_aStates;

    public StatesSchematic(int iWidth, int iHeight, int iLength, BlockState... aStates) {
        m_iWidth = iWidth;
        m_iHeight = iHeight;
        m_iLength = iLength;
        m_aStates = aStates;
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        return m_aStates[(y * m_iLength + z) * m_iWidth + x];
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return getDirect(x, y, z);
    }

    @Override
    public int widthX() {
        return m_iWidth;
    }

    @Override
    public int heightY() {
        return m_iHeight;
    }

    @Override
    public int lengthZ() {
        return m_iLength;
    }
}
