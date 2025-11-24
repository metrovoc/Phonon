package com.tovkaic.phonon.network;

import java.util.UUID;

public interface AudioPacketSender {
    void sendChunk(Object player, UUID resourceId, byte[] data, int chunkIndex, int totalChunks);
}
