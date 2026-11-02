package xyz.omegaware.builder.core;

import baritone.api.schematic.IStaticSchematic;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class LitematicaSource {
    public record Loaded(String strName, IStaticSchematic schematic, BlockPos origin) {}

    private LitematicaSource() {}

    public static int placementCount() {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size();
    }

    public static Loaded load(int iPlacement) {
        List<SchematicPlacement> aPlacements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
        if (iPlacement < 0 || iPlacement >= aPlacements.size()) return null;

        SchematicPlacement placement = aPlacements.get(iPlacement);
        int iMinX = Integer.MAX_VALUE;
        int iMinY = Integer.MAX_VALUE;
        int iMinZ = Integer.MAX_VALUE;
        int iMaxX = Integer.MIN_VALUE;
        int iMaxY = Integer.MIN_VALUE;
        int iMaxZ = Integer.MIN_VALUE;
        for (Box box : placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).values()) {
            if (box.getPos1() == null || box.getPos2() == null) continue;
            iMinX = Math.min(iMinX, Math.min(box.getPos1().getX(), box.getPos2().getX()));
            iMinY = Math.min(iMinY, Math.min(box.getPos1().getY(), box.getPos2().getY()));
            iMinZ = Math.min(iMinZ, Math.min(box.getPos1().getZ(), box.getPos2().getZ()));
            iMaxX = Math.max(iMaxX, Math.max(box.getPos1().getX(), box.getPos2().getX()));
            iMaxY = Math.max(iMaxY, Math.max(box.getPos1().getY(), box.getPos2().getY()));
            iMaxZ = Math.max(iMaxZ, Math.max(box.getPos1().getZ(), box.getPos2().getZ()));
        }
        if (iMinX == Integer.MAX_VALUE) return null;

        BlockPos min = new BlockPos(iMinX, iMinY, iMinZ);
        BlockPos max = new BlockPos(iMaxX, iMaxY, iMaxZ);

        return new Loaded(placement.getName(), new WorldBackedSchematic(min, max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1), min);
    }

    private static final class WorldBackedSchematic implements IStaticSchematic {
        private final BlockPos m_origin;
        private final int m_iWidth;
        private final int m_iHeight;
        private final int m_iLength;

        private WorldBackedSchematic(BlockPos origin, int iWidth, int iHeight, int iLength) {
            m_origin = origin;
            m_iWidth = iWidth;
            m_iHeight = iHeight;
            m_iLength = iLength;
        }

        @Override
        public BlockState getDirect(int x, int y, int z) {
            WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
            if (world == null) return Blocks.AIR.defaultBlockState();
            return world.getBlockState(m_origin.offset(x, y, z));
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
}
