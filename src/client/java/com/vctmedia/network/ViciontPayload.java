package com.vctmedia.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ViciontPayload(byte action, String pathOrUrl, String soundId, long duration, int size, String pos, int opacity, boolean isOverlay, String bgColor, String text, boolean useFade) implements CustomPayload {
    public static final Id<ViciontPayload> ID = new Id<>(Identifier.of("viciontmedia", "main"));

    public static final PacketCodec<RegistryByteBuf, ViciontPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeByte(value.action);
                buf.writeString(value.pathOrUrl);
                buf.writeString(value.soundId != null ? value.soundId : "");
                buf.writeVarLong(value.duration);
                buf.writeVarInt(value.size);
                buf.writeString(value.pos != null ? value.pos : "center");
                buf.writeVarInt(value.opacity);
                buf.writeBoolean(value.isOverlay);
                buf.writeString(value.bgColor != null ? value.bgColor : "");
                buf.writeString(value.text != null ? value.text : "");
                buf.writeBoolean(value.useFade);
            },
            buf -> new ViciontPayload(
                    buf.readByte(),
                    buf.readString(),
                    buf.readString(),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readString(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readString(),
                    buf.readString(),
                    buf.readBoolean()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}