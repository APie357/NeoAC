package dev.andrewd1.neoac.net;

import dev.andrewd1.neoac.Config;
import dev.andrewd1.neoac.NeoAC;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.*;

public record ModListPayload(Map<String, String> mods, int chunkIndex, int totalChunks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ModListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NeoAC.MOD_ID, "mod_list"));
    
    // Maximum safe packet size (leaving room for other packet data)
    private static final int MAX_PACKET_SIZE = 1000; // bytes
    
    public static final StreamCodec<ByteBuf, ModListPayload> STREAM_CODEC = StreamCodec.of(
            ModListPayload::encode,
            ModListPayload::decode
    );

    // Constructor for single chunk (non-split packets)
    public ModListPayload(Map<String, String> mods) {
        this(mods, 0, 1);
    }

    private static void encode(ByteBuf buffer, ModListPayload payload) {
        buffer.writeInt(payload.chunkIndex);
        buffer.writeInt(payload.totalChunks);
        buffer.writeInt(payload.mods.size());
        
        for (Map.Entry<String, String> entry : payload.mods.entrySet()) {
            writeString(buffer, entry.getKey());
            writeString(buffer, entry.getValue());
        }
    }

    private static ModListPayload decode(ByteBuf buffer) {
        int chunkIndex = buffer.readInt();
        int totalChunks = buffer.readInt();
        int size = buffer.readInt();
        
        Map<String, String> mods = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = readString(buffer);
            String value = readString(buffer);
            mods.put(key, value);
        }
        
        return new ModListPayload(mods, chunkIndex, totalChunks);
    }

    private static void writeString(ByteBuf buffer, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static String readString(ByteBuf buffer) {
        int length = buffer.readInt();
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Static map to store partial data from chunked packets
    private static final Map<String, Map<String, String>> partialData = new HashMap<>();

    public static void clientHandler(ModListPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Send client's mod list to server (with chunking if needed)
            sendModListChunked(NeoAC.loadedModHashes);
        });
    }

    public static void serverHandler(ModListPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            String playerId = context.player() != null ? context.player().getUUID().toString() : "unknown";
            
            if (data.totalChunks == 1) {
                // Single packet, process immediately
                processCompleteModList(data.mods, context);
            } else {
                // Multi-packet, need to collect all chunks
                partialData.computeIfAbsent(playerId, k -> new HashMap<>()).putAll(data.mods);
                
                // Check if we have all chunks (this is a simple approach - you might want to track chunk indices)
                if (partialData.get(playerId).size() >= getTotalExpectedSize(playerId)) {
                    processCompleteModList(partialData.get(playerId), context);
                    partialData.remove(playerId);
                }
            }
        });
    }

    private static void sendModListChunked(Map<String, String> mods) {
        // Calculate approximate size of each entry
        int totalSize = 0;
        for (Map.Entry<String, String> entry : mods.entrySet()) {
            totalSize += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
            totalSize += entry.getValue().getBytes(StandardCharsets.UTF_8).length;
            totalSize += 8; // for length prefixes
        }

        if (totalSize <= MAX_PACKET_SIZE) {
            // Send as single packet
            PacketDistributor.sendToServer(new ModListPayload(mods));
        } else {
            // Split into chunks
            Map<String, String> currentChunk = new HashMap<>();
            int currentSize = 0;
            int chunkIndex = 0;
            
            for (Map.Entry<String, String> entry : mods.entrySet()) {
                int entrySize = entry.getKey().getBytes(StandardCharsets.UTF_8).length +
                               entry.getValue().getBytes(StandardCharsets.UTF_8).length + 8;
                
                if (currentSize + entrySize > MAX_PACKET_SIZE && !currentChunk.isEmpty()) {
                    // Send current chunk
                    PacketDistributor.sendToServer(new ModListPayload(currentChunk, chunkIndex, -1)); // -1 means we'll update total later
                    currentChunk = new HashMap<>();
                    currentSize = 0;
                    chunkIndex++;
                }
                
                currentChunk.put(entry.getKey(), entry.getValue());
                currentSize += entrySize;
            }
            
            // Send final chunk
            if (!currentChunk.isEmpty()) {
                PacketDistributor.sendToServer(new ModListPayload(currentChunk, chunkIndex, chunkIndex + 1));
            }
        }
    }

    private static void processCompleteModList(Map<String, String> mods, IPayloadContext context) {
        NeoAC.LOGGER.info("Received mod list from client");
        ServerPlayer player = (ServerPlayer) context.player();
        NeoAC.playerModListDisconnectTimer.get(player).cancel();
        NeoAC.playerModListDisconnectTimer.remove(player);

        List<String> violatingMods = new ArrayList<>();
        for (String id : mods.keySet()) {
            if (!Config.modAllowlist.containsKey(id) || !Objects.equals(mods.get(id), Config.modAllowlist.get(id))) {
                violatingMods.add(id);
            }
        }

        if (!violatingMods.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String mod : violatingMods) {
                sb.append(mod).append("\n");
            }
            context.connection().disconnect(new DisconnectionDetails(Component.literal(
                    "§c" + NeoAC.MOD_NAME + "\n" +
                    "Mod mismatch detected!\nIs your modpack the same version as the server's?:\n\n" +
                    sb
            )));
        }
    }

    private static int getTotalExpectedSize(String playerId) {
        // This is a simplified approach - in a real implementation you'd want to track expected total size
        return Integer.MAX_VALUE; // For now, just assume we need to collect until timeout or explicit end
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}