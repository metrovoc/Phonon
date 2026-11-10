package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.server.LibraryJob;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: audio being added right now, with progress.
 */
public record SyncLibraryJobsPacket(List<LibraryJob> jobs) implements CustomPacketPayload {
    private static final int MAX_JOBS = 64;

    public static final Type<SyncLibraryJobsPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_library_jobs"));

    public static final StreamCodec<ByteBuf, SyncLibraryJobsPacket> CODEC = ByteBufCodecs.<ByteBuf, LibraryJob, List<LibraryJob>>collection(
        ArrayList::new,
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(8192), LibraryJob::label,
            ByteBufCodecs.VAR_INT, LibraryJob::percent,
            ByteBufCodecs.stringUtf8(8192), LibraryJob::url,
            LibraryJob::new),
        MAX_JOBS
    ).map(SyncLibraryJobsPacket::new, SyncLibraryJobsPacket::jobs);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncLibraryJobsPacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> ClientStations.getInstance().setJobs(List.copyOf(packet.jobs)));
    }
}
