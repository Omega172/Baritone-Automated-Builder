package xyz.omegaware.builder.step;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import xyz.omegaware.builder.util.Reach;

import java.util.function.Supplier;

public class OpenContainerStep implements Step {
    private final Supplier<BlockPos> m_posSupplier;
    private int m_iTicks = 0;
    private int m_iAttempts = 0;

    public OpenContainerStep(Supplier<BlockPos> posSupplier) {
        m_posSupplier = posSupplier;
    }

    @Override
    public String name() {
        return "OpenContainer";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 10;
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        BlockPos pos = m_posSupplier.get();
        if (player == null || mc.gameMode == null || pos == null) return Result.FAILED;

        if (player.containerMenu != player.inventoryMenu) return Result.DONE;
        if (!Reach.inReach(player, pos)) return Result.FAILED;

        m_iTicks++;
        if (m_iTicks == 1 || (m_iTicks % 20 == 0 && m_iAttempts < 3)) {
            player.setShiftKeyDown(false);
            Reach.lookAt(player, Vec3.atCenterOf(pos));
            InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, Reach.hitFor(player, pos));
            if (result.consumesAction()) Reach.swing(player);
            m_iAttempts++;
        }
        return Result.RUNNING;
    }
}
