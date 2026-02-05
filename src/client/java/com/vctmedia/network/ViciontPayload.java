package com.vctmedia.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ViciontPayload(byte action, String pathOrUrl, long duration, int size, int x, int y) implements CustomPayload {
    public static final Id<ViciontPayload> ID = new Id<>(Identifier.of("viciontmedia", "main"));

    public static final PacketCodec<RegistryByteBuf, ViciontPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BYTE, ViciontPayload::action,
            PacketCodecs.STRING, ViciontPayload::pathOrUrl,
            PacketCodecs.VAR_LONG, ViciontPayload::duration,
            PacketCodecs.VAR_INT, ViciontPayload::size,
            PacketCodecs.VAR_INT, ViciontPayload::x,
            PacketCodecs.VAR_INT, ViciontPayload::y,
            ViciontPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}