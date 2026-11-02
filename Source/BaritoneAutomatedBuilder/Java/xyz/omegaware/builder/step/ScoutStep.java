package xyz.omegaware.builder.step;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import xyz.omegaware.builder.storage.StorageIndex;
import xyz.omegaware.builder.util.Log;
import xyz.omegaware.builder.util.Storage;

public class ScoutStep implements Step {
    private final BlockPos m_pos;
    private boolean m_bGone = false;
    private int m_iIdleTicks = 0;
    private int m_iReissues = 0;

    public ScoutStep(BlockPos pos) {
        m_pos = pos;
    }

    @Override
    public String name() {
        return "Scout " + m_pos.toShortString();
    }

    @Override
    public int timeoutTicks() {
        return 20 * 90;
    }

    public boolean isGone() {
        return m_bGone;
    }

    private IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    @Override
    public void start() {
        ClientLevel level = Minecraft.getInstance().level;
        if (!Storage.canCheck(level, Minecraft.getInstance().player, m_pos)) baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(m_pos, 40));
    }

    @Override
    public Result tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return Result.FAILED;

        if (Storage.canCheck(level, Minecraft.getInstance().player, m_pos)) {
            if (!Storage.isStorage(level, m_pos)) {
                StorageIndex.get().forget(m_pos);
                Log.chat("The storage at " + m_pos.toShortString() + " is gone, removed it from the list.");
                m_bGone = true;
            }
            return Result.DONE;
        }

        if (!baritone().getPathingBehavior().isPathing()) {
            if (++m_iIdleTicks > 40) {
                if (++m_iReissues > 3) return Result.DONE;
                m_iIdleTicks = 0;
                baritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(m_pos, 40));
            }
        } else {
            m_iIdleTicks = 0;
        }
        return Result.RUNNING;
    }

    @Override
    public void stop() {
        baritone().getPathingBehavior().cancelEverything();
    }
}
