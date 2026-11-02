package xyz.omegaware.builder.step;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

public class PathStep implements Step {
    private final String m_strName;
    private final Supplier<Goal> m_goalSupplier;
    private int m_iIdleTicks = 0;
    private int m_iReissues = 0;
    private Goal m_goal;

    public PathStep(String strName, Supplier<Goal> goalSupplier) {
        m_strName = strName;
        m_goalSupplier = goalSupplier;
    }

    @Override
    public String name() {
        return "Path: " + m_strName;
    }

    @Override
    public int timeoutTicks() {
        return 20 * 120;
    }

    private IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    @Override
    public void start() {
        m_goal = m_goalSupplier.get();
        baritone().getCustomGoalProcess().setGoalAndPath(m_goal);
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Result.FAILED;
        boolean bPathing = baritone().getPathingBehavior().isPathing();
        if (m_goal.isInGoal(mc.player.blockPosition()) && !bPathing) return Result.DONE;

        if (!bPathing) {
            if (++m_iIdleTicks > 40) {
                if (++m_iReissues > 3) return Result.FAILED;
                m_iIdleTicks = 0;
                baritone().getCustomGoalProcess().setGoalAndPath(m_goal);
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
