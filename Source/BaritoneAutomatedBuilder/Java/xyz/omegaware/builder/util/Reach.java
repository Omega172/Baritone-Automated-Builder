package xyz.omegaware.builder.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class Reach {
    public static final double dMaxReach = 4.4;

    private Reach() {}

    public static boolean inReach(LocalPlayer player, BlockPos pos) {
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= dMaxReach * dMaxReach;
    }

    public static void swing(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
    }

    public static float[] rotationTo(Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double dHorizontal = Math.sqrt(dx * dx + dz * dz);
        return new float[] {(float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float) -Math.toDegrees(Math.atan2(dy, dHorizontal))};
    }

    public static void lookAt(LocalPlayer player, Vec3 target) {
        float[] aRotation = rotationTo(player.getEyePosition(), target);
        player.setYRot(aRotation[0]);
        player.setXRot(aRotation[1]);
        player.setYHeadRot(aRotation[0]);
    }

    public static BlockHitResult hitFor(LocalPlayer player, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 eye = player.getEyePosition();
        Direction face = Direction.getApproximateNearest(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        Vec3 hit = center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockHitResult(hit, face, pos, false);
    }
}
