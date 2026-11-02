package xyz.omegaware.builder.step;

import net.minecraft.client.Minecraft;

public class CloseContainerStep implements Step {
    private int m_iTicks = 0;

    @Override
    public String name() {
        return "CloseContainer";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 5;
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Result.FAILED;
        if (mc.player.containerMenu == mc.player.inventoryMenu) return Result.DONE;
        if (m_iTicks++ == 0) mc.player.closeContainer();
        return Result.RUNNING;
    }
}
