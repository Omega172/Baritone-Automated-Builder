package xyz.omegaware.builder.step;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import xyz.omegaware.builder.core.BuildSession;
import xyz.omegaware.builder.core.Printer;

public class AssistPlaceStep implements Step {
    private final BuildSession m_session;
    private final BlockPos m_pos;
    private int m_iTicks = 0;
    private int m_iSettle = 0;

    public AssistPlaceStep(BuildSession session, BlockPos pos) {
        m_session = session;
        m_pos = pos;
    }

    @Override
    public String name() {
        return "AssistPlace";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 20;
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Result.FAILED;

        Printer.assist(m_session, m_pos);

        BlockState desired = m_session.desiredState(m_pos);
        boolean bPlaced = desired == null || mc.level.getBlockState(m_pos).getBlock() == desired.getBlock();
        if (bPlaced) return ++m_iSettle > 10 ? Result.DONE : Result.RUNNING;
        return ++m_iTicks > 160 ? Result.DONE : Result.RUNNING;
    }
}
