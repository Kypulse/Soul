package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import info.soul.addon.utils.render.blockesp.ESPBlock;
import info.soul.addon.utils.render.blockesp.ESPBlockData;
import info.soul.addon.utils.render.blockesp.ESPChunk;
import info.soul.addon.utils.render.blockesp.ESPGroup;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.RainbowColors;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;


import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to search for.")
        .onChanged(blocks1 -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
        .build()
    );

    private final Setting<ESPBlockData> defaultBlockConfig = sgGeneral.add(new GenericSetting.Builder<ESPBlockData>()
        .name("default-block-config")
        .description("Default block config.")
        .defaultValue(
            new ESPBlockData(
                ShapeMode.Lines,
                new SettingColor(0, 255, 200),
                new SettingColor(0, 255, 200, 25),
                true,
                new SettingColor(0, 255, 200, 125)
            )
        )
        .build()
    );

    private final Setting<Map<Block, ESPBlockData>> blockConfigs = sgGeneral.add(new BlockDataSetting.Builder<ESPBlockData>()
        .name("block-configs")
        .description("Config for each block.")
        .defaultData(defaultBlockConfig)
        .build()
    );

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
    .name("discord-webhook")
    .description("Webhook to send alerts.")
    .defaultValue("https://discord.com/api/webhooks/...")
    .build()
);

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Render tracer lines.")
        .defaultValue(false)
        .build()
    );

    private void sendWebhook(String message) {
    String url = webhookUrl.get();
    if (url == null || url.isEmpty()) return;

    try {
        URL webhook = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) webhook.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String username = mc.getSession().getUsername();
        String avatarUrl = "https://mc-heads.net/avatar/" + username;

        String payload = """
            {
              "embeds": [{
                "title": "%s",
                "description": "%s",
                "thumbnail": {
                  "url": "%s"
                },
                "color": 16711680
              }]
            }
        """.formatted(username + " Found a Spawner/Block", message, avatarUrl);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        connection.getResponseCode();
    } catch (Exception ignored) {}
}

    private final BlockPos.Mutable blockPos = new BlockPos.Mutable();

    private final Long2ObjectMap<ESPChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final Set<ESPGroup> groups = new ReferenceOpenHashSet<>();
    private final ExecutorService workerThread = Executors.newSingleThreadExecutor();

    private Dimension lastDimension;

    public BlockESP() {
        super(SoulAddon.CATEGORY, "Block ESP", "Webhook Alerts");

        RainbowColors.register(this::onTickRainbow);
    }

    @Override
    public void onActivate() {
        synchronized (chunks) {
            chunks.clear();
            groups.clear();
        }

        for (Chunk chunk : Utils.chunks()) {
            searchChunk(chunk);
        }

        lastDimension = PlayerUtils.getDimension();
    }

    @Override
    public void onDeactivate() {
        synchronized (chunks) {
            chunks.clear();
            groups.clear();
        }
    }

    private void onTickRainbow() {
        if (!isActive()) return;

        defaultBlockConfig.get().tickRainbow();
        for (ESPBlockData blockData : blockConfigs.get().values()) blockData.tickRainbow();
    }

    public ESPBlockData getBlockData(Block block) {
        ESPBlockData blockData = blockConfigs.get().get(block);
        return blockData == null ? defaultBlockConfig.get() : blockData;
    }

    private void updateChunk(int x, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.toLong(x, z));
        if (chunk != null) chunk.update();
    }

    private void updateBlock(int x, int y, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
        if (chunk != null) chunk.update(x, y, z);
    }

    public ESPBlock getBlock(int x, int y, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
        return chunk == null ? null : chunk.get(x, y, z);
    }

    public ESPGroup newGroup(Block block) {
        synchronized (chunks) {
            ESPGroup group = new ESPGroup(block);
            groups.add(group);
            return group;
        }
    }

    public void removeGroup(ESPGroup group) {
        synchronized (chunks) {
            groups.remove(group);
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        searchChunk(event.chunk());
    }

    private void searchChunk(Chunk chunk) {
        workerThread.submit(() -> {
            if (!isActive()) return;
            ESPChunk schunk = ESPChunk.searchChunk(chunk, blocks.get());

            if (schunk.size() > 0) {
    sendWebhook("BlockESP detected **" + schunk.size() + "** matching blocks at chunk (" + chunk.getPos().x + ", " + chunk.getPos().z + ").");
    mc.player.networkHandler.onDisconnect(new net.minecraft.network.packet.s2c.common.DisconnectS2CPacket(
        net.minecraft.text.Text.literal("§7[§cBlock/Spawner Found§7] A Block/Spawner Was Found.")
    ));
    toggle();

    synchronized (chunks) {
        chunks.put(chunk.getPos().toLong(), schunk);
        schunk.update();
                    // Update neighbour chunks
                    updateChunk(chunk.getPos().x - 1, chunk.getPos().z);
                    updateChunk(chunk.getPos().x + 1, chunk.getPos().z);
                    updateChunk(chunk.getPos().x, chunk.getPos().z - 1);
                    updateChunk(chunk.getPos().x, chunk.getPos().z + 1);
                }
            }
        });
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {

        int bx = event.pos.getX();
        int by = event.pos.getY();
        int bz = event.pos.getZ();

        int chunkX = bx >> 4;
        int chunkZ = bz >> 4;
        long key = ChunkPos.toLong(chunkX, chunkZ);

        boolean added = blocks.get().contains(event.newState.getBlock()) && !blocks.get().contains(event.oldState.getBlock());
        boolean removed = !added && !blocks.get().contains(event.newState.getBlock()) && blocks.get().contains(event.oldState.getBlock());

        if (added || removed) {
            workerThread.submit(() -> {
                synchronized (chunks) {
                    ESPChunk chunk = chunks.get(key);

                    if (chunk == null) {
                        chunk = new ESPChunk(chunkX, chunkZ);
                        if (chunk.shouldBeDeleted()) return;

                        chunks.put(key, chunk);
                    }

                    blockPos.set(bx, by, bz);

                    if (added) chunk.add(blockPos);
                    else chunk.remove(blockPos);

                    for (int x = -1; x < 2; x++) {
                        for (int z = -1; z < 2; z++) {
                            for (int y = -1; y < 2; y++) {
                                if (x == 0 && y == 0 && z == 0) continue;

                                updateBlock(bx + x, by + y, bz + z);
                            }
                        }
                    }
                }
            });
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        Dimension dimension = PlayerUtils.getDimension();

        if (lastDimension != dimension) onActivate();

        lastDimension = dimension;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (chunks) {
            for (Iterator<ESPChunk> it = chunks.values().iterator(); it.hasNext();) {
                ESPChunk chunk = it.next();

                if (chunk.shouldBeDeleted()) {
                    workerThread.submit(() -> {
                        for (ESPBlock block : chunk.blocks.values()) {
                            block.group.remove(block, false);
                            block.loaded = false;
                        }
                    });

                    it.remove();
                }
                else chunk.render(event);
            }

            if (tracers.get()) {
                for (ESPGroup group : groups) {
                    group.render(event);
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        return "%s groups".formatted(groups.size());
    }
}
