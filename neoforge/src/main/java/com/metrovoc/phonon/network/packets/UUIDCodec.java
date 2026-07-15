package com.metrovoc.phonon.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * UUID codec for network serialization.
 * Supports nullable UUIDs (used for stopped playback state).
 */
public class UUIDCodec {
    public static final StreamCodec<ByteBuf, UUID> STREAM_CODEC = StreamCodec.of(
        (buf, uuid) -> {
            if (uuid == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
            }
        },
        buf -> {
            boolean present = buf.readBoolean();
            if (!present) {
                return null;
            }
            return new UUID(buf.readLong(), buf.readLong());
        }
    );
}
