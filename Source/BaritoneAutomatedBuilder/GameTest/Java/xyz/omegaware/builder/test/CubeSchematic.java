package xyz.omegaware.builder.test;

import baritone.api.schematic.IStaticSchematic;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CubeSchematic implements IStaticSchematic {
    private final int m_iWidth;
    private final int m_iHeight;
    private final int m_iLength;
    private final BlockState m_state;

    public CubeSchematic(int iWidth, int iHeight, int iLength, BlockState state) {
        m_iWidth = iWidth;
        m_iHeight = iHeight;
        m_iLength = iLength;
        m_state = state;
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        return m_state;
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return m_state;
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
