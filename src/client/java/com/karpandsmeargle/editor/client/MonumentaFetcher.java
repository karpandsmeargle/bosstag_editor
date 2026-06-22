package com.karpandsmeargle.editor.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MonumentaFetcher implements ClientPlayNetworking.PlayChannelHandler {
    private static final Identifier CHANNEL_ID = new Identifier("monumenta:bosstag_editor");
    private final Map<String, CompletableFuture<ResponsePacket>> awaitingResponses = new HashMap<>();

    public MonumentaFetcher() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL_ID, this);
    }

    private void sendAsJson(RequestPacket packet) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBytes(GSON.toJson(packet).getBytes(StandardCharsets.UTF_8));
        ClientPlayNetworking.send(CHANNEL_ID, buf);
    }

    public CompletableFuture<Void> requestEditorToMainhand(Map<String, Map<String, String>> tagList) {
        String messageId = UUID.randomUUID().toString();

        Main.LOGGER.info("Sending request for editor to mainhand with id {}", messageId);

        CompletableFuture<ResponsePacket> responseFuture = new CompletableFuture<>();
        awaitingResponses.put(messageId, responseFuture);

        sendAsJson(new RequestEditorToMainhandPacket(messageId, tagList));

        return responseFuture.thenAccept(responsePacket -> {
            if (responsePacket instanceof ResponseEditorToMainhandPacket responseEditorToMainhandPacket) {
                if (!responseEditorToMainhandPacket.success) {
                    Main.LOGGER.error("Server rejected editor to mainhand request for id {}", messageId);
                    throw new RuntimeException("Server rejected editor to mainhand request for id " + messageId);
                }
            } else {
                Main.LOGGER.error("Received wrong packet type from server for id {}; expected ResponseEditorToMainhand", messageId);
                throw new RuntimeException("Received wrong packet type from server for id " + messageId + "; expected ResponseEditorToMainhand");
            }
        });
    }

    public CompletableFuture<Map<String, Map<String, String>>> requestMainhandToEditor() {
        String messageId = UUID.randomUUID().toString();

        Main.LOGGER.info("Sending request for mainhand to editor with id {}", messageId);

        CompletableFuture<ResponsePacket> responseFuture = new CompletableFuture<>();
        awaitingResponses.put(messageId, responseFuture);

        sendAsJson(new RequestMainhandToEditorPacket(messageId));

        return responseFuture.thenApply(responsePacket -> {
            if (responsePacket instanceof ResponseMainhandToEditorPacket responseMainhandToEditorPacket) {
                if (responseMainhandToEditorPacket.tagList == null) {
                    Main.LOGGER.error("Server rejected mainhand to editor request for id {}", messageId);
                    throw new RuntimeException("Server rejected mainhand to editor request for id " + messageId);
                }
                return responseMainhandToEditorPacket.tagList;
            } else {
                Main.LOGGER.error("Received wrong packet type from server for id {}; expected ResponseMainhandToEditor", messageId);
                throw new RuntimeException("Received wrong packet type from server for id " + messageId + "; expected ResponseMainhandToEditor");
            }
        });
    }

    public CompletableFuture<Map<BosstagInfo, List<ParameterInfo>>> requestAllInfo() {
        String messageId = UUID.randomUUID().toString();

        Main.LOGGER.info("Sending request for all info with id {}", messageId);

        CompletableFuture<ResponsePacket> responseFuture = new CompletableFuture<>();
        awaitingResponses.put(messageId, responseFuture);

        sendAsJson(new RequestAllInfoPacket(messageId));

        return responseFuture.thenApply(responsePacket -> {
            if (responsePacket instanceof ResponseAllInfoPacket responseAllInfoPacket) {
                return responseAllInfoPacket.params;
            } else {
                Main.LOGGER.error("Received wrong packet type from server for id {}; expected ResponseAllInfo", messageId);
                throw new RuntimeException("Received wrong packet type from server for id " + messageId + "; expected ResponseAllInfo");
            }
        });
    }

    @Override
    public void receive(
        MinecraftClient client,
        ClientPlayNetworkHandler handler,
        PacketByteBuf buf,
        PacketSender responseSender
    ) {
        String encodedMessage = buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
        String type =
            JsonParser.parseString(encodedMessage)
                .getAsJsonObject()
                .get("TYPE")
                .getAsString();

        ResponsePacket packet = switch (type) {
            case ResponseEditorToMainhandPacket.TYPE -> GSON.fromJson(encodedMessage, ResponseEditorToMainhandPacket.class);
            case ResponseMainhandToEditorPacket.TYPE -> GSON.fromJson(encodedMessage, ResponseMainhandToEditorPacket.class);
            case ResponseAllInfoPacket.TYPE -> GSON.fromJson(encodedMessage, ResponseAllInfoPacket.class);
            default -> {
                Main.LOGGER.log(Level.WARN, "Unknown packet type: {}", type);
                yield null;
            }
        };

        if (packet == null) {
            return;
        }

        synchronized (awaitingResponses) {
            if (awaitingResponses.containsKey(packet.messageId())) {
                awaitingResponses.get(packet.messageId()).complete(packet);
                awaitingResponses.remove(packet.messageId());
            } else {
                Main.LOGGER.log(Level.ERROR, "Response packet type {} with id {} arrived, but couldn't find a corresponding outstanding request", type, packet.messageId());
            }
        }
    }

    /* Replicated exactly between mod and here */

    // Serialize static fields in packets so that the type is included.
    private static final Gson GSON =
        new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .create();

    // TODO: Consider adding deprecated field
    public record BosstagInfo(String name, @Nullable String description) {}

    public record ParameterInfo(String name, String type, @Nullable String description) {}

    // Client to Server
    private sealed interface RequestPacket {}
    @SuppressWarnings("unused")
    private record RequestEditorToMainhandPacket(String messageId, Map<String, Map<String, String>> tagList) implements RequestPacket {
        private static final String TYPE = "RequestEditorToMainhand";
    }

    @SuppressWarnings("unused")
    private record RequestMainhandToEditorPacket(String messageId) implements RequestPacket {
        private static final String TYPE = "RequestMainhandToEditor";
    }

    @SuppressWarnings("unused")
    private record RequestAllInfoPacket(String messageId) implements RequestPacket {
        private static final String TYPE = "RequestAllInfo";
    }

    // Server to Client
    private sealed interface ResponsePacket {
        @SuppressWarnings("unused")
        String messageId();
    }
    @SuppressWarnings("unused")
    private record ResponseEditorToMainhandPacket(String messageId, boolean success) implements ResponsePacket {
        private static final String TYPE = "ResponseEditorToMainhand";
    }

    @SuppressWarnings("unused")
    private record ResponseMainhandToEditorPacket(String messageId, @Nullable Map<String, Map<String, String>> tagList) implements ResponsePacket {
        private static final String TYPE = "ResponseMainhandToEditor";
    }

    @SuppressWarnings("unused")
    private record ResponseAllInfoPacket(String messageId, Map<BosstagInfo, List<ParameterInfo>> params) implements ResponsePacket {
        private static final String TYPE = "ResponseAllInfo";
    }
}
