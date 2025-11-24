package com.tovkaic.phonon.network.packets;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * UUID codec for network serialization.
 */
public class UUIDCodec {
    public static final Codec<UUID> CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final StreamCodec<ByteBuf, UUID> STREAM_CODEC = StreamCodec.of(
        (buf, uuid) -> {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        },
        buf -> new UUID(buf.readLong(), buf.readLong())
    );
}
