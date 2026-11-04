package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.server.SpeakerControl;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: 控制 speaker (play/pause/stop)。
 */
public record SpeakerControlPacket(
    BlockPos pos,
    Action action,
    UUID resourceId
) implements CustomPacketPayload {

    public enum Action {
        PLAY,   // 开始播放 (从头或恢复)
        PAUSE,  // 暂停
        STOP    // 停止 (重置到开头)
    }

    private static final Action[] ACTIONS = Action.values();

    public static final Type<SpeakerControlPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "speaker_control"));

    private static final StreamCodec<ByteBuf, Action> ACTION_CODEC = StreamCodec.of(
        (buf, action) -> buf.writeByte(action.ordinal()),
        buf -> {
            int id = buf.readUnsignedByte();
            if (id >= ACTIONS.length) {
                throw new DecoderException("Unknown speaker action: " + id);
            }
            return ACTIONS[id];
        }
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
            SpeakerBlockEntity speaker = SpeakerPacketValidator.getAccessibleSpeaker(player, packet.pos);
            if (speaker == null) return;

            switch (packet.action) {
                case PLAY -> SpeakerControl.play(level, packet.pos, speaker, packet.resourceId);
                case PAUSE -> SpeakerControl.pause(level, packet.pos, speaker);
                case STOP -> SpeakerControl.stop(level, packet.pos, speaker);
            }
        });
    }
}
