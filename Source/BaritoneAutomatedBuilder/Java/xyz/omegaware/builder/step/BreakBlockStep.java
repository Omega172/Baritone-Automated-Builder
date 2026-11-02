package xyz.omegaware.builder.step;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import xyz.omegaware.builder.util.Log;
import xyz.omegaware.builder.util.Reach;

import java.util.function.Supplier;

public class BreakBlockStep implements Step {
    private final Supplier<BlockPos> m_posSupplier;
    private boolean m_bToolSelected = false;
    private int m_iTicks = 0;

    public BreakBlockStep(Supplier<BlockPos> posSupplier) {
        m_posSupplier = posSupplier;
    }

    @Override
    public String name() {
        return "BreakBlock";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 15;
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        BlockPos pos = m_posSupplier.get();
        if (player == null || level == null || mc.gameMode == null || pos == null) return Result.FAILED;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return Result.DONE;
        if (!Reach.inReach(player, pos)) return Result.FAILED;

        if (!m_bToolSelected) {
            float flBest = 0.0f;
            int iBest = player.getInventory().getSelectedSlot();
            for (int i = 0; i < 9; i++) {
                float flSpeed = player.getInventory().getItem(i).getDestroySpeed(state);
                if (flSpeed > flBest) {
                    flBest = flSpeed;
                    iBest = i;
                }
            }
            player.getInventory().setSelectedSlot(iBest);
            m_bToolSelected = true;
            return Result.RUNNING;
        }

        Reach.lookAt(player, Vec3.atCenterOf(pos));
        mc.options.keyAttack.setDown(true);
        if (m_iTicks++ % 20 == 0) {
            Log.LOGGER.info("BreakBlock tick {} pos {} destroying {} crosshair {} held {}", m_iTicks, pos.toShortString(), mc.gameMode.isDestroying(), mc.hitResult, player.getInventory().getSelectedItem().getItem().getDescriptionId());
        }
        return Result.RUNNING;
    }

    @Override
    public void stop() {
        Minecraft.getInstance().options.keyAttack.setDown(false);
    }
}
