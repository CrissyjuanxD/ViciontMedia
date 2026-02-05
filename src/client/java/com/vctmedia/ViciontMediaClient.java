package com.vctmedia;

import com.mojang.brigadier.arguments.BoolArgumentType;
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
                else MediaOrchestrator.process(payload.pathOrUrl(), payload.duration(), payload.x(), payload.y(), payload.size(), payload.isOverlay());
            });
        });

        HudRenderCallback.EVENT.register(MediaOverlay::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /vctmedia <duracion> <size> <x> <y> <overlay> <url>
            dispatcher.register(ClientCommandManager.literal("vctmedia")
                    .then(ClientCommandManager.argument("duracion", LongArgumentType.longArg())
                            .then(ClientCommandManager.argument("size", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                                            .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                                                    .executes(context -> {
                                                                        MediaOrchestrator.process(
                                                                                StringArgumentType.getString(context, "url"),
                                                                                LongArgumentType.getLong(context, "duracion"),
                                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                                IntegerArgumentType.getInteger(context, "size"),
                                                                                BoolArgumentType.getBool(context, "overlay")
                                                                        );
                                                                        return 1;
                                                                    }))))))));

            // /vctvideofc <url> <overlay>
            dispatcher.register(ClientCommandManager.literal("vctvideofc")
                    .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                            .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        boolean overlay = BoolArgumentType.getBool(context, "overlay");

                                        // Enviamos -1 en duración (infinito) y 0 en x, y, size (pantalla completa)
                                        MediaOrchestrator.process(url, -1, 0, 0, 0, overlay);

                                        context.getSource().sendFeedback(Text.literal("§a[ViciontMedia] §fIniciando pantalla completa..."));
                                        return 1;
                                    }))));

            dispatcher.register(ClientCommandManager.literal("vctedit")
                    .then(ClientCommandManager.argument("nombre", StringArgumentType.string())
                            .then(ClientCommandManager.argument("duracion", LongArgumentType.longArg())
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("size", IntegerArgumentType.integer())
                                                            .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                                                    .executes(context -> {
                                                                        MediaOrchestrator.edit(
                                                                                StringArgumentType.getString(context, "nombre"),
                                                                                LongArgumentType.getLong(context, "duracion"),
                                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                                IntegerArgumentType.getInteger(context, "size"),
                                                                                BoolArgumentType.getBool(context, "overlay")
                                                                        );
                                                                        context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fEditado: §7" + StringArgumentType.getString(context, "nombre")));
                                                                        return 1;
                                                                    }))))))));

            // /vctstop
            dispatcher.register(ClientCommandManager.literal("vctstop")
                    .executes(context -> {
                        // Sin argumentos muestra lista de activos
                        var list = MediaOrchestrator.getActiveList();
                        context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fActivos:"));
                        for(var m : list) context.getSource().sendFeedback(Text.literal("§7- " + m.url));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("all").executes(context -> {
                        MediaOrchestrator.stopAll();
                        return 1;
                    }))
                    .then(ClientCommandManager.argument("nombre", StringArgumentType.string()).executes(context -> {
                        MediaOrchestrator.stopSpecific(StringArgumentType.getString(context, "nombre"));
                        return 1;
                    })));
        });
    }
}