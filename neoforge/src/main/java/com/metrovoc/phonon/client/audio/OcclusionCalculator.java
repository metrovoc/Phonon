package com.metrovoc.phonon.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Calculates audio occlusion by ray-marching from listener to source.
 *
 * Simple algorithm: count solid blocks between listener and source,
 * reduce gain exponentially per block.
 */
public class OcclusionCalculator {
    private static final float OCCLUSION_PER_BLOCK = 0.7f;
    private static final float MIN_GAIN = 0.05f;
    private static final int MAX_BLOCKS_CHECK = 32;

    private OcclusionCalculator() {}

    public static float calculate(Vec3 listener, Vec3 source, BlockPos sourceBlockPos) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return 1.0f;

        Vec3 direction = source.subtract(listener);
        double distance = direction.length();
        if (distance < 0.5) return 1.0f;

        direction = direction.normalize();
        int blocksHit = 0;
        Vec3 current = listener;
        double step = 0.5;
        double traveled = 0;

        while (traveled < distance && blocksHit < MAX_BLOCKS_CHECK) {
            current = listener.add(direction.scale(traveled));
            BlockPos blockPos = BlockPos.containing(current);

            if (blockPos.equals(sourceBlockPos)) {
                traveled += step;
                continue;
            }

            BlockState state = level.getBlockState(blockPos);

            if (isOccluding(state, level, blockPos)) {
                blocksHit++;
                traveled += 1.0;
            } else {
                traveled += step;
            }
        }

        if (blocksHit == 0) return 1.0f;

        float gain = (float) Math.pow(OCCLUSION_PER_BLOCK, blocksHit);
        return Math.max(gain, MIN_GAIN);
    }

    private static boolean isOccluding(BlockState state, Level level, BlockPos pos) {
        if (state.isAir()) return false;
        return state.isSolidRender(level, pos);
    }
}
