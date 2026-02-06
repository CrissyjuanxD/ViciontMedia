package com.vctmedia;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.vctmedia.network.ViciontPayload;
import com.vctmedia.render.MediaOverlay;
import com.vctmedia.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

public class ViciontMediaClient implements ClientModInitializer {
    public static final Path MEDIA_DIR = FabricLoader.getInstance().getConfigDir().resolve("vctmedia_playback");
    private static KeyBinding volumeUp;
    private static KeyBinding volumeDown;

    @Override
    public void onInitializeClient() {
        if (!Files.exists(MEDIA_DIR)) {
            try { Files.createDirectories(MEDIA_DIR); } catch (Exception e) { e.printStackTrace(); }
        }

        MediaRegistry.initPreload();

        volumeUp = KeyBindingHelper.registerKeyBinding(new KeyBinding("Subir Volumen", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "ViciontMedia"));
        volumeDown = KeyBindingHelper.registerKeyBinding(new KeyBinding("Bajar Volumen", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "ViciontMedia"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (volumeUp.wasPressed()) VolumeManager.changeVolume(5);
            while (volumeDown.wasPressed()) VolumeManager.changeVolume(-5);
        });

        PayloadTypeRegistry.playS2C().register(ViciontPayload.ID, ViciontPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(ViciontPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.action() == 0) MediaOrchestrator.stopAll();
                else MediaOrchestrator.process(payload.pathOrUrl(), payload.soundId(), payload.duration(), payload.x(), payload.y(), payload.size(), payload.isOverlay());
            });
        });

        HudRenderCallback.EVENT.register(MediaOverlay::render);

        SuggestionProvider<FabricClientCommandSource> SOUND_SUGGESTIONS = (context, builder) -> {
            return CommandSource.suggestIdentifiers(MinecraftClient.getInstance().getSoundManager().getKeys(), builder);
        };

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // /vctmedia <duracion> <size> <x> <y> <overlay> [sound] <url>
            dispatcher.register(ClientCommandManager.literal("vctmedia")
                    .then(ClientCommandManager.argument("duracion", LongArgumentType.longArg())
                            .then(ClientCommandManager.argument("size", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                                            .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                                                                    .suggests(SOUND_SUGGESTIONS)
                                                                    .executes(context -> {
                                                                        String rawArgs = StringArgumentType.getString(context, "args").trim();

                                                                        String soundId = null;
                                                                        String url = rawArgs;

                                                                        // NUEVA LÓGICA DE DETECCIÓN (Más simple y robusta)
                                                                        if (rawArgs.contains(" ")) {
                                                                            String[] parts = rawArgs.split(" ", 2);
                                                                            String firstPart = parts[0];

                                                                            // Si la primera parte NO es http Y NO tiene extensión de archivo conocida...
                                                                            // Asumimos que ES UN SONIDO.
                                                                            boolean looksLikeUrlOrFile = firstPart.startsWith("http")
                                                                                    || firstPart.endsWith(".png")
                                                                                    || firstPart.endsWith(".jpg")
                                                                                    || firstPart.endsWith(".jpeg")
                                                                                    || firstPart.endsWith(".gif")
                                                                                    || firstPart.endsWith(".mp4");

                                                                            if (!looksLikeUrlOrFile) {
                                                                                soundId = firstPart;
                                                                                url = parts[1].trim();
                                                                            }
                                                                            if (soundId != null) {
                                                                                context.getSource().sendFeedback(Text.literal("§e[Debug] Sonido detectado: " + soundId));
                                                                            }
                                                                        }

                                                                        MediaOrchestrator.process(url, soundId,
                                                                                LongArgumentType.getLong(context, "duracion"),
                                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                                IntegerArgumentType.getInteger(context, "size"),
                                                                                BoolArgumentType.getBool(context, "overlay"));
                                                                        context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fProcesando: §7" + url));

                                                                        return 1;
                                                                    }))))))));

            // /vctvideofc <overlay> <url>
            dispatcher.register(ClientCommandManager.literal("vctvideofc")
                    .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                            .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        boolean overlay = BoolArgumentType.getBool(context, "overlay");
                                        MediaOrchestrator.process(url, null, -1, 0, 0, 0, overlay);
                                        context.getSource().sendFeedback(Text.literal("§a[ViciontMedia] §fIniciando pantalla completa..."));
                                        return 1;
                                    }))));

            // /vctedit
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