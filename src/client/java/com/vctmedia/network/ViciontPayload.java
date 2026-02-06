package com.vctmedia.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ViciontPayload(byte action, String pathOrUrl, String soundId, long duration, int size, int x, int y, boolean isOverlay) implements CustomPayload {
    public static final Id<ViciontPayload> ID = new Id<>(Identifier.of("viciontmedia", "main"));

    public static final PacketCodec<RegistryByteBuf, ViciontPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeByte(value.action);
                buf.writeString(value.pathOrUrl);
                buf.writeString(value.soundId != null ? value.soundId : "");
                buf.writeVarLong(value.duration);
                buf.writeVarInt(value.size);
                buf.writeVarInt(value.x);
                buf.writeVarInt(value.y);
                buf.writeBoolean(value.isOverlay);
            },
            buf -> new ViciontPayload(
                    buf.readByte(),
                    buf.readString(),
                    buf.readString(),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}