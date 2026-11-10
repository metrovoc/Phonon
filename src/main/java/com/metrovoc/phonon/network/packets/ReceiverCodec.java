package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.audio.Receiver;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** A speaker by its position, or a boombox by its id. */
public final class ReceiverCodec {
    private ReceiverCodec() {}

    public static final StreamCodec<ByteBuf, Receiver> STREAM_CODEC = StreamCodec.of(
        (buf, receiver) -> {
            switch (receiver) {
                case Receiver.Block block -> {
                    buf.writeBoolean(true);
                    buf.writeLong(block.pos().asLong());
                }
                case Receiver.Boombox boombox -> {
                    buf.writeBoolean(false);
                    buf.writeLong(boombox.id().getMostSignificantBits());
                    buf.writeLong(boombox.id().getLeastSignificantBits());
                }
            }
        },
        buf -> buf.readBoolean()
            ? new Receiver.Block(BlockPos.of(buf.readLong()))
            : new Receiver.Boombox(new UUID(buf.readLong(), buf.readLong()))
    );
}
