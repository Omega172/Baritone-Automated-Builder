package xyz.omegaware.builder.step;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import xyz.omegaware.builder.util.Inv;

import java.util.List;
import java.util.function.Supplier;

public class PickupStep implements Step {
    private final Supplier<BlockPos> m_posSupplier;
    private int m_iTicks = 0;

    public PickupStep(Supplier<BlockPos> posSupplier) {
        m_posSupplier = posSupplier;
    }

    @Override
    public String name() {
        return "PickupShulker";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 20;
    }

    private IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        BlockPos pos = m_posSupplier.get();
        if (mc.player == null || level == null || pos == null) return Result.FAILED;

        m_iTicks++;
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8.0),
            entity -> Inv.isShulker(entity.getItem()));

        if (drops.isEmpty()) return m_iTicks > 10 ? Result.DONE : Result.RUNNING;

        if (!baritone().getPathingBehavior().isPathing() && m_iTicks % 20 == 0) {
            baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(drops.getFirst().blockPosition()));
        }
        return Result.RUNNING;
    }

    @Override
    public void stop() {
        baritone().getPathingBehavior().cancelEverything();
    }
}
