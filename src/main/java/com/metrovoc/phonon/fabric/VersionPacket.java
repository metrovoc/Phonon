package com.metrovoc.phonon.fabric;

import com.metrovoc.phonon.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The server's packet layout version, sent while a client configures, so a client with a different Phonon build
 * leaves with a clear reason instead of failing to read the first packet. NeoForge checks this itself.
 */
public record VersionPacket(String version) implements CustomPacketPayload {
    public static final Type<VersionPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "version"));
    public static final StreamCodec<ByteBuf, VersionPacket> CODEC =
        ByteBufCodecs.STRING_UTF8.map(VersionPacket::new, VersionPacket::version);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
