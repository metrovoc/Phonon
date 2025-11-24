package com.tovkaic.phonon.network.packets;

import com.tovkaic.phonon.Constants;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.block.SpeakerBlockEntity;
import com.tovkaic.phonon.platform.PlatformHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: Control speaker (play/stop).
 */
public record SpeakerControlPacket(
    BlockPos pos,
    Action action,
    UUID resourceId,
    float volume
) implements CustomPacketPayload {

    public enum Action {
        PLAY,
        STOP
    }

    public static final Type<SpeakerControlPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "speaker_control"));

    private static final StreamCodec<ByteBuf, Action> ACTION_CODEC = StreamCodec.of(
        (buf, action) -> buf.writeByte(action.ordinal()),
        buf -> Action.values()[buf.readByte()]
    );

    public static final StreamCodec<ByteBuf, SpeakerControlPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SpeakerControlPacket::pos,
        ACTION_CODEC,
        SpeakerControlPacket::action,
        UUIDCodec.STREAM_CODEC,
        SpeakerControlPacket::resourceId,
        ByteBufCodecs.FLOAT,
        SpeakerControlPacket::volume,
        SpeakerControlPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            var level = player.serverLevel();
            if (!(level.getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker)) return;

            switch (packet.action) {
                case PLAY -> {
                    long serverTime = System.currentTimeMillis();
                    var playback = new PlaybackState(
                        packet.resourceId,
                        serverTime,
                        packet.volume,
                        true
                    );
                    speaker.setPlayback(playback);
                    PlatformHelper.INSTANCE.sendToAllTracking(
                        level,
                        packet.pos,
                        new SyncSpeakerStatePacket(packet.pos, playback, serverTime)
                    );
                }
                case STOP -> {
                    speaker.setPlayback(PlaybackState.STOPPED);
                    PlatformHelper.INSTANCE.sendToAllTracking(
                        level,
                        packet.pos,
                        new SyncSpeakerStatePacket(packet.pos, PlaybackState.STOPPED, System.currentTimeMillis())
                    );
                }
            }
        });
    }
}
