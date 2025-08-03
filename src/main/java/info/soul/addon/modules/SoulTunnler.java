package info.soul.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.StorageESP;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import info.soul.addon.SoulAddon;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SoulTunnler extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<Boolean> enableStorageESP = sgGeneral.add(new BoolSetting.Builder()
        .name("Enable StorageESP While Mining")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> disconnectOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("Disconnect On Base Found")
        .defaultValue(true)
        .build());

    private final Setting<Integer> storageThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("Storage Block Threshold")
        .defaultValue(2)
        .min(1)
        .sliderMax(10)
        .build());

    private final Setting<Integer> spawnerThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("Spawner Threshold")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .build());

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("Webhook URL")
        .defaultValue("")
        .build());

    private final Setting<Boolean> baseFindWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("Base Found Webhook")
        .defaultValue(true)
        .build());

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Set<ChunkPos> processedChunks = new HashSet<>();
    private boolean tunnelStarted = false;
    private final Random random = new Random();

    public SoulTunnler() {
        super(SoulAddon.CATEGORY, "SoulTunnler", "Stealth base-finding module using Baritone tunneling.");
    }

    @Override
    public void onActivate() {
        processedChunks.clear();
        tunnelStarted = false;

        ChatUtils.sendPlayerMsg("#set smoothLook true");
        ChatUtils.sendPlayerMsg("#set legitMine true");
        ChatUtils.sendPlayerMsg("#blocksToAvoidBreaking minecraft:water");
        ChatUtils.sendPlayerMsg("#blocksToAvoidBreaking minecraft:lava");
        ChatUtils.sendPlayerMsg("#blocksToAvoidBreaking minecraft:gravel");
        ChatUtils.sendPlayerMsg("#set smoothLookTicks 10");

        if (mc.player != null) startTunneling();
    }

    @Override
    public void onDeactivate() {
        ChatUtils.sendPlayerMsg("#stop");
        ChatUtils.sendPlayerMsg("#cancel");
        toggleModule(StorageESP.class, false);
        processedChunks.clear();
        tunnelStarted = false;
        info("SoulTunnler stopped and Baritone operations cancelled.");
    }

    private void startTunneling() {
        if (!isActive()) return;
        tunnelStarted = true;
        ChatUtils.sendPlayerMsg("#tunnel");
        info("Started tunneling with default mode (1x2).");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;
        if (enableStorageESP.get()) {
            StorageESP storageESP = Modules.get().get(StorageESP.class);
            if (storageESP != null && !storageESP.isActive()) {
                storageESP.toggle();
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (!isActive() || !tunnelStarted) return;

        ChunkPos chunkPos = event.chunk().getPos();
        if (processedChunks.contains(chunkPos)) return;

        int storageCount = 0;
        int spawnerCount = 0;
        BlockPos foundStoragePos = null;
        BlockPos foundSpawnerPos = null;

        for (BlockEntity be : event.chunk().getBlockEntities().values()) {
            BlockEntityType<?> type = be.getType();
            if (isStorageBlock(type)) {
                storageCount++;
                if (foundStoragePos == null) foundStoragePos = be.getPos();
            } else if (type == BlockEntityType.MOB_SPAWNER) {
                spawnerCount++;
                if (foundSpawnerPos == null) foundSpawnerPos = be.getPos();
            }
        }

        // Priority: spawner detection first
        if (spawnerCount >= spawnerThreshold.get()) {
            info("Spawner detected at " + foundSpawnerPos + " with " + spawnerCount + " spawners.");
            if (baseFindWebhook.get() && !webhookUrl.get().isEmpty())
                sendSpawnerWebhook(foundSpawnerPos, spawnerCount);
            stopAndDisconnect("§c[Soul] §rSpawners Found");
            return;
        }

        // Then storage detection
        if (storageCount >= storageThreshold.get()) {
            info("Possible base found at " + foundStoragePos + " with " + storageCount + " storage blocks.");
            if (baseFindWebhook.get() && !webhookUrl.get().isEmpty())
                sendBaseWebhook(foundStoragePos, storageCount);
            stopAndDisconnect("§c[Soul] §rBase Found");
            return;
        }

        processedChunks.add(chunkPos);
    }

    private boolean isStorageBlock(BlockEntityType<?> type) {
        return type == BlockEntityType.CHEST || type == BlockEntityType.BARREL ||
            type == BlockEntityType.ENDER_CHEST || type == BlockEntityType.SHULKER_BOX ||
            type == BlockEntityType.HOPPER || type == BlockEntityType.DISPENSER ||
            type == BlockEntityType.DROPPER || type == BlockEntityType.FURNACE ||
            type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER ||
            type == BlockEntityType.BREWING_STAND || type == BlockEntityType.TRAPPED_CHEST;
    }

    private <T extends Module> void toggleModule(Class<T> clazz, boolean enable) {
        T module = Modules.get().get(clazz);
        if (module != null && module.isActive() != enable) module.toggle();
    }

    private void stopAndDisconnect(String disconnectReason) {
        ChatUtils.sendPlayerMsg("#stop");
        ChatUtils.sendPlayerMsg("#cancel");
        toggleModule(StorageESP.class, false);

        if (disconnectOnFind.get()) {
            if (mc.getNetworkHandler() != null) {
                String[] messages = {
                    disconnectReason + ": Logging off - see you later!",
                    disconnectReason + ": Disconnecting for maintenance.",
                    disconnectReason + ": Found what I needed, goodbye!",
                    disconnectReason + ": Server hop time!"
                };
                String msg = messages[random.nextInt(messages.length)];
                mc.getNetworkHandler().getConnection().disconnect(Text.of(msg));
            }
        }

        toggle();
    }

    private void sendBaseWebhook(BlockPos pos, int storageCount) {
        String playerName = mc.getSession().getUsername();
        String content = String.format("\uD83C\uDFE0 **BASE FOUND!**\n" +
            "\uD83D\uDC64 Player: `%s`\n" +
            "\uD83D\uDCCD Coordinates: `%s`\n" +
            "\uD83D\uDCE6 Storage Blocks: `%d`\n" +
            "\uD83C\uDF0D Dimension: `%s`",
            playerName,
            pos.toShortString(),
            storageCount,
            mc.player.getWorld().getRegistryKey().getValue().toString());
        sendWebhook(content);
    }

    private void sendSpawnerWebhook(BlockPos pos, int spawnerCount) {
        String playerName = mc.getSession().getUsername();
        String content = String.format("\uD83C\uDFE0 **SPAWNERS FOUND!**\n" +
            "\uD83D\uDC64 Player: `%s`\n" +
            "\uD83D\uDCCD Coordinates: `%s`\n" +
            "\uD83D\uDCE6 Spawners: `%d`\n" +
            "\uD83C\uDF0D Dimension: `%s`",
            playerName,
            pos.toShortString(),
            spawnerCount,
            mc.player.getWorld().getRegistryKey().getValue().toString());
        sendWebhook(content);
    }

    private void sendWebhook(String content) {
        CompletableFuture.runAsync(() -> {
            try {
                java.net.URL url = new java.net.URL(webhookUrl.get());
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jsonPayload = String.format("{\"content\": \"%s\"}", content);
                try (java.io.OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = connection.getResponseCode();
                if (code != 204 && code != 200) {
                    warning("Webhook failed with response code " + code);
                }
            } catch (Exception e) {
                warning("Webhook error: " + e.getMessage());
            }
        });
    }
}
