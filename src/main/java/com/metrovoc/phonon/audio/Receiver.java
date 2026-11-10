package com.metrovoc.phonon.audio;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Something that plays a station out loud: a speaker where it stands, or a boombox wherever it is. */
public sealed interface Receiver {
    record Block(BlockPos pos) implements Receiver {
        public Block {
            pos = pos.immutable();
        }
    }

    record Boombox(UUID id) implements Receiver {}
}
