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
import xyz.omegaware.builder.util.Log;
import xyz.omegaware.builder.util.Reach;

import java.util.List;
import java.util.function.Supplier;

public class PickupStep implements Step {
    private final Supplier<BlockPos> m_posSupplier;
    private int m_iTicks = 0;
    private boolean m_bWalking = false;

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

        if (drops.isEmpty()) {
            releaseKeys();
            return m_iTicks > 10 ? Result.DONE : Result.RUNNING;
        }

        if (m_iTicks % 40 == 0) {
            ItemEntity drop = drops.getFirst();
            Log.LOGGER.info("PickupShulker waiting {} ticks, drops {}, drop at {} age {}, player at {}, pathing {}, free slots {}",
                m_iTicks, drops.size(), drop.position(), drop.tickCount, mc.player.position(), baritone().getPathingBehavior().isPathing(), Inv.freeSlots());
        }
        if (!baritone().getPathingBehavior().isPathing() && m_iTicks % 20 == 0) {
            baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(drops.getFirst().blockPosition()));
        }
        if (m_iTicks > 60 && !baritone().getPathingBehavior().isPathing()) {
            ItemEntity drop = drops.getFirst();
            Reach.lookAt(mc.player, drop.position());
            mc.player.setXRot(0.0f);
            mc.options.keyUp.setDown(true);
            mc.options.keyJump.setDown(mc.player.horizontalCollision);
            m_bWalking = true;
        }
        return Result.RUNNING;
    }

    private void releaseKeys() {
        if (!m_bWalking) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        m_bWalking = false;
    }

    @Override
    public void stop() {
        releaseKeys();
        baritone().getPathingBehavior().cancelEverything();
    }
}
