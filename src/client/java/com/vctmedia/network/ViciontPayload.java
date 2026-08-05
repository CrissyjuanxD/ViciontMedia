package com.vctmedia.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ViciontPayload(byte action, String pathOrUrl, String soundId, long duration, int size, String pos, int opacity, boolean isOverlay, String bgColor, String text, boolean useFade) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ViciontPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("viciontmedia", "main"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ViciontPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeByte(value.action);
                buf.writeUtf(value.pathOrUrl);
                buf.writeUtf(value.soundId != null ? value.soundId : "");
                buf.writeVarLong(value.duration);
                buf.writeVarInt(value.size);
                buf.writeUtf(value.pos != null ? value.pos : "center");
                buf.writeVarInt(value.opacity);
                buf.writeBoolean(value.isOverlay);
                buf.writeUtf(value.bgColor != null ? value.bgColor : "");
                buf.writeUtf(value.text != null ? value.text : "");
                buf.writeBoolean(value.useFade);
            },
            buf -> new ViciontPayload(
                    buf.readByte(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean()
            )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
