package xyz.omegaware.builder.core;

import baritone.api.BaritoneAPI;
import baritone.api.schematic.IStaticSchematic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

public class BuildSession {
    private static final int iScanPerTick = 40000;

    public final String strName;
    public final IStaticSchematic schematic;
    public final BlockPos origin;

    private final boolean m_bDynamic;
    private final int m_iWidth;
    private final int m_iHeight;
    private final int m_iLength;
    private final Block[] m_aDesired;
    private final boolean[] m_aResolved;
    private final boolean[] m_aPending;
    private final Map<Item, Integer> m_aNeeded = new HashMap<>();
    private int m_iUnresolved;
    private int m_iCursor = 0;
    private int m_iPasses = 0;

    public BuildSession(String strName, IStaticSchematic schematic, BlockPos origin) {
        this(strName, schematic, origin, false);
    }

    public BuildSession(String strName, IStaticSchematic schematic, BlockPos origin, boolean bDynamic) {
        this.strName = strName;
        this.schematic = schematic;
        this.origin = origin;
        m_bDynamic = bDynamic;
        m_iWidth = schematic.widthX();
        m_iHeight = schematic.heightY();
        m_iLength = schematic.lengthZ();
        m_aDesired = new Block[m_iWidth * m_iHeight * m_iLength];
        m_aResolved = new boolean[m_aDesired.length];
        m_aPending = new boolean[m_aDesired.length];
        m_iUnresolved = m_aDesired.length;
    }

    private int index(int x, int y, int z) {
        return x + m_iWidth * (z + m_iLength * y);
    }

    private Block tracked(BlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();
        if (block == Blocks.AIR || block.asItem() == Items.AIR) return null;
        var settings = BaritoneAPI.getSettings();
        if (settings.buildIgnoreBlocks.value.contains(block) || settings.buildSkipBlocks.value.contains(block)) return null;
        return block;
    }

    public BlockState desiredState(BlockPos pos) {
        int x = pos.getX() - origin.getX();
        int y = pos.getY() - origin.getY();
        int z = pos.getZ() - origin.getZ();
        if (x < 0 || y < 0 || z < 0 || x >= m_iWidth || y >= m_iHeight || z >= m_iLength) return null;
        BlockState state = schematic.getDirect(x, y, z);
        return tracked(state) == null ? null : state;
    }

    public void tick(ClientLevel level) {
        int iEnd = Math.min(m_iCursor + iScanPerTick, m_aDesired.length);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = m_iCursor; i < iEnd; i++) {
            if (!m_bDynamic && m_aResolved[i] && m_aDesired[i] == null) continue;

            int x = i % m_iWidth;
            int z = (i / m_iWidth) % m_iLength;
            int y = i / (m_iWidth * m_iLength);
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (!level.hasChunkAt(pos)) continue;

            if (m_bDynamic || !m_aResolved[i]) {
                Block resolved = tracked(schematic.getDirect(x, y, z));
                if (!m_aResolved[i]) {
                    m_aResolved[i] = true;
                    m_iUnresolved--;
                }
                if (resolved != m_aDesired[i]) {
                    if (m_aPending[i] && m_aDesired[i] != null) m_aNeeded.merge(m_aDesired[i].asItem(), -1, Integer::sum);
                    m_aPending[i] = false;
                    m_aDesired[i] = resolved;
                }
            }

            Block desired = m_aDesired[i];
            if (desired == null) continue;

            boolean bPending = level.getBlockState(pos).getBlock() != desired;
            if (bPending == m_aPending[i]) continue;
            m_aPending[i] = bPending;
            m_aNeeded.merge(desired.asItem(), bPending ? 1 : -1, Integer::sum);
        }
        m_iCursor = iEnd;
        if (m_iCursor >= m_aDesired.length) {
            m_iCursor = 0;
            m_iPasses++;
        }
    }

    public BlockPos nearestPending(BlockPos from, BiPredicate<BlockPos, BlockState> filter) {
        BlockPos best = null;
        double dBest = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < m_aDesired.length; i++) {
            if (!m_aPending[i] || m_aDesired[i] == null) continue;
            int x = i % m_iWidth;
            int z = (i / m_iWidth) % m_iLength;
            int y = i / (m_iWidth * m_iLength);
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            double dDistance = pos.distSqr(from);
            if (dDistance >= dBest || !filter.test(pos, schematic.getDirect(x, y, z))) continue;
            dBest = dDistance;
            best = pos.immutable();
        }
        return best;
    }

    public boolean usesLitematica() {
        return m_bDynamic;
    }

    public boolean hasCompletedPass() {
        return m_iPasses > 0;
    }

    public boolean isFullyResolved() {
        return m_iUnresolved == 0;
    }

    public Map<Item, Integer> needed() {
        Map<Item, Integer> aResult = new HashMap<>();
        m_aNeeded.forEach((item, iCount) -> {
            if (iCount > 0) aResult.put(item, iCount);
        });
        return aResult;
    }

    public int totalNeeded() {
        int iTotal = 0;
        for (int iCount : m_aNeeded.values()) {
            if (iCount > 0) iTotal += iCount;
        }
        return iTotal;
    }
}
