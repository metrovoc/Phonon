package com.metrovoc.phonon.mixin;

import com.metrovoc.phonon.block.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.allay.Allay;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allays hard-code the jukebox block as what keeps them dancing; a playing speaker counts too. */
@Mixin(Allay.class)
abstract class AllayMixin {
    @Shadow
    private @Nullable BlockPos jukeboxPos;

    @Inject(method = "shouldStopDancing", at = @At("HEAD"), cancellable = true)
    private void phonon$keepDancingToSpeaker(CallbackInfoReturnable<Boolean> result) {
        Allay allay = (Allay) (Object) this;
        if (jukeboxPos != null && jukeboxPos.closerToCenterThan(allay.position(), 10.0)
            && allay.level().getBlockEntity(jukeboxPos) instanceof SpeakerBlockEntity speaker
            && speaker.getPlayback().isPlaying()) {
            result.setReturnValue(false);
        }
    }
}
