package com.vctmedia;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.vctmedia.network.ViciontPayload;
import com.vctmedia.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ViciontMediaClient implements ClientModInitializer {
    public static final Path MEDIA_DIR = FabricLoader.getInstance().getConfigDir().resolve("vctmedia_playback");
    private static KeyBinding volumeUp;
    private static KeyBinding volumeDown;

    @Override
    public void onInitializeClient() {
        if (!Files.exists(MEDIA_DIR)) {
            try { Files.createDirectories(MEDIA_DIR); } catch (Exception e) { e.printStackTrace(); }
        }

        volumeUp = KeyBindingHelper.registerKeyBinding(new KeyBinding("Subir Volumen", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, "ViciontMedia"));
        volumeDown = KeyBindingHelper.registerKeyBinding(new KeyBinding("Bajar Volumen", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, "ViciontMedia"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (volumeUp.wasPressed()) VolumeManager.changeVolume(5);
            while (volumeDown.wasPressed()) VolumeManager.changeVolume(-5);
        });

        GifPreCache.init();

        PayloadTypeRegistry.playS2C().register(ViciontPayload.ID, ViciontPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(ViciontPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                switch (payload.action()) {
                    case 0:
                        MediaOrchestrator.stopAll();
                        break;
                    case 1:
                        MediaOrchestrator.process(payload.pathOrUrl(), payload.soundId(), payload.duration(), payload.size(), payload.pos(), payload.opacity(), payload.isOverlay(), payload.useFade());
                        break;
                    case 2:
                        MediaOrchestrator.edit(payload.pathOrUrl(), payload.duration(), payload.size(), payload.pos(), payload.opacity(), payload.isOverlay());
                        break;
                    case 3:
                        MediaOrchestrator.stopSpecific(payload.pathOrUrl());
                        break;
                    case 4:
                        TextOrchestrator.addText(payload.bgColor(), (int) payload.duration(), payload.pos(), payload.text(), payload.size(), payload.pathOrUrl(), payload.isOverlay());
                        break;
                    case 5:
                        TextOrchestrator.removeTextByContent(payload.text());
                        break;
                    case 6:
                        ShaderManager.loadShader(payload.pathOrUrl());
                        break;
                    case 7:
                        ShaderManager.removeShader(payload.pathOrUrl());
                        break;
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TextOrchestrator.clearAll();
            ShaderManager.loadShader("none");
            GifPreCache.evictAll();
        });

        SuggestionProvider<FabricClientCommandSource> SOUND_SUGGESTIONS = (context, builder) -> {
            return CommandSource.suggestIdentifiers(MinecraftClient.getInstance().getSoundManager().getKeys(), builder);
        };

        SuggestionProvider<FabricClientCommandSource> POS_SUGGESTIONS = (context, builder) -> {
            return CommandSource.suggestMatching(Arrays.asList("topleft", "topright", "bottomleft", "bottomright", "center"), builder);
        };

        SuggestionProvider<FabricClientCommandSource> FADE_SUGGESTIONS = (context, builder) -> {
            return CommandSource.suggestMatching(Arrays.asList("fadeon", "fadeoff"), builder);
        };

        SuggestionProvider<FabricClientCommandSource> ACTIVE_TEXTS_SUGGESTIONS = (context, builder) -> {
            return CommandSource.suggestMatching(TextOrchestrator.getActiveTextStrings(), builder);
        };

        SuggestionProvider<FabricClientCommandSource> SHADER_SUGGESTIONS = (context, builder) -> {
            return CommandSource.suggestMatching(ShaderManager.SHADERS, builder);
        };

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // VCTVIDEOFC
            dispatcher.register(ClientCommandManager.literal("vctvideofc")
                    .then(ClientCommandManager.argument("opacity", IntegerArgumentType.integer(0, 100))
                            .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                    .then(ClientCommandManager.argument("fade", StringArgumentType.word()).suggests(FADE_SUGGESTIONS)
                                            .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        String url = StringArgumentType.getString(context, "url");
                                                        int opacity = IntegerArgumentType.getInteger(context, "opacity");
                                                        boolean overlay = BoolArgumentType.getBool(context, "overlay");
                                                        boolean hasFade = StringArgumentType.getString(context, "fade").equalsIgnoreCase("fadeon");

                                                        MediaOrchestrator.process(url, null, -1, 0, "center", opacity, overlay, hasFade);
                                                        context.getSource().sendFeedback(Text.literal("§a[ViciontMedia] §fIniciando pantalla completa..."));
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )
            );

            // VCTMEDIA
            dispatcher.register(ClientCommandManager.literal("vctmedia")
                    .then(ClientCommandManager.argument("duracion", LongArgumentType.longArg())
                            .then(ClientCommandManager.argument("size", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("pos", StringArgumentType.word())
                                            .then(ClientCommandManager.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                    .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                                            .then(ClientCommandManager.argument("fade", StringArgumentType.word()).suggests(FADE_SUGGESTIONS)
                                                                    .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                                                                            .suggests(SOUND_SUGGESTIONS)
                                                                            .executes(context -> {
                                                                                String rawArgs = StringArgumentType.getString(context, "args").trim();
                                                                                String soundId = null;
                                                                                String url = rawArgs;
                                                                                if (rawArgs.contains(" ")) {
                                                                                    String[] parts = rawArgs.split(" ", 2);
                                                                                    String firstPart = parts[0];
                                                                                    boolean looksLikeUrlOrFile = firstPart.startsWith("http") || firstPart.endsWith(".png") || firstPart.endsWith(".jpg") || firstPart.endsWith(".jpeg") || firstPart.endsWith(".gif") || firstPart.endsWith(".mp4");
                                                                                    if (!looksLikeUrlOrFile) { soundId = firstPart; url = parts[1].trim(); }
                                                                                }
                                                                                boolean hasFade = StringArgumentType.getString(context, "fade").equalsIgnoreCase("fadeon");
                                                                                MediaOrchestrator.process(url, soundId, LongArgumentType.getLong(context, "duracion"), IntegerArgumentType.getInteger(context, "size"), StringArgumentType.getString(context, "pos"), IntegerArgumentType.getInteger(context, "opacity"), BoolArgumentType.getBool(context, "overlay"), hasFade);
                                                                                context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fProcesando: §7" + url));
                                                                                return 1;
                                                                            }))))))
                                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                            .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                                                    .then(ClientCommandManager.argument("fade", StringArgumentType.word()).suggests(FADE_SUGGESTIONS)
                                                                            .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                                                                                    .suggests(SOUND_SUGGESTIONS)
                                                                                    .executes(context -> {
                                                                                        String rawArgs = StringArgumentType.getString(context, "args").trim();
                                                                                        String soundId = null;
                                                                                        String url = rawArgs;
                                                                                        if (rawArgs.contains(" ")) {
                                                                                            String[] parts = rawArgs.split(" ", 2);
                                                                                            String firstPart = parts[0];
                                                                                            boolean looksLikeUrlOrFile = firstPart.startsWith("http") || firstPart.endsWith(".png") || firstPart.endsWith(".jpg") || firstPart.endsWith(".jpeg") || firstPart.endsWith(".gif") || firstPart.endsWith(".mp4");
                                                                                            if (!looksLikeUrlOrFile) { soundId = firstPart; url = parts[1].trim(); }
                                                                                        }
                                                                                        String posCustom = IntegerArgumentType.getInteger(context, "x") + "," + IntegerArgumentType.getInteger(context, "y");
                                                                                        boolean hasFade = StringArgumentType.getString(context, "fade").equalsIgnoreCase("fadeon");
                                                                                        MediaOrchestrator.process(url, soundId, LongArgumentType.getLong(context, "duracion"), IntegerArgumentType.getInteger(context, "size"), posCustom, IntegerArgumentType.getInteger(context, "opacity"), BoolArgumentType.getBool(context, "overlay"), hasFade);
                                                                                        context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fProcesando: §7" + url));
                                                                                        return 1;
                                                                                    }))))))))));

            // VCTEDIT
            dispatcher.register(ClientCommandManager.literal("vctedit")
                    .then(ClientCommandManager.argument("nombre", StringArgumentType.string())
                            .then(ClientCommandManager.argument("duracion", LongArgumentType.longArg())
                                    .then(ClientCommandManager.argument("size", IntegerArgumentType.integer())
                                            .then(ClientCommandManager.argument("pos", StringArgumentType.word())
                                                    .then(ClientCommandManager.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                            .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                                                    .executes(context -> {
                                                                        MediaOrchestrator.edit(StringArgumentType.getString(context, "nombre"), LongArgumentType.getLong(context, "duracion"), IntegerArgumentType.getInteger(context, "size"), StringArgumentType.getString(context, "pos"), IntegerArgumentType.getInteger(context, "opacity"), BoolArgumentType.getBool(context, "overlay"));
                                                                        context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fEditado: §7" + StringArgumentType.getString(context, "nombre")));
                                                                        return 1;
                                                                    }))))
                                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                            .then(ClientCommandManager.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                                    .then(ClientCommandManager.argument("overlay", BoolArgumentType.bool())
                                                                            .executes(context -> {
                                                                                String posCustom = IntegerArgumentType.getInteger(context, "x") + "," + IntegerArgumentType.getInteger(context, "y");
                                                                                MediaOrchestrator.edit(StringArgumentType.getString(context, "nombre"), LongArgumentType.getLong(context, "duracion"), IntegerArgumentType.getInteger(context, "size"), posCustom, IntegerArgumentType.getInteger(context, "opacity"), BoolArgumentType.getBool(context, "overlay"));
                                                                                context.getSource().sendFeedback(Text.literal("§6[ViciontMedia] §fEditado: §7" + StringArgumentType.getString(context, "nombre")));
                                                                                return 1;
                                                                            })))))))));

            // VCTSTOP
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

            // VCTEXT - MODIFICADO CON EL ARGUMENTO SYNC AL FINAL
            dispatcher.register(ClientCommandManager.literal("vctext")
                    .then(ClientCommandManager.argument("size", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("animacion", StringArgumentType.string()).suggests((context, builder) -> CommandSource.suggestMatching(Arrays.asList("default", "fade", "izquierda", "derecha", "arriba", "abajo"), builder))
                                    .then(ClientCommandManager.argument("bgcolor", StringArgumentType.string())
                                            .then(ClientCommandManager.argument("tiempo", IntegerArgumentType.integer(1))
                                                    .then(ClientCommandManager.argument("pos", StringArgumentType.string()).suggests(POS_SUGGESTIONS)
                                                            .then(ClientCommandManager.argument("sync", BoolArgumentType.bool())
                                                                    .then(ClientCommandManager.argument("texto", StringArgumentType.greedyString())
                                                                            .executes(context -> {
                                                                                int size = IntegerArgumentType.getInteger(context, "size");
                                                                                String anim = StringArgumentType.getString(context, "animacion");
                                                                                String bgColor = StringArgumentType.getString(context, "bgcolor");
                                                                                int duration = IntegerArgumentType.getInteger(context, "tiempo");
                                                                                String pos = StringArgumentType.getString(context, "pos");
                                                                                boolean sync = BoolArgumentType.getBool(context, "sync");
                                                                                String text = StringArgumentType.getString(context, "texto");

                                                                                TextOrchestrator.addText(bgColor, duration, pos, text, size, anim, sync);
                                                                                context.getSource().sendFeedback(Text.literal("§a[ViciontMedia] §fTexto mostrado en " + pos));
                                                                                return 1;
                                                                            })))))))));

            // VCTEXTDEL
            dispatcher.register(ClientCommandManager.literal("vctextdel")
                    .then(ClientCommandManager.argument("textoActivo", StringArgumentType.greedyString())
                            .suggests(ACTIVE_TEXTS_SUGGESTIONS)
                            .executes(context -> {
                                String textToRemove = StringArgumentType.getString(context, "textoActivo");
                                TextOrchestrator.removeTextByContent(textToRemove);
                                context.getSource().sendFeedback(Text.literal("§c[ViciontMedia] §fTexto eliminado: " + textToRemove));
                                return 1;
                            })));

            // VCTSS Y VCTSSRM
            dispatcher.register(ClientCommandManager.literal("vctss")
                    .then(ClientCommandManager.argument("shader", StringArgumentType.word())
                            .suggests(SHADER_SUGGESTIONS)
                            .executes(context -> {
                                String shaderName = StringArgumentType.getString(context, "shader");
                                ShaderManager.loadShader(shaderName);
                                context.getSource().sendFeedback(Text.literal("§d[ViciontMedia] §fAplicando Super Secret Setting: §e" + shaderName));
                                return 1;
                            })));

            dispatcher.register(ClientCommandManager.literal("vctssrm")
                    .then(ClientCommandManager.argument("shader", StringArgumentType.word())
                            .suggests(SHADER_SUGGESTIONS)
                            .executes(context -> {
                                String shaderName = StringArgumentType.getString(context, "shader");
                                ShaderManager.removeShader(shaderName);
                                context.getSource().sendFeedback(Text.literal("§c[ViciontMedia] §fEliminando shader: §e" + shaderName));
                                return 1;
                            })));

        });
    }
}