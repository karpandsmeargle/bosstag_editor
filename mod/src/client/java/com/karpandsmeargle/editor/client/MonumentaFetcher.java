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
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MonumentaFetcher implements ClientPlayNetworking.PlayChannelHandler {
    private static final Identifier CHANNEL_ID = new Identifier("monumenta:bosstag_editor");
    private final Map<String, CompletableFuture<ResponsePacket>> awaitingResponses = new HashMap<>();
    private static final long TIMEOUT_MS = 5000;

    public static class RequestFailException extends RuntimeException {
        public static final String NOT_CONNECTED = "Attempted to send request while not connected to a Monumenta server";
        public static final String PACKET_MISMATCH = "Response packet does not match request";
        public static final String NO_BOS_MAINHAND = "No book of souls held in mainhand";

        private final String cause;
        private final String type;
        private final String messageId;
        public RequestFailException(String cause, String type, String messageId) {
            super(cause + "; request type: " + type + ", id: " + messageId);
            this.cause = cause;
            this.type = type;
            this.messageId = messageId;
        }

        public String reason() {
            return cause + "; request type: " + type + ", id: " + messageId;
        }
    }

    public MonumentaFetcher() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL_ID, this);
    }

    private CompletableFuture<ResponsePacket> request(RequestPacket request, String type) {
        String messageId = request.messageId();

        Main.LOGGER.info("Sending request; request type: {}, id: {}", type, messageId);

        // Not completely foolproof, since you can log off Monumenta after this check,
        // but is just an early check for the common case.
        if (isNotOnMonumenta(MinecraftClient.getInstance())) {
            var connectFailException = new RequestFailException(RequestFailException.NOT_CONNECTED, type, messageId);
            Main.LOGGER.error(connectFailException.reason());
            return CompletableFuture.failedFuture(connectFailException);
        }

        CompletableFuture<ResponsePacket> responseFuture = new CompletableFuture<>();
        synchronized (awaitingResponses) {
            // whenComplete inside to guarantee that remove will always run after
            awaitingResponses.put(messageId, responseFuture);
            responseFuture
                .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((_r, _t) -> {
                    synchronized(awaitingResponses) {
                        awaitingResponses.remove(messageId);
                    }
                });
        }

        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBytes(GSON.toJson(request).getBytes(StandardCharsets.UTF_8));
            ClientPlayNetworking.send(CHANNEL_ID, buf);
        } catch (IllegalStateException e) {
            var connectFailException = new RequestFailException(RequestFailException.NOT_CONNECTED, type, messageId);
            Main.LOGGER.error(connectFailException.reason());
            responseFuture.completeExceptionally(connectFailException);
        }

        return responseFuture;
    }

    public CompletableFuture<Void> requestEditorToMainhand(Map<String, Map<String, String>> tagList) {
        String messageId = UUID.randomUUID().toString();

        var responseFuture = request(new RequestEditorToMainhandPacket(messageId, tagList), RequestEditorToMainhandPacket.TYPE);

        return responseFuture.thenAccept(responsePacket -> {
            if (responsePacket instanceof ResponseEditorToMainhandPacket responseEditorToMainhandPacket) {
                if (!responseEditorToMainhandPacket.success) {
                    var rfe = new RequestFailException(RequestFailException.NO_BOS_MAINHAND, RequestEditorToMainhandPacket.TYPE, messageId);
                    Main.LOGGER.error(rfe.reason());
                    throw rfe;
                }
            } else {
                var rfe = new RequestFailException(RequestFailException.PACKET_MISMATCH, RequestEditorToMainhandPacket.TYPE, messageId);
                Main.LOGGER.error(rfe.reason());
                throw rfe;
            }
        });
    }

    public CompletableFuture<Map<String, Map<String, String>>> requestMainhandToEditor() {
        String messageId = UUID.randomUUID().toString();

        var responseFuture = request(new RequestMainhandToEditorPacket(messageId), RequestMainhandToEditorPacket.TYPE);

        return responseFuture.thenApply(responsePacket -> {
            if (responsePacket instanceof ResponseMainhandToEditorPacket responseMainhandToEditorPacket) {
                if (responseMainhandToEditorPacket.tagList == null) {
                    var rfe = new RequestFailException(RequestFailException.NO_BOS_MAINHAND, RequestMainhandToEditorPacket.TYPE, messageId);
                    Main.LOGGER.error(rfe.reason());
                    throw rfe;
                }
                return responseMainhandToEditorPacket.tagList;
            } else {
                var rfe = new RequestFailException(RequestFailException.PACKET_MISMATCH, RequestMainhandToEditorPacket.TYPE, messageId);
                Main.LOGGER.error(rfe.reason());
                throw rfe;
            }
        });
    }

    public CompletableFuture<Map<BosstagInfo, List<ParameterInfo>>> requestAllInfo() {
        String messageId = UUID.randomUUID().toString();

        var responseFuture = request(new RequestAllInfoPacket(messageId), RequestAllInfoPacket.TYPE);

        return responseFuture.thenApply(responsePacket -> {
            if (responsePacket instanceof ResponseAllInfoPacket responseAllInfoPacket) {
                return responseAllInfoPacket.params;
            } else {
                var rfe = new RequestFailException(RequestFailException.PACKET_MISMATCH, RequestAllInfoPacket.TYPE, messageId);
                Main.LOGGER.error(rfe.reason());
                throw rfe;
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
        if (isNotOnMonumenta(client)) {
            Main.LOGGER.warn("Received a packet while not on Monumenta");
            return;
        }

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
                Main.LOGGER.warn("Unknown packet type: {}", type);
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
                Main.LOGGER.error("Response packet type {} with id {} arrived, but couldn't find a corresponding outstanding request", type, packet.messageId());
            }
        }
    }

    public boolean isNotOnMonumenta(MinecraftClient client) {
        return client.isInSingleplayer()
            || client.getCurrentServerEntry() == null
            || !client.getCurrentServerEntry().address.toLowerCase().endsWith(".playmonumenta.com");
    }

    /* Replicated exactly between mod and here */

    // Serialize static fields in packets so that the type is included.
    private static final Gson GSON =
        new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .create();

    public record BosstagInfo(String name, @Nullable String description, boolean deprecated) {}

    public record ParameterInfo(String name, String type, @Nullable String description, boolean deprecated) {}

    // Client to Server
    private sealed interface RequestPacket {
        String messageId();
    }
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
