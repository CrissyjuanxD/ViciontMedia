package com.vctmedia;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.vctmedia.network.ViciontPayload;
import com.vctmedia.ViciontMedia;
import com.vctmedia.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ViciontMediaClient implements ClientModInitializer {
    public static final Path MEDIA_DIR = FabricLoader.getInstance().getConfigDir().resolve("vctmedia_playback");
    private static KeyMapping volumeUp;
    private static KeyMapping volumeDown;

    @Override
    public void onInitializeClient() {
        if (!Files.exists(MEDIA_DIR)) {
            try { Files.createDirectories(MEDIA_DIR); } catch (Exception e) { e.printStackTrace(); }
        }

        KeyMapping.Category vctCategory = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ViciontMedia.MOD_ID, "category"));
        volumeUp = KeyMappingHelper.registerKeyMapping(new KeyMapping("Subir Volumen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, vctCategory));
        volumeDown = KeyMappingHelper.registerKeyMapping(new KeyMapping("Bajar Volumen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, vctCategory));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (volumeUp.consumeClick()) VolumeManager.changeVolume(5);
            while (volumeDown.consumeClick()) VolumeManager.changeVolume(-5);
        });

        GifPreCache.init();

        PayloadTypeRegistry.clientboundPlay().register(ViciontPayload.TYPE, ViciontPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(ViciontPayload.TYPE, (payload, ctx) -> {
            ViciontPayload p = (ViciontPayload) payload;
            ctx.client().execute(() -> {
                switch (p.action()) {
                    case 0:
                        MediaOrchestrator.stopAll();
                        break;
                    case 1:
                        MediaOrchestrator.process(p.pathOrUrl(), p.soundId(), p.duration(), p.size(), p.pos(), p.opacity(), p.isOverlay(), p.useFade());
                        break;
                    case 2:
                        MediaOrchestrator.edit(p.pathOrUrl(), p.duration(), p.size(), p.pos(), p.opacity(), p.isOverlay());
                        break;
                    case 3:
                        MediaOrchestrator.stopSpecific(p.pathOrUrl());
                        break;
                    case 4:
                        TextOrchestrator.addText(p.bgColor(), (int) p.duration(), p.pos(), p.text(), p.size(), p.pathOrUrl(), p.isOverlay());
                        break;
                    case 5:
                        TextOrchestrator.removeTextByContent(p.text());
                        break;
                    case 6:
                        VctShaderManager.loadShader(p.pathOrUrl());
                        break;
                    case 7:
                        VctShaderManager.removeShader(p.pathOrUrl());
                        break;
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TextOrchestrator.clearAll();
            VctShaderManager.loadShader("none");
            GifPreCache.evictAll();
        });

        SuggestionProvider<FabricClientCommandSource> SOUND_SUGGESTIONS = (context, builder) -> {
            return net.minecraft.commands.SharedSuggestionProvider.suggestResource(Minecraft.getInstance().getSoundManager().getAvailableSounds(), builder);
        };

        SuggestionProvider<FabricClientCommandSource> POS_SUGGESTIONS = (context, builder) -> {
            return net.minecraft.commands.SharedSuggestionProvider.suggest(Arrays.asList("topleft", "topright", "bottomleft", "bottomright", "center"), builder);
        };

        SuggestionProvider<FabricClientCommandSource> FADE_SUGGESTIONS = (context, builder) -> {
            return net.minecraft.commands.SharedSuggestionProvider.suggest(Arrays.asList("fadeon", "fadeoff"), builder);
        };

        SuggestionProvider<FabricClientCommandSource> ACTIVE_TEXTS_SUGGESTIONS = (context, builder) -> {
            return net.minecraft.commands.SharedSuggestionProvider.suggest(TextOrchestrator.getActiveTextStrings(), builder);
        };

        SuggestionProvider<FabricClientCommandSource> SHADER_SUGGESTIONS = (context, builder) -> {
            return net.minecraft.commands.SharedSuggestionProvider.suggest(VctShaderManager.SHADERS, builder);
        };

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // VCTVIDEOFC
            dispatcher.register(ClientCommands.literal("vctvideofc")
                    .then(ClientCommands.argument("opacity", IntegerArgumentType.integer(0, 100))
                            .then(ClientCommands.argument("overlay", BoolArgumentType.bool())
                                    .then(ClientCommands.argument("fade", StringArgumentType.word()).suggests(FADE_SUGGESTIONS)
                                            .then(ClientCommands.argument("url", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        String url = StringArgumentType.getString(context, "url");
                                                        int opacity = IntegerArgumentType.getInteger(context, "opacity");
                                                        boolean overlay = BoolArgumentType.getBool(context, "overlay");
                                                        boolean hasFade = StringArgumentType.getString(context, "fade").equalsIgnoreCase("fadeon");

                                                        MediaOrchestrator.process(url, null, -1, 0, "center", opacity, overlay, hasFade);
                                                        context.getSource().sendFeedback(Component.literal("§a[ViciontMedia] §fIniciando pantalla completa..."));
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                    )
            );

            // VCTMEDIA
            dispatcher.register(ClientCommands.literal("vctmedia")
                    .then(ClientCommands.argument("duracion", LongArgumentType.longArg())
                            .then(ClientCommands.argument("size", IntegerArgumentType.integer())
                                    .then(ClientCommands.argument("pos", StringArgumentType.word())
                                            .then(ClientCommands.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                    .then(ClientCommands.argument("overlay", BoolArgumentType.bool())
                                                            .then(ClientCommands.argument("fade", StringArgumentType.word()).suggests(FADE_SUGGESTIONS)
                                                                    .then(ClientCommands.argument("args", StringArgumentType.greedyString())
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
                                                                                context.getSource().sendFeedback(Component.literal("§6[ViciontMedia] §fProcesando: §7" + url));
                                                                                return 1;
                                                                            }))))))
                                    .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                            .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                    .then(ClientCommands.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                            .then(ClientCommands.argument("overlay", BoolArgumentType.bool())
                                                                    .then(ClientCommands.argument("fade", StringArgumentType.word()).suggests(FADE_SUGGESTIONS)
                                                                            .then(ClientCommands.argument("args", StringArgumentType.greedyString())
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
                                                                                        context.getSource().sendFeedback(Component.literal("§6[ViciontMedia] §fProcesando: §7" + url));
                                                                                        return 1;
                                                                                    }))))))))));

            // VCTEDIT
            dispatcher.register(ClientCommands.literal("vctedit")
                    .then(ClientCommands.argument("nombre", StringArgumentType.string())
                            .then(ClientCommands.argument("duracion", LongArgumentType.longArg())
                                    .then(ClientCommands.argument("size", IntegerArgumentType.integer())
                                            .then(ClientCommands.argument("pos", StringArgumentType.word())
                                                    .then(ClientCommands.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                            .then(ClientCommands.argument("overlay", BoolArgumentType.bool())
                                                                    .executes(context -> {
                                                                        MediaOrchestrator.edit(StringArgumentType.getString(context, "nombre"), LongArgumentType.getLong(context, "duracion"), IntegerArgumentType.getInteger(context, "size"), StringArgumentType.getString(context, "pos"), IntegerArgumentType.getInteger(context, "opacity"), BoolArgumentType.getBool(context, "overlay"));
                                                                        context.getSource().sendFeedback(Component.literal("§6[ViciontMedia] §fEditado: §7" + StringArgumentType.getString(context, "nombre")));
                                                                        return 1;
                                                                    }))))
                                            .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                                    .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                            .then(ClientCommands.argument("opacity", IntegerArgumentType.integer(0, 100))
                                                                    .then(ClientCommands.argument("overlay", BoolArgumentType.bool())
                                                                            .executes(context -> {
                                                                                String posCustom = IntegerArgumentType.getInteger(context, "x") + "," + IntegerArgumentType.getInteger(context, "y");
                                                                                MediaOrchestrator.edit(StringArgumentType.getString(context, "nombre"), LongArgumentType.getLong(context, "duracion"), IntegerArgumentType.getInteger(context, "size"), posCustom, IntegerArgumentType.getInteger(context, "opacity"), BoolArgumentType.getBool(context, "overlay"));
                                                                                context.getSource().sendFeedback(Component.literal("§6[ViciontMedia] §fEditado: §7" + StringArgumentType.getString(context, "nombre")));
                                                                                return 1;
                                                                            })))))))));

            // VCTSTOP
            dispatcher.register(ClientCommands.literal("vctstop")
                    .executes(context -> {
                        var list = MediaOrchestrator.getActiveList();
                        context.getSource().sendFeedback(Component.literal("§6[ViciontMedia] §fActivos:"));
                        for(var m : list) context.getSource().sendFeedback(Component.literal("§7- " + m.url));
                        return 1;
                    })
                    .then(ClientCommands.literal("all").executes(context -> {
                        MediaOrchestrator.stopAll();
                        return 1;
                    }))
                    .then(ClientCommands.argument("nombre", StringArgumentType.string()).executes(context -> {
                        MediaOrchestrator.stopSpecific(StringArgumentType.getString(context, "nombre"));
                        return 1;
                    })));

            // VCTEXT - MODIFICADO CON EL ARGUMENTO SYNC AL FINAL
            dispatcher.register(ClientCommands.literal("vctext")
                    .then(ClientCommands.argument("size", IntegerArgumentType.integer())
                            .then(ClientCommands.argument("animacion", StringArgumentType.string()).suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Arrays.asList("default", "fade", "izquierda", "derecha", "arriba", "abajo"), builder))
                                    .then(ClientCommands.argument("bgcolor", StringArgumentType.string())
                                            .then(ClientCommands.argument("tiempo", IntegerArgumentType.integer(1))
                                                    .then(ClientCommands.argument("pos", StringArgumentType.string()).suggests(POS_SUGGESTIONS)
                                                            .then(ClientCommands.argument("sync", BoolArgumentType.bool())
                                                                    .then(ClientCommands.argument("texto", StringArgumentType.greedyString())
                                                                            .executes(context -> {
                                                                                int size = IntegerArgumentType.getInteger(context, "size");
                                                                                String anim = StringArgumentType.getString(context, "animacion");
                                                                                String bgColor = StringArgumentType.getString(context, "bgcolor");
                                                                                int duration = IntegerArgumentType.getInteger(context, "tiempo");
                                                                                String pos = StringArgumentType.getString(context, "pos");
                                                                                boolean sync = BoolArgumentType.getBool(context, "sync");
                                                                                String text = StringArgumentType.getString(context, "texto");

                                                                                TextOrchestrator.addText(bgColor, duration, pos, text, size, anim, sync);
                                                                                context.getSource().sendFeedback(Component.literal("§a[ViciontMedia] §fTexto mostrado en " + pos));
                                                                                return 1;
                                                                            })))))))));

            // VCTEXTDEL
            dispatcher.register(ClientCommands.literal("vctextdel")
                    .then(ClientCommands.argument("textoActivo", StringArgumentType.greedyString())
                            .suggests(ACTIVE_TEXTS_SUGGESTIONS)
                            .executes(context -> {
                                String textToRemove = StringArgumentType.getString(context, "textoActivo");
                                TextOrchestrator.removeTextByContent(textToRemove);
                                context.getSource().sendFeedback(Component.literal("§c[ViciontMedia] §fTexto eliminado: " + textToRemove));
                                return 1;
                            })));

            // VCTSS Y VCTSSRM
            dispatcher.register(ClientCommands.literal("vctss")
                    .then(ClientCommands.argument("shader", StringArgumentType.word())
                            .suggests(SHADER_SUGGESTIONS)
                            .executes(context -> {
                                String shaderName = StringArgumentType.getString(context, "shader");
                                VctShaderManager.loadShader(shaderName);
                                context.getSource().sendFeedback(Component.literal("§d[ViciontMedia] §fAplicando Super Secret Setting: §e" + shaderName));
                                return 1;
                            })));

            dispatcher.register(ClientCommands.literal("vctssrm")
                    .then(ClientCommands.argument("shader", StringArgumentType.word())
                            .suggests(SHADER_SUGGESTIONS)
                            .executes(context -> {
                                String shaderName = StringArgumentType.getString(context, "shader");
                                VctShaderManager.removeShader(shaderName);
                                context.getSource().sendFeedback(Component.literal("§c[ViciontMedia] §fEliminando shader: §e" + shaderName));
                                return 1;
                            })));

        });
    }
}