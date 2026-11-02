package xyz.omegaware.builder.core;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.schematic.IStaticSchematic;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.omegaware.builder.step.AssistPlaceStep;
import xyz.omegaware.builder.step.BreakBlockStep;
import xyz.omegaware.builder.step.Blackboard;
import xyz.omegaware.builder.step.CloseContainerStep;
import xyz.omegaware.builder.step.OpenContainerStep;
import xyz.omegaware.builder.step.PathStep;
import xyz.omegaware.builder.step.PickupStep;
import xyz.omegaware.builder.step.PlaceShulkerStep;
import xyz.omegaware.builder.step.Step;
import xyz.omegaware.builder.step.TransferStep;
import xyz.omegaware.builder.step.FetchShulkerStep;
import xyz.omegaware.builder.step.LedgerUpdateStep;
import xyz.omegaware.builder.step.ReturnShulkersStep;
import xyz.omegaware.builder.storage.ShulkerLedger;
import xyz.omegaware.builder.storage.StorageIndex;
import xyz.omegaware.builder.util.Inv;
import xyz.omegaware.builder.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;

public class Controller {
    public enum State { IDLE, BUILDING, RESUPPLY, RETURNING_HOME, RETURNING_SHULKERS, FINISHED, ABORTED }

    private static final Controller s_instance = new Controller();

    private State m_eState = State.IDLE;
    private BuildSession m_session = null;
    private final Deque<Step> m_aSteps = new ArrayDeque<>();
    private Step m_currentStep = null;
    private int m_iStepTicks = 0;
    private int m_iFailures = 0;
    private int m_iStuckTicks = 0;
    private int m_iStuckEvents = 0;
    private int m_iLastNeeded = -1;
    private BlockPos m_lastPos = null;
    private int m_iCooldown = 0;
    private int m_iTick = 0;
    private final Map<BlockPos, Integer> m_aAssisted = new HashMap<>();

    public static Controller get() {
        return s_instance;
    }

    private IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public boolean isResolved() {
        return m_session != null && m_session.hasCompletedPass() && m_session.isFullyResolved();
    }

    public Map<Item, Integer> needed() {
        return m_session == null ? Map.of() : m_session.needed();
    }

    public State state() {
        return m_eState;
    }

    public boolean isRunning() {
        return m_eState == State.BUILDING || m_eState == State.RESUPPLY || m_eState == State.RETURNING_HOME || m_eState == State.RETURNING_SHULKERS;
    }

    public String status() {
        if (m_session == null) return "Idle";
        String strStep = m_currentStep != null ? " (" + m_currentStep.name() + ")" : "";
        return m_eState + strStep + ", " + m_session.totalNeeded() + " blocks left, failures " + m_iFailures;
    }

    public boolean start(File file, BlockPos origin) {
        var format = BaritoneAPI.getProvider().getSchematicSystem().getByFile(file);
        if (format.isEmpty()) {
            Log.chat("Unsupported schematic format: " + file.getName());
            return false;
        }
        IStaticSchematic schematic;
        try (FileInputStream stream = new FileInputStream(file)) {
            schematic = format.get().parse(stream);
        } catch (Exception e) {
            Log.chat("Failed to load schematic: " + e.getMessage());
            return false;
        }

        return start(file.getName(), schematic, origin);
    }

