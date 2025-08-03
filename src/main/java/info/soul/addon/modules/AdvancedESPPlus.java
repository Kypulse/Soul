package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdvancedESPPlus extends Module {
    // Setting Groups
    private final SettingGroup sgKelp = settings.createGroup("Kelp ESP");
    private final SettingGroup sgVillager = settings.createGroup("Villager ESP");
    private final SettingGroup sgVine = settings.createGroup("Vine ESP");
    private final SettingGroup sgStone = settings.createGroup("Stone ESP");
    private final SettingGroup sgWanderingTrader = settings.createGroup("Wandering Trader ESP");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");
    private final SettingGroup sgThreading = settings.createGroup("Threading");

    // Kelp ESP Settings
    private final Setting<Boolean> enableKelp = sgKelp.add(new BoolSetting.Builder()
        .name("enable-kelp-esp")
        .description("Enable kelp chunk detection")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> kelpColor = sgKelp.add(new ColorSetting.Builder()
        .name("kelp-color")
        .description("Kelp ESP box color")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .visible(enableKelp::get)
        .build());

    private final Setting<ShapeMode> kelpShapeMode = sgKelp.add(new EnumSetting.Builder<ShapeMode>()
        .name("kelp-shape-mode")
        .description("Kelp ESP box render mode")
        .defaultValue(ShapeMode.Lines)
        .visible(enableKelp::get)
        .build());

    private final Setting<Boolean> kelpChat = sgKelp.add(new BoolSetting.Builder()
        .name("kelp-chat")
        .description("Announce flagged kelp chunks in chat")
        .defaultValue(true)
        .visible(enableKelp::get)
        .build());

    // Villager ESP Settings
    private final Setting<Boolean> enableVillager = sgVillager.add(new BoolSetting.Builder()
        .name("enable-villager-esp")
        .description("Enable villager detection")
        .defaultValue(true)
        .build());

    private final Setting<DetectionMode> villagerDetectionMode = sgVillager.add(new EnumSetting.Builder<DetectionMode>()
        .name("villager-detection-mode")
        .description("What type of villagers to detect")
        .defaultValue(DetectionMode.Both)
        .visible(enableVillager::get)
        .build());

    private final Setting<Boolean> villagerTracers = sgVillager.add(new BoolSetting.Builder()
        .name("villager-tracers")
        .description("Draw tracer lines to villagers")
        .defaultValue(true)
        .visible(enableVillager::get)
        .build());

    private final Setting<SettingColor> villagerTracerColor = sgVillager.add(new ColorSetting.Builder()
        .name("villager-tracer-color")
        .defaultValue(new SettingColor(0, 255, 0, 127))
        .visible(() -> enableVillager.get() && villagerTracers.get() && (villagerDetectionMode.get() != DetectionMode.ZombieVillagers))
        .build());

    private final Setting<SettingColor> zombieVillagerTracerColor = sgVillager.add(new ColorSetting.Builder()
        .name("zombie-villager-tracer-color")
        .defaultValue(new SettingColor(255, 0, 0, 127))
        .visible(() -> enableVillager.get() && villagerTracers.get() && (villagerDetectionMode.get() != DetectionMode.Villagers))
        .build());

    private final Setting<NotificationMode> villagerNotificationMode = sgVillager.add(new EnumSetting.Builder<NotificationMode>()
        .name("villager-notification-mode")
        .defaultValue(NotificationMode.Both)
        .visible(enableVillager::get)
        .build());

    private final Setting<Boolean> villagerToggleOnFind = sgVillager.add(new BoolSetting.Builder()
        .name("villager-toggle-on-find")
        .defaultValue(false)
        .visible(enableVillager::get)
        .build());

    private final Setting<Boolean> villagerDisconnect = sgVillager.add(new BoolSetting.Builder()
        .name("villager-disconnect")
        .defaultValue(false)
        .visible(enableVillager::get)
        .build());

    // Vine ESP Settings
    private final Setting<Boolean> enableVine = sgVine.add(new BoolSetting.Builder()
        .name("enable-vine-esp")
        .description("Enable long vine detection")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> vineColor = sgVine.add(new ColorSetting.Builder()
        .name("vine-color")
        .description("ESP box color for long vines")
        .defaultValue(new SettingColor(100, 255, 100, 100))
        .visible(enableVine::get)
        .build());

    private final Setting<ShapeMode> vineShapeMode = sgVine.add(new EnumSetting.Builder<ShapeMode>()
        .name("vine-shape-mode")
        .description("ESP box render mode")
        .defaultValue(ShapeMode.Both)
        .visible(enableVine::get)
        .build());

    private final Setting<Boolean> vineTracers = sgVine.add(new BoolSetting.Builder()
        .name("vine-tracers")
        .description("Draw tracers to vine chains")
        .defaultValue(false)
        .visible(enableVine::get)
        .build());

    private final Setting<SettingColor> vineTracerColor = sgVine.add(new ColorSetting.Builder()
        .name("vine-tracer-color")
        .description("Vine tracer color")
        .defaultValue(new SettingColor(100, 255, 100, 200))
        .visible(() -> enableVine.get() && vineTracers.get())
        .build());

    private final Setting<Boolean> vineChat = sgVine.add(new BoolSetting.Builder()
        .name("vine-chat")
        .description("Announce long vines in chat")
        .defaultValue(true)
        .visible(enableVine::get)
        .build());

    private final Setting<Integer> vineMinLength = sgVine.add(new IntSetting.Builder()
        .name("vine-min-length")
        .description("Minimum vine chain length to show ESP")
        .defaultValue(4)
        .min(2)
        .max(64)
        .sliderRange(2, 64)
        .visible(enableVine::get)
        .build());

    // Stone ESP Settings
    private final Setting<Boolean> enableStone = sgStone.add(new BoolSetting.Builder()
        .name("enable-stone-esp")
        .description("Enable stone block detection above Y=10")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> stoneColor = sgStone.add(new ColorSetting.Builder()
        .name("stone-color")
        .description("ESP box color for illegal stone blocks")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(enableStone::get)
        .build());

    private final Setting<ShapeMode> stoneShapeMode = sgStone.add(new EnumSetting.Builder<ShapeMode>()
        .name("stone-shape-mode")
        .description("Box render mode")
        .defaultValue(ShapeMode.Both)
        .visible(enableStone::get)
        .build());

    private final Setting<Boolean> stoneChat = sgStone.add(new BoolSetting.Builder()
        .name("stone-chat")
        .description("Announce detections in chat")
        .defaultValue(true)
        .visible(enableStone::get)
        .build());

    // Wandering Trader ESP Settings
    private final Setting<Boolean> enableWanderingTrader = sgWanderingTrader.add(new BoolSetting.Builder()
        .name("enable-wandering-trader-esp")
        .description("Enable wandering trader and trader llama detection")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> traderSoundNotification = sgWanderingTrader.add(new BoolSetting.Builder()
        .name("trader-sound-notification")
        .description("Plays a sound when a wandering trader is detected")
        .defaultValue(true)
        .visible(enableWanderingTrader::get)
        .build());

    private final Setting<SettingColor> traderTracerColor = sgWanderingTrader.add(new ColorSetting.Builder()
        .name("trader-tracer-color")
        .description("The color of the tracer to wandering traders")
        .defaultValue(new SettingColor(0, 255, 255, 127))
        .visible(enableWanderingTrader::get)
        .build());

    private final Setting<SettingColor> llamaTracerColor = sgWanderingTrader.add(new ColorSetting.Builder()
        .name("llama-tracer-color")
        .description("The color of the tracer to trader llamas")
        .defaultValue(new SettingColor(255, 255, 0, 127))
        .visible(enableWanderingTrader::get)
        .build());

    private final Setting<Boolean> traderChat = sgWanderingTrader.add(new BoolSetting.Builder()
        .name("trader-chat")
        .description("Announce wandering trader detections in chat")
        .defaultValue(true)
        .visible(enableWanderingTrader::get)
        .build());

    // Webhook Settings
    private final Setting<Boolean> enableWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("enable-webhook")
        .defaultValue(false)
        .build());

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build());

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build());

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build());

    // Threading Settings
    private final Setting<Boolean> useThreading = sgThreading.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for chunk scanning")
        .defaultValue(true)
        .build());

    private final Setting<Integer> threadPoolSize = sgThreading.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads to use")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build());

    // Data Storage - Use synchronized collections for thread safety
    private final Set<ChunkPos> flaggedKelpChunks = Collections.synchronizedSet(new HashSet<>());
    private final Set<Integer> detectedVillagers = Collections.synchronizedSet(new HashSet<>());
    private final Set<BlockPos> vineBottoms = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> detectedStoneBlocks = Collections.synchronizedSet(new HashSet<>());

    // Wandering Trader data
    private final Set<Integer> detectedTraders = Collections.synchronizedSet(new HashSet<>());
    private final Set<Integer> detectedLlamas = Collections.synchronizedSet(new HashSet<>());
    private boolean hasNotifiedTrader = false;

    private ExecutorService threadPool;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Illegal blocks for stone ESP
    private static final Set<Block> illegalAboveY10 = Set.of(
        Blocks.DEEPSLATE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.DEEPSLATE_COPPER_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
        Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_TILES,
        Blocks.INFESTED_DEEPSLATE,
        Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.NETHER_QUARTZ_ORE,
        Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS, Blocks.BASALT, Blocks.POLISHED_BASALT,
        Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
        Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.CHORUS_FLOWER, Blocks.CHORUS_PLANT,
        Blocks.SPAWNER, Blocks.BEDROCK
    );

    public AdvancedESPPlus() {
        super(SoulAddon.CATEGORY, "AdvancedESP+", "ESP for Vines Villagers Kelp Stone and Wandering Traders");
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;

        try {
            if (useThreading.get()) {
                threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
            }

            // Clear all data
            flaggedKelpChunks.clear();
            detectedVillagers.clear();
            vineBottoms.clear();
            detectedStoneBlocks.clear();
            detectedTraders.clear();
            detectedLlamas.clear();
            hasNotifiedTrader = false;

            // Check for existing wandering traders
            if (enableWanderingTrader.get() && mc.world != null) {
                for (Entity entity : mc.world.getEntities()) {
                    if (entity instanceof WanderingTraderEntity) {
                        handleTraderAdded(entity);
                    } else if (entity instanceof TraderLlamaEntity) {
                        handleLlamaAdded(entity);
                    }
                }
            }

            // Scan all loaded chunks
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    try {
                        if (useThreading.get() && threadPool != null) {
                            threadPool.submit(() -> {
                                try {
                                    scanChunk(worldChunk);
                                } catch (Exception e) {
                                    error("Error scanning chunk: " + e.getMessage());
                                }
                            });
                        } else {
                            scanChunk(worldChunk);
                        }
                    } catch (Exception e) {
                        error("Error processing chunk: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            error("Error during activation: " + e.getMessage());
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdownNow();
                threadPool = null;
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
            threadPool = null;
        }

        flaggedKelpChunks.clear();
        detectedVillagers.clear();
        vineBottoms.clear();
        detectedStoneBlocks.clear();
        detectedTraders.clear();
        detectedLlamas.clear();
        hasNotifiedTrader = false;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!enableWanderingTrader.get()) return;

        if (event.entity instanceof WanderingTraderEntity) {
            handleTraderAdded(event.entity);
        } else if (event.entity instanceof TraderLlamaEntity) {
            handleLlamaAdded(event.entity);
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!enableWanderingTrader.get()) return;

        if (event.entity instanceof WanderingTraderEntity) {
            detectedTraders.remove(event.entity.getId());
            if (detectedTraders.isEmpty() && detectedLlamas.isEmpty()) {
                hasNotifiedTrader = false;
            }
        } else if (event.entity instanceof TraderLlamaEntity) {
            detectedLlamas.remove(event.entity.getId());
            if (detectedTraders.isEmpty() && detectedLlamas.isEmpty()) {
                hasNotifiedTrader = false;
            }
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (!isActive() || event.chunk() == null) return;

        try {
            if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                threadPool.submit(() -> {
                    try {
                        scanChunk(event.chunk());
                    } catch (Exception e) {
                        error("Chunk scan error: " + e.getMessage());
                    }
                });
            } else {
                scanChunk(event.chunk());
            }
        } catch (Exception e) {
            error("Chunk load error: " + e.getMessage());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        Chunk chunk = mc.world.getChunk(pos);
        if (chunk instanceof WorldChunk worldChunk) {
            if (enableKelp.get()) scanChunkForKelp(worldChunk);
            if (enableStone.get()) checkStoneBlock(pos, event.newState);
        }
    }

    private void handleTraderAdded(Entity trader) {
        detectedTraders.add(trader.getId());

        if (!hasNotifiedTrader) {
            notifyTraderFound(trader);
            hasNotifiedTrader = true;
        }
    }

    private void handleLlamaAdded(Entity llama) {
        detectedLlamas.add(llama.getId());

        // If we haven't notified yet and there's no trader detected, notify about the llama
        if (!hasNotifiedTrader && detectedTraders.isEmpty()) {
            notifyLlamaFound(llama);
            hasNotifiedTrader = true;
        }
    }

    private void notifyTraderFound(Entity trader) {
        if (traderChat.get()) {
            ChatUtils.sendMsg(Text.literal("")
                .append(Text.literal("Wandering Trader").formatted(Formatting.AQUA))
                .append(Text.literal(" found at ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("[%d, %d, %d]",
                    (int)trader.getX(),
                    (int)trader.getY(),
                    (int)trader.getZ())).formatted(Formatting.WHITE))
            );
        }

        if (traderSoundNotification.get() && mc.world != null && mc.player != null) {
            mc.world.playSoundFromEntity(mc.player, mc.player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.AMBIENT, 3.0F, 1.0F);
        }
    }

    private void notifyLlamaFound(Entity llama) {
        if (traderChat.get()) {
            ChatUtils.sendMsg(Text.literal("")
                .append(Text.literal("Trader Llama").formatted(Formatting.YELLOW))
                .append(Text.literal(" found at ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format("[%d, %d, %d]",
                    (int)llama.getX(),
                    (int)llama.getY(),
                    (int)llama.getZ())).formatted(Formatting.WHITE))
            );
        }

        if (traderSoundNotification.get() && mc.world != null && mc.player != null) {
            mc.world.playSoundFromEntity(mc.player, mc.player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.AMBIENT, 3.0F, 1.0F);
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (chunk == null) return;

        try {
            if (enableKelp.get()) scanChunkForKelp(chunk);
            if (enableVine.get()) scanChunkForVines(chunk);
            if (enableStone.get()) scanChunkForStone(chunk);
        } catch (Exception e) {
            error("Error scanning chunk: " + e.getMessage());
        }
    }

    // Kelp ESP Logic
    private void scanChunkForKelp(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        flaggedKelpChunks.remove(cpos);

        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        int kelpColumns = 0;
        int kelpTopsAt62 = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                int bottom = -1;
                int top = -1;

                for (int y = yMin; y < yMax; y++) {
                    Block block = chunk.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
                        if (bottom < 0) bottom = y;
                        top = y;
                    }
                }

                if (bottom >= 0 && top - bottom + 1 >= 8) {
                    kelpColumns++;
                    if (top == 62) kelpTopsAt62++;
                }
            }
        }

        if (kelpColumns >= 10 && ((double) kelpTopsAt62 / kelpColumns) >= 0.6) {
            flaggedKelpChunks.add(cpos);
            if (kelpChat.get()) {
                info("§5[§dKelpESP§5] §aChunk " + cpos + " flagged: " + kelpTopsAt62 + "/" + kelpColumns + " kelp tops at Y=62");
            }
        }
    }

    // Vine ESP Logic
    private void scanChunkForVines(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        Set<BlockPos> chunkBottoms = new HashSet<>();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMax - 1; y >= yMin; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isVineBlock(state)) {
                        VineInfo info = getVineInfo(pos);
                        if (info != null && info.length >= vineMinLength.get()) {
                            chunkBottoms.add(info.bottomPos);
                            if (!vineBottoms.contains(info.bottomPos) && vineChat.get()) {
                                info("§2[VineESP] Long vine at " + pos.toShortString() + " (length " + info.length + ")");
                            }
                        }
                    }
                }
            }
        }

        vineBottoms.removeIf(pos -> new ChunkPos(pos).equals(cpos) && !chunkBottoms.contains(pos));
        vineBottoms.addAll(chunkBottoms);
    }

    // Stone ESP Logic
    private void scanChunkForStone(Chunk chunk) {
        int xStart = chunk.getPos().getStartX();
        int zStart = chunk.getPos().getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = Math.max(11, yMin); y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);

                    if (isIllegalBlock(state, y) && detectedStoneBlocks.add(pos) && stoneChat.get()) {
                        info("§d[StoneESP] §fPlayer placed block (" + state.getBlock().getName().getString() + ") at §a" + pos.toShortString());
                    }
                }
            }
        }
    }

    private void checkStoneBlock(BlockPos pos, BlockState state) {
        if (isIllegalBlock(state, pos.getY())) {
            if (detectedStoneBlocks.add(pos) && stoneChat.get()) {
                info("§d[StoneESP] §fPlayer placed block (" + state.getBlock().getName().getString() + ") at §a" + pos.toShortString());
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        try {
            // Render Kelp ESP
            if (enableKelp.get()) {
                Color kelpSide = new Color(kelpColor.get());
                Color kelpOutline = new Color(kelpColor.get());

                // Create a copy to avoid concurrent modification
                Set<ChunkPos> kelpCopy = new HashSet<>(flaggedKelpChunks);
                for (ChunkPos pos : kelpCopy) {
                    event.renderer.box(
                        pos.getStartX(), 63, pos.getStartZ(),
                        pos.getStartX() + 16, 64, pos.getStartZ() + 16,
                        kelpSide, kelpOutline, kelpShapeMode.get(), 0
                    );
                }
            }

            // Render Villager ESP
            if (enableVillager.get()) {
                handleVillagerRendering(event);
            }

            // Render Vine ESP
            if (enableVine.get()) {
                Vec3d playerPos = mc.player.getPos();
                Color vineSide = new Color(vineColor.get());
                Color vineOutline = new Color(vineColor.get());
                Color vineTracer = new Color(vineTracerColor.get());

                // Create a copy to avoid concurrent modification
                Set<BlockPos> vineCopy = new HashSet<>(vineBottoms);
                for (BlockPos pos : vineCopy) {
                    event.renderer.box(pos, vineSide, vineOutline, vineShapeMode.get(), 0);

                    if (vineTracers.get()) {
                        Vec3d center = Vec3d.ofCenter(pos);
                        event.renderer.line(playerPos.x, playerPos.y, playerPos.z, center.x, center.y, center.z, vineTracer);
                    }
                }
            }

            // Render Stone ESP
            if (enableStone.get()) {
                Color stoneSide = new Color(stoneColor.get());
                Color stoneLines = new Color(stoneColor.get());

                // Create a copy to avoid concurrent modification
                Set<BlockPos> stoneCopy = new HashSet<>(detectedStoneBlocks);
                for (BlockPos pos : stoneCopy) {
                    event.renderer.box(pos, stoneSide, stoneLines, stoneShapeMode.get(), 0);
                }
            }

            // Render Wandering Trader ESP
            if (enableWanderingTrader.get()) {
                handleWanderingTraderRendering(event);
            }
        } catch (Exception e) {
            error("Render error: " + e.getMessage());
        }
    }

    private void handleVillagerRendering(Render3DEvent event) {
        Set<Integer> currentVillagers = new HashSet<>();
        int villagerCount = 0;
        int zombieVillagerCount = 0;

        for (var entity : mc.world.getEntities()) {
            boolean shouldDetect = false;
            Color tracerColor = null;

            if (entity instanceof VillagerEntity && (villagerDetectionMode.get() != DetectionMode.ZombieVillagers)) {
                shouldDetect = true;
                tracerColor = new Color(villagerTracerColor.get());
                villagerCount++;
            } else if (entity instanceof ZombieVillagerEntity && (villagerDetectionMode.get() != DetectionMode.Villagers)) {
                shouldDetect = true;
                tracerColor = new Color(zombieVillagerTracerColor.get());
                zombieVillagerCount++;
            }

            if (shouldDetect) {
                currentVillagers.add(entity.getId());

                if (villagerTracers.get()) {
                    double x = entity.prevX + (entity.getX() - entity.prevX) * event.tickDelta;
                    double y = entity.prevY + (entity.getY() - entity.prevY) * event.tickDelta + entity.getHeight() / 2;
                    double z = entity.prevZ + (entity.getZ() - entity.prevZ) * event.tickDelta;

                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, tracerColor);
                }
            }
        }

        if (!currentVillagers.isEmpty() && !currentVillagers.equals(detectedVillagers)) {
            Set<Integer> newVillagers = new HashSet<>(currentVillagers);
            newVillagers.removeAll(detectedVillagers);

            if (!newVillagers.isEmpty()) {
                detectedVillagers.addAll(newVillagers);
                handleVillagerDetection(villagerCount, zombieVillagerCount);
            }
        } else if (currentVillagers.isEmpty()) {
            detectedVillagers.clear();
        }
    }

    private void handleWanderingTraderRendering(Render3DEvent event) {
        // Render tracers to all detected traders
        for (Integer id : new HashSet<>(detectedTraders)) {
            Entity entity = mc.world.getEntityById(id);
            if (entity instanceof WanderingTraderEntity trader) {
                renderTraderTracer(event, trader, new Color(traderTracerColor.get()));
            }
        }

        // Render tracers to all detected llamas
        for (Integer id : new HashSet<>(detectedLlamas)) {
            Entity entity = mc.world.getEntityById(id);
            if (entity instanceof TraderLlamaEntity llama) {
                renderTraderTracer(event, llama, new Color(llamaTracerColor.get()));
            }
        }
    }

    private void renderTraderTracer(Render3DEvent event, Entity entity, Color color) {
        double x = entity.prevX + (entity.getX() - entity.prevX) * event.tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * event.tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * event.tickDelta;

        double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
        y += height / 2;

        event.renderer.line(
            RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
            x, y, z,
            color
        );
    }

    private void handleVillagerDetection(int villagerCount, int zombieVillagerCount) {
        String message = buildDetectionMessage(villagerCount, zombieVillagerCount);

        switch (villagerNotificationMode.get()) {
            case Chat -> info("(highlight)%s", message);
            case Toast -> mc.getToastManager().add(new MeteorToast(Items.EMERALD, "VillagerESP", message));
            case Both -> {
                info("(highlight)%s", message);
                mc.getToastManager().add(new MeteorToast(Items.EMERALD, "VillagerESP", message));
            }
        }

        if (enableWebhook.get()) sendWebhookNotification(villagerCount, zombieVillagerCount);
        if (villagerToggleOnFind.get()) toggle();
        if (villagerDisconnect.get()) disconnectFromServer(message);
    }

    // Helper Methods
    private boolean isVineBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.VINE
            || block == Blocks.WEEPING_VINES
            || block == Blocks.WEEPING_VINES_PLANT
            || block == Blocks.TWISTING_VINES
            || block == Blocks.TWISTING_VINES_PLANT
            || block == Blocks.GLOW_LICHEN;
    }

    private boolean isIllegalBlock(BlockState state, int y) {
        return y > 10 && illegalAboveY10.contains(state.getBlock());
    }

    private VineInfo getVineInfo(BlockPos startPos) {
        if (mc.world == null || startPos == null) return null;

        try {
            int length = 0;
            BlockPos current = startPos;
            BlockPos bottom = startPos;

            while (current.getY() > mc.world.getBottomY() && isVineBlock(mc.world.getBlockState(current))) {
                bottom = current;
                length++;
                current = current.down();

                if (length > 64) break;
            }

            return length >= 1 ? new VineInfo(length, bottom) : null;
        } catch (Exception e) {
            error("Error getting vine info: " + e.getMessage());
            return null;
        }
    }

    private String buildDetectionMessage(int villagers, int zombies) {
        if (villagerDetectionMode.get() == DetectionMode.Villagers) {
            return villagers == 1 ? "Villager detected!" : villagers + " villagers detected!";
        } else if (villagerDetectionMode.get() == DetectionMode.ZombieVillagers) {
            return zombies == 1 ? "Zombie villager detected!" : zombies + " zombie villagers detected!";
        } else {
            if (villagers > 0 && zombies > 0)
                return villagers + " villagers and " + zombies + " zombie villagers detected!";
            else if (villagers > 0)
                return villagers == 1 ? "Villager detected!" : villagers + " villagers detected!";
            else
                return zombies == 1 ? "Zombie villager detected!" : zombies + " zombie villagers detected!";
        }
    }

    private void sendWebhookNotification(int villagers, int zombies) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) {
            warning("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Unknown";
                String coords = mc.player != null
                    ? String.format("X: %.0f, Y: %.0f, Z: %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    : "Unknown";

                String mention = (selfPing.get() && !discordId.get().isEmpty()) ? "<@" + discordId.get().trim() + ">" : "";

                String json = String.format("""
                    {
                      "content": "%s",
                      "username": "AdvancedESP+",
                      "avatar_url": "https://i.imgur.com/gVzV8ve.jpeg",
                      "embeds": [{
                        "title": " Villager Alert",
                        "description": "%s",
                        "color": 65280,
                        "thumbnail": {"url": "https://i.imgur.com/gVzV8ve.jpeg"},
                        "fields": [
                          {"name": "Server", "value": "%s", "inline": true},
                          {"name": "Villagers", "value": "%d", "inline": true},
                          {"name": "Zombie Villagers", "value": "%d", "inline": true},
                          {"name": "Coordinates", "value": "%s", "inline": false},
                          {"name": "Time", "value": "<t:%d:R>", "inline": true}
                        ],
                        "footer": {"text": "Sent by AdvancedESP+"}
                      }]
                    }""",
                    mention, buildDetectionMessage(villagers, zombies), server,
                    villagers, zombies, coords, System.currentTimeMillis() / 1000
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(15))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 204) info("Webhook sent.");
                else error("Webhook failed: " + response.statusCode());

            } catch (IOException | InterruptedException e) {
                error("Webhook error: " + e.getMessage());
            }
        });
    }

    private void disconnectFromServer(String reason) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            info("Disconnected from server - " + reason);
        }
    }

    @Override
    public String getInfoString() {
        int total = 0;
        if (enableKelp.get()) total += flaggedKelpChunks.size();
        if (enableVillager.get()) total += detectedVillagers.size();
        if (enableVine.get()) total += vineBottoms.size();
        if (enableStone.get()) total += detectedStoneBlocks.size();
        if (enableWanderingTrader.get()) total += detectedTraders.size() + detectedLlamas.size();
        return total == 0 ? null : String.valueOf(total);
    }

    private static class VineInfo {
        final int length;
        final BlockPos bottomPos;

        VineInfo(int length, BlockPos bottomPos) {
            this.length = length;
            this.bottomPos = bottomPos;
        }
    }

    public enum NotificationMode {
        Chat, Toast, Both
    }

    public enum DetectionMode {
        Villagers("Villagers"),
        ZombieVillagers("Zombie Villagers"),
        Both("Both");

        private final String name;
        DetectionMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }
}
