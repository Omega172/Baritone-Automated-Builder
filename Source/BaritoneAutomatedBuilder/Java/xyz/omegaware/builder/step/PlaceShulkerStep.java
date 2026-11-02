package xyz.omegaware.builder.step;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import xyz.omegaware.builder.util.Inv;
import xyz.omegaware.builder.util.Log;
import xyz.omegaware.builder.util.Reach;

public class PlaceShulkerStep implements Step {
    private final Item m_item;
    private final Blackboard m_blackboard;
    private BlockPos m_pos = null;
    private int m_iWait = 0;
    private int m_iTicks = 0;
    private int m_iRejected = 0;
    private int m_iSelectRetries = 0;
    private final java.util.Set<BlockPos> m_aBadSpots = new java.util.HashSet<>();

    public PlaceShulkerStep(Item item, Blackboard blackboard) {
        m_item = item;
        m_blackboard = blackboard;
    }

    @Override
    public String name() {
        return "PlaceShulker";
    }

    @Override
    public int timeoutTicks() {
        return 20 * 10;
    }

    private BlockPos findSpot(LocalPlayer player, ClientLevel level) {
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double dBest = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (m_aBadSpots.contains(candidate)) continue;
                    if (!level.getBlockState(candidate).canBeReplaced()) continue;
                    if (!level.getBlockState(candidate.above()).isAir()) continue;
                    if (!level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)) continue;
                    if (player.getBoundingBox().intersects(new AABB(candidate))) continue;
                    if (!Reach.inReach(player, candidate.below())) continue;
                    double dDist = candidate.distSqr(origin);
                    if (dDist < dBest) {
                        dBest = dDist;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) return Result.FAILED;

        if (m_pos != null && level.getBlockState(m_pos).getBlock() instanceof ShulkerBoxBlock) {
            m_blackboard.placedShulker = m_pos;
            return Result.DONE;
        }

        if (m_pos == null) {
            int iIndex = Inv.findShulkerWith(m_item);
            if (iIndex < 0) {
                Log.LOGGER.info("PlaceShulker found no shulker holding {}", m_item.getDescriptionId());
                return Result.FAILED;
            }
            m_pos = findSpot(player, level);
            if (m_pos == null) {
                Log.LOGGER.info("PlaceShulker found no free spot near {}", player.blockPosition().toShortString());
                return Result.FAILED;
            }
            Inv.selectInHotbar(iIndex);
            m_iWait = 3;
            return Result.RUNNING;
        }

        if (m_iWait-- > 0) return Result.RUNNING;
        if (!Inv.isShulker(player.getInventory().getSelectedItem())) {
            int iIndex = Inv.findShulkerWith(m_item);
            StringBuilder strHotbar = new StringBuilder();
            for (int i = 0; i < 9; i++) strHotbar.append(player.getInventory().getItem(i).getItem().getDescriptionId()).append(' ');
            Log.LOGGER.info("PlaceShulker select retry {} index {} selected slot {} hotbar {} menu {}", m_iSelectRetries, iIndex, player.getInventory().getSelectedSlot(), strHotbar, player.containerMenu == player.inventoryMenu);
            if (iIndex < 0 || ++m_iSelectRetries > 6) return Result.FAILED;
            Inv.selectInHotbar(iIndex);
            m_iWait = 4;
            return Result.RUNNING;
        }

        if (m_iTicks++ % 10 == 0) {
            BlockPos floor = m_pos.below();
            Vec3 hit = new Vec3(m_pos.getX() + 0.5, m_pos.getY(), m_pos.getZ() + 0.5);
            Reach.lookAt(player, hit);
            var result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, floor, false));
            Log.LOGGER.info("PlaceShulker attempt {} at {} floor {} result {} held {} spot {} floorState {} entities {}", m_iTicks, m_pos.toShortString(), floor.toShortString(), result, player.getInventory().getSelectedItem().getItem().getDescriptionId(), level.getBlockState(m_pos), level.getBlockState(floor), level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(m_pos), entity -> true).size());
            Reach.swing(player);
            if (!result.consumesAction() && ++m_iRejected >= 3) {
                m_aBadSpots.add(m_pos);
                m_pos = null;
                m_iRejected = 0;
                m_iTicks = 0;
            }
        }
        return Result.RUNNING;
    }
}