    public boolean startLitematica(int iPlacement) {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) {
            Log.chat("Litematica is not installed.");
            return false;
        }
        LitematicaSource.Loaded loaded = LitematicaSource.load(iPlacement);
        if (loaded == null) {
            Log.chat("Litematica has no usable placement number " + (iPlacement + 1) + " (" + LitematicaSource.placementCount() + " loaded).");
            return false;
        }
        return start(loaded.strName(), loaded.schematic(), loaded.origin(), true);
    }

    public boolean start(String strName, IStaticSchematic schematic, BlockPos origin) {
        return start(strName, schematic, origin, false);
    }

    public boolean start(String strName, IStaticSchematic schematic, BlockPos origin, boolean bDynamic) {
        stop();
        Printer.reset();
        StorageIndex.get().ensureLoaded();
        BaritoneAPI.getSettings().allowInventory.value = true;
        m_session = new BuildSession(strName, schematic, origin, bDynamic);
        m_iFailures = 0;
        m_iStuckEvents = 0;
        m_aAssisted.clear();
        beginBuilding();
        Log.chat("Building " + strName + " at " + origin.toShortString());
        return true;
    }

    public void stop() {
        Printer.release();
        abandonSteps();
        baritone().getPathingBehavior().cancelEverything();
        m_session = null;
        m_eState = State.IDLE;
    }

    private void abort(String strReason) {
        abandonSteps();
        baritone().getPathingBehavior().cancelEverything();
        Printer.release();
        m_eState = State.ABORTED;
        Log.chat("Aborted: " + strReason);
    }

    private void abandonSteps() {
        if (m_currentStep != null) m_currentStep.stop();
        m_currentStep = null;
        m_aSteps.clear();
    }

    private void beginBuilding() {
        m_eState = State.BUILDING;
        Log.LOGGER.info("Begin building, {} blocks left", m_session.totalNeeded());
        m_iStuckTicks = 0;
        m_iCooldown = 20;
        baritone().getBuilderProcess().build(m_session.strName, m_session.schematic, m_session.origin);
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!isRunning() || mc.player == null || mc.level == null || mc.gameMode == null) return;
        m_iTick++;
        if (m_session != null) m_session.tick(mc.level);

        BaritoneAPI.getSettings().allowInventory.value = m_eState == State.BUILDING;
        if (m_eState == State.BUILDING) tickBuilding(mc.player);
        else tickSteps();
    }

    private void tickBuilding(LocalPlayer player) {
        if (m_iCooldown > 0) {
            m_iCooldown--;
            return;
        }

        Printer.tick(m_session);

        int iNeeded = m_session.totalNeeded();
        boolean bDone = m_session.hasCompletedPass() && m_session.isFullyResolved() && iNeeded == 0 && !baritone().getBuilderProcess().isActive();
        if (bDone) {
            Printer.release();
            baritone().getPathingBehavior().cancelEverything();
            List<Step> returns = Config.bReturnShulkersAtEnd ? planReturns(false) : new ArrayList<>();
            if (!returns.isEmpty()) {
                m_aSteps.clear();
                m_aSteps.addAll(returns);
                m_eState = State.RETURNING_SHULKERS;
                Log.chat("Build done, returning " + ShulkerLedger.get().all().size() + " shulker boxes.");
                return;
            }
            m_eState = State.FINISHED;
            baritone().getPathingBehavior().cancelEverything();
            Log.chat("Build finished.");
            return;
        }

        if (m_iTick % 20 != 0) return;

        if (Config.bAutoResupply && m_session.hasCompletedPass() && shortage()) {
            List<Step> plan = planResupply();
            if (plan.isEmpty()) {
                if (Inv.freeSlots() - Math.max(1, Config.iReservedSlots) <= 0) {
                    abort("inventory is full of items that are not needed, free some slots");
                } else {
                    abort("out of materials and no known source has them: " + describeShortage());
                }
                return;
            } else {
                baritone().getPathingBehavior().cancelEverything();
                m_aSteps.clear();
                m_aSteps.addAll(plan);
                m_eState = State.RESUPPLY;
                Log.chat("Resupplying: " + plan.size() + " steps planned.");
                return;
            }
        }

        if (Config.bPrinter && baritone().getBuilderProcess().isPaused() && m_session.hasCompletedPass()) {
            BlockPos target = m_session.nearestPending(player.blockPosition(),
                (pos, state) -> Inv.count(state.getBlock().asItem()) > 0 && m_aAssisted.getOrDefault(pos, 0) < 3);
            if (target != null) {
                m_aAssisted.merge(target, 1, Integer::sum);
                baritone().getPathingBehavior().cancelEverything();
                m_aSteps.clear();
                m_aSteps.add(new PathStep("place near " + target.toShortString(), () -> new GoalNear(target, 2)));
                m_aSteps.add(new AssistPlaceStep(m_session, target));
                m_eState = State.RESUPPLY;
                Log.chat("Baritone cannot place " + target.toShortString() + " from here, placing it with the printer.");
                return;
            }
        }

        BlockPos here = player.blockPosition();
        if (iNeeded < m_iLastNeeded) {
            m_iStuckEvents = 0;
            m_iFailures = 0;
        }
        if (iNeeded == m_iLastNeeded && here.equals(m_lastPos)) m_iStuckTicks += 20;
        else m_iStuckTicks = 0;
        m_iLastNeeded = iNeeded;
        m_lastPos = here;

        if (m_iStuckTicks >= Config.iStuckSeconds * 20) {
            if (++m_iStuckEvents > Config.iMaxFailures) {
                abort("stuck too many times");
                return;
            }
            Log.chat("Stuck, restarting the build (" + m_iStuckEvents + "/" + Config.iMaxFailures + ").");
            BlockPos home = StorageIndex.get().home();
            if (home != null) {
                baritone().getPathingBehavior().cancelEverything();
                m_aSteps.clear();
                m_aSteps.add(new PathStep("home", () -> new GoalBlock(home)));
                m_eState = State.RETURNING_HOME;
            } else {
                beginBuilding();
            }
        }
    }

    private void tickSteps() {
        if (m_currentStep == null) {
            m_currentStep = m_aSteps.pollFirst();
            if (m_currentStep == null) {
                finishSteps();
                return;
            }
            m_iStepTicks = 0;
            Log.LOGGER.info("Step start: {}", m_currentStep.name());
            m_currentStep.start();
        }

        m_iStepTicks++;
        Step.Result result = m_currentStep.tick();
        if (result == Step.Result.RUNNING && m_iStepTicks > m_currentStep.timeoutTicks()) result = Step.Result.FAILED;

        if (result == Step.Result.RUNNING) return;
        String strName = m_currentStep.name();
        Log.LOGGER.info("Step end: {} -> {} after {} ticks", strName, result, m_iStepTicks);
        m_currentStep.stop();
        m_currentStep = null;

        if (result == Step.Result.FAILED) {
            Log.chat("Step failed: " + strName);
            m_aSteps.clear();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
            if (++m_iFailures > Config.iMaxFailures) {
                abort("too many failed steps");
                return;
            }
            finishSteps();
        }
    }

    private void finishSteps() {
        if (m_eState == State.RETURNING_SHULKERS) {
            m_eState = State.FINISHED;
            Log.chat("Shulker boxes returned.");
            return;
        }
        if (m_eState == State.RESUPPLY) Log.chat("Resupply done.");
        beginBuilding();
    }

    public boolean returnShulkers() {
        if (ShulkerLedger.get().all().isEmpty()) {
            Log.chat("No shulker boxes to return.");
            return false;
        }
        List<Step> plan = planReturns(false);
        if (plan.isEmpty()) return false;
        abandonSteps();
        baritone().getPathingBehavior().cancelEverything();
        m_aSteps.addAll(plan);
        m_eState = State.RETURNING_SHULKERS;
        return true;
    }

    private boolean shortage() {
        var builder = baritone().getBuilderProcess();
        if (builder.isActive() && !builder.isPaused()) return false;
        for (Map.Entry<Item, Integer> entry : m_session.needed().entrySet()) {
            if (Inv.count(entry.getKey()) == 0) return true;
        }
        return false;
    }

    private String describeShortage() {
        StringBuilder sb = new StringBuilder();
        m_session.needed().forEach((item, iCount) -> {
            if (Inv.count(item) == 0) sb.append(item.getDescriptionId()).append(" x").append(iCount).append(' ');
        });
        return sb.toString().trim();
    }

    private List<Step> planReturns(boolean bOnlyEmpty) {
        List<Step> aPlan = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return aPlan;

        BlockPos dump = Config.eShulkerReturn == Config.ShulkerReturn.DUMP ? StorageIndex.get().dump() : null;
        if (Config.eShulkerReturn == Config.ShulkerReturn.DUMP && dump == null) {
            Log.chat("Shulker return is set to dump but no dump chest is set, returning to the origin chests. Use /autobuild setdump.");
        }

        Map<BlockPos, List<ShulkerLedger.Entry>> aByOrigin = new HashMap<>();
        for (ShulkerLedger.Entry entry : bOnlyEmpty ? ShulkerLedger.get().empties() : ShulkerLedger.get().all()) {
            aByOrigin.computeIfAbsent(dump != null ? dump : entry.origin, key -> new ArrayList<>()).add(entry);
        }
        boolean bKeepSlots = dump == null;

        BlockPos from = player.blockPosition();
        List<Map.Entry<BlockPos, List<ShulkerLedger.Entry>>> aOrder = new ArrayList<>(aByOrigin.entrySet());
        aOrder.sort(Comparator.comparingDouble(entry -> entry.getKey().distSqr(from)));
        for (Map.Entry<BlockPos, List<ShulkerLedger.Entry>> entry : aOrder) {
            BlockPos pos = entry.getKey();
            aPlan.add(new PathStep("return shulkers to " + pos.toShortString(), () -> new GoalGetToBlock(pos)));
            aPlan.add(new OpenContainerStep(() -> pos));
            aPlan.add(new ReturnShulkersStep(() -> pos, entry.getValue(), bKeepSlots));
            aPlan.add(new CloseContainerStep());
        }
        return aPlan;
    }

    private void addUseCycle(List<Step> aPlan, Item representative, Map<Item, Integer> aWanted) {
        Blackboard blackboard = new Blackboard();
        aPlan.add(new PlaceShulkerStep(representative, blackboard));
        aPlan.add(new OpenContainerStep(() -> blackboard.placedShulker));
        aPlan.add(new TransferStep(aWanted, () -> null, Config.iClickDelayTicks, blackboard, 1));
        aPlan.add(new CloseContainerStep());
        aPlan.add(new BreakBlockStep(() -> blackboard.placedShulker));
        aPlan.add(new PickupStep(() -> blackboard.placedShulker));
        aPlan.add(new LedgerUpdateStep(blackboard));
    }

    private List<Step> planResupply() {
        LocalPlayer player = Minecraft.getInstance().player;
        List<Step> aPlan = new ArrayList<>();
        if (player == null) return aPlan;

        if (Config.bReturnEmptyShulkers) aPlan.addAll(planReturns(true));

        Map<Item, Integer> aWanted = new HashMap<>();
        int iSlotBudget = Inv.freeSlots() - Math.max(1, Config.iReservedSlots);
        List<Map.Entry<Item, Integer>> aSorted = new ArrayList<>(m_session.needed().entrySet());
        aSorted.sort(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder()));

        for (Map.Entry<Item, Integer> entry : aSorted) {
            Item item = entry.getKey();
            int iMaxStack = new ItemStack(item).getMaxStackSize();
            int iTarget = Math.min(entry.getValue(), iMaxStack * Config.iMaxStacksPerItem);
            int iWant = iTarget - Inv.count(item);
            if (iWant <= 0) continue;
            int iStacks = (iWant + iMaxStack - 1) / iMaxStack;
            if (iStacks > iSlotBudget) {
                iWant = iSlotBudget * iMaxStack;
                iStacks = iSlotBudget;
            }
            if (iWant <= 0) continue;
            aWanted.put(item, iWant);
            iSlotBudget -= iStacks;
        }

        BlockPos from = player.blockPosition();
        Map<Integer, Map<Item, Integer>> aInventoryGroups = new LinkedHashMap<>();
        Map<String, Map<Item, Integer>> aFetchedGroups = new LinkedHashMap<>();
        Map<BlockPos, List<StorageIndex.ShulkerInfo>> aTakenInfos = new HashMap<>();
        Map<BlockPos, List<Item>> aFetchByChest = new HashMap<>();
        Map<BlockPos, Map<Item, Integer>> aLooseByChest = new HashMap<>();

        for (Map.Entry<Item, Integer> entry : aWanted.entrySet()) {
            Item item = entry.getKey();
            int iLeft = entry.getValue();

            int iShulker = Inv.findShulkerWith(item);
            if (iShulker >= 0) {
                aInventoryGroups.computeIfAbsent(iShulker, key -> new HashMap<>()).put(item, iLeft);
                continue;
            }

            List<StorageIndex.Entry> aShulkerSources = StorageIndex.get().shulkerSourcesFor(item, from);
            if (!aShulkerSources.isEmpty()) {
                StorageIndex.Entry source = aShulkerSources.getFirst();
                aFetchByChest.computeIfAbsent(source.pos, key -> new ArrayList<>()).add(item);

                List<StorageIndex.ShulkerInfo> aTaken = aTakenInfos.computeIfAbsent(source.pos, key -> new ArrayList<>());
                StorageIndex.ShulkerInfo shulker = null;
                for (StorageIndex.ShulkerInfo taken : aTaken) {
                    if (taken.contents().getOrDefault(item, 0) > 0) {
                        shulker = taken;
                        break;
                    }
                }
                if (shulker == null) {
                    for (StorageIndex.ShulkerInfo info : source.shulkers) {
                        if (info.contents().getOrDefault(item, 0) > 0) {
                            shulker = info;
                            aTaken.add(info);
                            break;
                        }
                    }
                }
                String strGroup = source.pos.asLong() + "#" + (shulker != null ? shulker.slot() : -1 - aFetchedGroups.size());
                aFetchedGroups.computeIfAbsent(strGroup, key -> new LinkedHashMap<>()).put(item, iLeft);
                continue;
            }

            for (StorageIndex.Entry source : StorageIndex.get().sourcesFor(item, from)) {
                if (iLeft <= 0) break;
                int iTake = Math.min(iLeft, source.contents.get(item));
                aLooseByChest.computeIfAbsent(source.pos, key -> new HashMap<>()).put(item, iTake);
                iLeft -= iTake;
            }
        }

        Set<BlockPos> aChests = new HashSet<>(aFetchByChest.keySet());
        aChests.addAll(aLooseByChest.keySet());
        List<BlockPos> aChestOrder = new ArrayList<>(aChests);
        aChestOrder.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
        for (BlockPos pos : aChestOrder) {
            aPlan.add(new PathStep("storage " + pos.toShortString(), () -> new GoalGetToBlock(pos)));
            aPlan.add(new OpenContainerStep(() -> pos));
            if (aFetchByChest.containsKey(pos)) aPlan.add(new FetchShulkerStep(() -> pos, aFetchByChest.get(pos), Config.iClickDelayTicks));
            if (aLooseByChest.containsKey(pos)) aPlan.add(new TransferStep(aLooseByChest.get(pos), () -> pos, Config.iClickDelayTicks, null, 1));
            aPlan.add(new CloseContainerStep());
        }

        for (Map<Item, Integer> aGroup : aInventoryGroups.values()) {
            addUseCycle(aPlan, aGroup.keySet().iterator().next(), aGroup);
        }
        for (Map<Item, Integer> aGroup : aFetchedGroups.values()) {
            addUseCycle(aPlan, aGroup.keySet().iterator().next(), aGroup);
        }
        return aPlan;
    }
}
