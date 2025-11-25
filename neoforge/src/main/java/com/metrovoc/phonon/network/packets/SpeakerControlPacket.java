package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.platform.PlatformHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
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
    UUID resourceId
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
                    var playback = new PlaybackState(packet.resourceId, serverTime, true);
                    speaker.setPlayback(playback);
                    PlatformHelper.INSTANCE.sendToAllTracking(
                        level,
                        packet.pos,
                        new SyncSpeakerStatePacket(packet.pos, playback, speaker.getVolume(), serverTime)
                    );
                }
                case STOP -> {
                    speaker.setPlayback(PlaybackState.STOPPED);
                    PlatformHelper.INSTANCE.sendToAllTracking(
                        level,
                        packet.pos,
                        new SyncSpeakerStatePacket(packet.pos, PlaybackState.STOPPED, speaker.getVolume(), System.currentTimeMillis())
                    );
                }
            }
        });
    }
}
