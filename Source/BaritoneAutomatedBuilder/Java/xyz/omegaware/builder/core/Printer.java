package xyz.omegaware.builder.core;

// Placement rules, the air place approach and the option set are modelled on kkllffaa meteor-litematica-printer
// (GPL-3.0, https://github.com/kkllffaa/meteor-litematica-printer).
// This is an independent reimplementation that works from the build session instead of the Litematica world.

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.omegaware.builder.util.Inv;
import xyz.omegaware.builder.util.Log;
import xyz.omegaware.builder.util.Reach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Printer {
    private record Pending(BlockPos pos, BlockState desired, boolean bAir, int iPlacedTick) {}

    private record Placement(BlockHitResult hit, float flYaw, float flPitch) {}

    private static final List<Property<?>> ORIENTATION = List.of(
        BlockStateProperties.FACING,
        BlockStateProperties.HORIZONTAL_FACING,
        BlockStateProperties.FACING_HOPPER,
        BlockStateProperties.ROTATION_16,
        BlockStateProperties.ATTACH_FACE,
        BlockStateProperties.AXIS,
        BlockStateProperties.HORIZONTAL_AXIS,
        BlockStateProperties.HALF,
        BlockStateProperties.SLAB_TYPE);

    private static int s_iTimer = 0;
    private static int s_iTick = 0;
    private static int s_iRotateWait = 0;
    private static int s_iAirFailures = 0;
    private static boolean s_bAirConfirmed = false;
    private static boolean s_bAirDisabled = false;
    private static boolean s_bWarnedBackend = false;
    private static final List<Pending> s_aPending = new ArrayList<>();
    private static final Map<BlockPos, Integer> s_aBadPlacements = new HashMap<>();

    private Printer() {}

    public static void reset() {
        s_iAirFailures = 0;
        s_iRotateWait = 0;
        s_bAirConfirmed = false;
        s_bAirDisabled = false;
        s_aPending.clear();
        s_aBadPlacements.clear();
        MeteorInterop.release();
    }

    public static void release() {
        MeteorInterop.release();
        s_aPending.clear();
    }

    public static void assist(BuildSession session, BlockPos pos) {
        if (!Config.bPrinter) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) return;
        verifyPlacements(level);
        if (player.containerMenu != player.inventoryMenu) return;
        if (s_iRotateWait > 0) {
            s_iRotateWait--;
            return;
        }

        BlockState desired = session.desiredState(pos);
        if (desired == null || !supported(desired)) return;
        BlockState current = level.getBlockState(pos);
        if (!current.canBeReplaced() || current.getBlock() == desired.getBlock()) return;
        if (!desired.canSurvive(level, pos) || player.getBoundingBox().intersects(new AABB(pos))) return;
        place(mc, player, level, pos, desired, Config.bPrinterRotate);
    }

    public static String debug() {
        return "rotateWait " + s_iRotateWait + " pending " + s_aPending.size() + " bad " + s_aBadPlacements.size() + " airDisabled " + s_bAirDisabled;
    }

    public static boolean airPlaceActive() {
        return switch (Config.eAirPlace) {
            case OFF -> false;
            case ON -> true;
            case AUTO -> !s_bAirDisabled;
        };
    }

    private static boolean useMeteorPrinter(BuildSession session) {
        if (Config.ePrinterBackend == Config.PrinterBackend.BUILTIN) return false;
        if (!session.usesLitematica()) return false;
        if (MeteorInterop.hasMeteorPrinter()) return true;
        if (Config.ePrinterBackend == Config.PrinterBackend.METEOR && !s_bWarnedBackend) {
            s_bWarnedBackend = true;
            Log.chat("Meteor printer backend requested but not available, using the built-in printer.");
        }
        return false;
    }

    private static void verifyPlacements(ClientLevel level) {
        s_iTick++;
        s_aPending.removeIf(pending -> {
            BlockState current = level.getBlockState(pending.pos());
            if (matches(pending.desired(), current)) {
                if (pending.bAir()) {
                    s_bAirConfirmed = true;
                    s_iAirFailures = 0;
                }
                return true;
            }
            if (current.getBlock() == pending.desired().getBlock()) {
                s_aBadPlacements.merge(pending.pos().immutable(), 1, Integer::sum);
                Log.LOGGER.warn("Placed {} at {} with the wrong orientation, wanted {}", current, pending.pos().toShortString(), pending.desired());
                return true;
            }
            if (s_iTick - pending.iPlacedTick() < 10) return false;
            if (pending.bAir() && !s_bAirConfirmed && ++s_iAirFailures >= 3 && !s_bAirDisabled) {
                s_bAirDisabled = true;
                Log.chat("Air place was rejected 3 times, turning it off for this build.");
            }
            return true;
        });
    }

    public static void tick(BuildSession session) {
        if (!Config.bPrinter) {
            MeteorInterop.release();
            return;
        }

        if (useMeteorPrinter(session)) {
            MeteorInterop.setPrinterActive(true);
            return;
        }
        MeteorInterop.release();

        Minecraft mcCheck = Minecraft.getInstance();
        if (mcCheck.level != null) verifyPlacements(mcCheck.level);
        if (++s_iTimer <= Config.iPrinterDelayTicks) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) return;
        if (player.containerMenu != player.inventoryMenu) return;
        if (s_iRotateWait > 0) {
            s_iRotateWait--;
            return;
        }

        List<BlockPos> aCandidates = collect(player, level, session);
        sort(aCandidates, player);

        int iPlaced = 0;
        for (BlockPos pos : aCandidates) {
            BlockState desired = session.desiredState(pos);
            if (desired != null && place(mc, player, level, pos, desired, Config.bPrinterRotate)) {
                s_iTimer = 0;
                if (s_iRotateWait > 0 || ++iPlaced >= Config.iPrinterBlocksPerTick) return;
            }
        }
    }

    private static List<BlockPos> collect(LocalPlayer player, ClientLevel level, BuildSession session) {
        List<BlockPos> aCandidates = new ArrayList<>();
        double dRange = Config.dPrinterRange;
        int iRadius = (int) Math.ceil(dRange);
        BlockPos center = player.blockPosition();
        Vec3 eye = player.getEyePosition();

        for (int dx = -iRadius; dx <= iRadius; dx++) {
            for (int dy = -iRadius; dy <= iRadius; dy++) {
                for (int dz = -iRadius; dz <= iRadius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (eye.distanceToSqr(Vec3.atCenterOf(pos)) > dRange * dRange) continue;

                    BlockState desired = session.desiredState(pos);
                    if (desired == null || !supported(desired)) continue;

                    BlockState current = level.getBlockState(pos);
                    if (!current.canBeReplaced() || current.getBlock() == desired.getBlock()) continue;
                    if (!desired.getFluidState().isEmpty()) continue;
                    if (!desired.canSurvive(level, pos)) continue;
                    if (player.getBoundingBox().intersects(new AABB(pos))) continue;
                    if (s_aBadPlacements.getOrDefault(pos, 0) >= 2) continue;
                    aCandidates.add(pos);
                }
            }
        }
        return aCandidates;
    }

    private static void sort(List<BlockPos> aCandidates, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Comparator<BlockPos> byDistance = Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos)));
        switch (Config.ePrinterSort) {
            case NEAREST -> aCandidates.sort(byDistance);
            case FURTHEST -> aCandidates.sort(byDistance.reversed());
            case TOP_DOWN -> aCandidates.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparing(byDistance));
            case DOWN_TOP -> aCandidates.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).thenComparing(byDistance));
            default -> {}
        }
    }

    private static boolean supported(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false;
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return false;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return false;
        return state.getBlock().asItem() != Items.AIR;
    }

    private static boolean hasOrientation(BlockState state) {
        for (Property<?> property : ORIENTATION) {
            if (state.hasProperty(property)) return true;
        }
        return false;
    }

    private static boolean matches(BlockState wanted, BlockState got) {
        if (got == null || got.getBlock() != wanted.getBlock()) return false;
        for (Property<?> property : ORIENTATION) {
            if (wanted.hasProperty(property) && (!got.hasProperty(property) || !wanted.getValue(property).equals(got.getValue(property)))) return false;
        }
        return true;
    }

    private static boolean place(Minecraft mc, LocalPlayer player, ClientLevel level, BlockPos pos, BlockState desired, boolean bRotate) {
        Item item = desired.getBlock().asItem();
        if (Config.bPrinterDirtAsGrass && item == Items.GRASS_BLOCK) item = Items.DIRT;

        Placement placement = findPlacement(player, level, pos, desired, item, bRotate);
        if (placement == null) return false;
        BlockHitResult hit = placement.hit();
        if (bRotate) {
            boolean bChanged = Math.abs(Mth.wrapDegrees(player.getYRot() - placement.flYaw())) > 0.01f || Math.abs(player.getXRot() - placement.flPitch()) > 0.01f;
            player.setYRot(placement.flYaw());
            player.setXRot(placement.flPitch());
            player.setYHeadRot(placement.flYaw());
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(placement.flYaw(), placement.flPitch(), player.onGround(), player.horizontalCollision));
            }
            if (bChanged && hasOrientation(desired)) {
                s_iRotateWait = 3;
                return true;
            }
        }
        int iPreviousSlot = player.getInventory().getSelectedSlot();
        if (!equip(mc, player, item)) return false;
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.getInventory().setSelectedSlot(iPreviousSlot);
        if (!result.consumesAction()) return false;
        if (Config.bPrinterSwing) Reach.swing(player);
        s_aPending.add(new Pending(pos.immutable(), desired, hit.getBlockPos().equals(pos), s_iTick));
        return true;
    }

    private static boolean equip(Minecraft mc, LocalPlayer player, Item item) {
        if (player.getInventory().getSelectedItem().getItem() == item) return true;

        for (int i = 0; i < Inv.iMainSize; i++) {
            if (player.getInventory().getItem(i).getItem() == item) return Inv.selectInHotbar(i);
        }

        if (player.isCreative() && mc.getConnection() != null) {
            int iSlot = player.getInventory().getSelectedSlot();
            ItemStack stack = new ItemStack(item);
            player.getInventory().setItem(iSlot, stack);
            mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short) (36 + iSlot), stack));
            return true;
        }
        return false;
    }

    private static List<BlockHitResult> candidateHits(LocalPlayer player, ClientLevel level, BlockPos pos, boolean bOriented) {
        Vec3 eye = player.getEyePosition();
        double dRangeSqr = Config.dPrinterRange * Config.dPrinterRange;
        double[] aFractions = bOriented ? new double[] {0.5, 0.25, 0.75} : new double[] {0.5};
        List<BlockHitResult> aHits = new ArrayList<>();

        for (Direction toNeighbor : Direction.values()) {
            BlockPos neighborPos = pos.relative(toNeighbor);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.isAir() || neighbor.canBeReplaced() || !neighbor.getFluidState().isEmpty()) continue;
            if (neighbor.getBlock() instanceof EntityBlock) continue;
            addHits(aHits, eye, dRangeSqr, neighborPos, toNeighbor.getOpposite(), aFractions);
        }
        if (airPlaceActive()) {
            for (Direction face : Direction.values()) addHits(aHits, eye, dRangeSqr, pos, face, aFractions);
        }
        return aHits;
    }

    private static void addHits(List<BlockHitResult> aHits, Vec3 eye, double dRangeSqr, BlockPos target, Direction face, double[] aFractions) {
        for (double dFraction : aFractions) {
            if (face.getAxis().isVertical() && dFraction != 0.5) continue;
            Vec3 hitVec = new Vec3(
                target.getX() + 0.5 + face.getStepX() * 0.5,
                target.getY() + (face.getAxis().isVertical() ? 0.5 + face.getStepY() * 0.5 : dFraction),
                target.getZ() + 0.5 + face.getStepZ() * 0.5);
            if (eye.distanceToSqr(hitVec) > dRangeSqr) continue;
            aHits.add(new BlockHitResult(hitVec, face, target, false));
        }
    }

    private static List<float[]> candidateRotations(LocalPlayer player, BlockState desired, Vec3 hitVec, boolean bOriented, boolean bRotate) {
        List<float[]> aRotations = new ArrayList<>();
        if (!bRotate) {
            aRotations.add(new float[] {player.getYRot(), player.getXRot()});
            return aRotations;
        }
        aRotations.add(Reach.rotationTo(player.getEyePosition(), hitVec));
        if (!bOriented) return aRotations;

        int iYawSteps = desired.hasProperty(BlockStateProperties.ROTATION_16) ? 16 : 4;
        for (float flPitch : new float[] {0.0f, 90.0f, -90.0f}) {
            for (int i = 0; i < iYawSteps; i++) aRotations.add(new float[] {i * (360.0f / iYawSteps), flPitch});
        }
        return aRotations;
    }

    private static BlockState simulate(LocalPlayer player, ClientLevel level, BlockState desired, Item item, BlockHitResult hit, float flYaw, float flPitch) {
        float flOldYaw = player.getYRot();
        float flOldPitch = player.getXRot();
        float flOldHead = player.getYHeadRot();
        player.setYRot(flYaw);
        player.setXRot(flPitch);
        player.setYHeadRot(flYaw);
        try {
            return desired.getBlock().getStateForPlacement(new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(item), hit));
        } finally {
            player.setYRot(flOldYaw);
            player.setXRot(flOldPitch);
            player.setYHeadRot(flOldHead);
        }
    }

    private static Placement findPlacement(LocalPlayer player, ClientLevel level, BlockPos pos, BlockState desired, Item item, boolean bRotate) {
        boolean bOriented = hasOrientation(desired);
        for (BlockHitResult hit : candidateHits(player, level, pos, bOriented)) {
            for (float[] aRotation : candidateRotations(player, desired, hit.getLocation(), bOriented, bRotate)) {
                if (!bOriented || matches(desired, simulate(player, level, desired, item, hit, aRotation[0], aRotation[1]))) {
                    return new Placement(hit, aRotation[0], aRotation[1]);
                }
            }
        }
        return null;
    }
}
