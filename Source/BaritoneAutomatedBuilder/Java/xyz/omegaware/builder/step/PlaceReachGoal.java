package xyz.omegaware.builder.step;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;

public class PlaceReachGoal implements Goal {
    private final BlockPos m_target;
    private final int m_iRangeSq;

    public PlaceReachGoal(BlockPos target, int iRange) {
        m_target = target;
        m_iRangeSq = iRange * iRange;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        int dx = x - m_target.getX();
        int dy = y - m_target.getY();
        int dz = z - m_target.getZ();
        if (dx == 0 && dz == 0 && (dy == 0 || dy == -1)) return false;
        return dx * dx + dy * dy + dz * dz <= m_iRangeSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        return GoalBlock.calculate(x - m_target.getX(), y - m_target.getY(), z - m_target.getZ());
    }
}
