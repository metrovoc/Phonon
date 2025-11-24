package com.tovkaic.phonon.mixin;

import com.tovkaic.phonon.client.AudioCache;
import com.tovkaic.phonon.client.audio.PhononAudioStream;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public class MixinSoundBufferLibrary {

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void onGetStream(ResourceLocation resource, boolean isWrapper, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (resource.getNamespace().equals("phonon") && resource.getPath().startsWith("dynamic/")) {
            String uuidStr = resource.getPath().replace("dynamic/", "");
            try {
                UUID id = UUID.fromString(uuidStr);
                Optional<Path> cached = AudioCache.getInstance().getCachedAudio(id);
                
                if (cached.isPresent()) {
                    cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
                        try {
                            // TODO: Pass correct start offset if needed, for now 0
                            return new PhononAudioStream(cached.get(), 0);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, net.minecraft.Util.backgroundExecutor()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
