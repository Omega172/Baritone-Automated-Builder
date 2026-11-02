package xyz.omegaware.builder.step;

import xyz.omegaware.builder.storage.ShulkerLedger;
import xyz.omegaware.builder.util.Log;

import java.util.Map;

public class LedgerUpdateStep implements Step {
    private final Blackboard m_blackboard;

    public LedgerUpdateStep(Blackboard blackboard) {
        m_blackboard = blackboard;
    }

    @Override
    public String name() {
        return "LedgerUpdate";
    }

    @Override
    public Result tick() {
        if (m_blackboard.shulkerBefore != null && m_blackboard.shulkerAfter != null) {
            ShulkerLedger.Entry entry = ShulkerLedger.get().matching(m_blackboard.shulkerBefore);
            if (entry != null) entry.contents = Map.copyOf(m_blackboard.shulkerAfter);
            ShulkerLedger.iUseCycles++;
            Log.chat("Shulker cycle " + ShulkerLedger.iUseCycles + " done: " + m_blackboard.shulkerBefore.size() + " item types before, " + m_blackboard.shulkerAfter.size() + " after.");
        }
        return Result.DONE;
    }
}
