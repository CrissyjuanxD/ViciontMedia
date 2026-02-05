package com.vctmedia;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vctmedia.network.ViciontPayload;
import com.vctmedia.render.MediaOverlay;
import com.vctmedia.util.MediaOrchestrator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;

public class ViciontMediaClient implements ClientModInitializer {
    public static final Path MEDIA_DIR = FabricLoader.getInstance().getConfigDir().resolve("vctmedia_playback");

    @Override
    public void onInitializeClient() {
        if (!Files.exists(MEDIA_DIR)) {
            try { Files.createDirectories(MEDIA_DIR); } catch (Exception e) { e.printStackTrace(); }
        }

        // Registro de red (Sigue igual, es correcto)
        PayloadTypeRegistry.playS2C().register(ViciontPayload.ID, ViciontPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(ViciontPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.action() == 0) MediaOrchestrator.stopAll();
                else MediaOrchestrator.process(payload.pathOrUrl(), payload.duration(), payload.x(), payload.y(), payload.size());
            });
        });

        HudRenderCallback.EVENT.register(MediaOverlay::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // Comando unificado para testeo rápido: /vctmedia <url> <duracion> <size> <x> <y>
            dispatcher.register(ClientCommandManager.literal("vctmedia")
                    .then(ClientCommandManager.argument("duracion", LongArgumentType.longArg())
                            .then(ClientCommandManager.argument("size", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("url", StringArgumentType.greedyString()) // GreedyString AL FINAL
                                                            .executes(context -> {
                                                                String url = StringArgumentType.getString(context, "url");
                                                                long dur = LongArgumentType.getLong(context, "duracion");
                                                                int size = IntegerArgumentType.getInteger(context, "size");
                                                                int x = IntegerArgumentType.getInteger(context, "x");
                                                                int y = IntegerArgumentType.getInteger(context, "y");

                                                                MediaOrchestrator.process(url, dur, x, y, size);
                                                                context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fProcesando: §7" + url));
                                                                return 1;
                                                            })))))));

            // /vctvideofc <url> (Mantenemos tu comando de pantalla completa)
            dispatcher.register(ClientCommandManager.literal("vctvideofc")
                    .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                            .executes(context -> {
                                MediaOrchestrator.process(StringArgumentType.getString(context, "url"), -1, 0, 0, 0);
                                context.getSource().sendFeedback(Text.literal("§a[ViciontMedia] §fIniciando pantalla completa..."));
                                return 1;
                            })));

            // /vctstop
            dispatcher.register(ClientCommandManager.literal("vctstop").executes(context -> {
                MediaOrchestrator.stopAll();
                context.getSource().sendFeedback(Text.literal("§c[ViciontMedia] §fMedia detenida."));
                return 1;
            }));
        });
    }
}