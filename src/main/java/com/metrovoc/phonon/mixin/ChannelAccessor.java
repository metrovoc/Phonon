package com.metrovoc.phonon.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sound channel's OpenAL source, for muffling it. Remapped on every loader, unlike reflection by name. */
@Mixin(Channel.class)
public interface ChannelAccessor {
    @Accessor("source")
    int phonon$getSource();
}
