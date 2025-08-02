package info.soul.addon.modules;

import info.soul.addon.SoulAddon;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class SpawnerProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL to notify when disconnecting.")
        .defaultValue("webhook.")
        .build()
    );

    private final Setting<Integer> delaySeconds = sgGeneral.add(new IntSetting.Builder()
        .name("recheck-delay-seconds")
        .description("Delay in seconds before rechecking for spawners.")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private boolean triggered = false;
    private boolean sneaking = false;
    private BlockPos currentTarget = null;
    private int recheckDelay = 0;
    private int confirmDelay = 0;
    private boolean waiting = false;
    private String detectedPlayer = null;

    public SpawnerProtect() {
        super(SoulAddon.CATEGORY, "SpawnerProtect", "Crouches, breaks spawners, and sends a Discord embed with player faces.");
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!triggered && event.entity instanceof OtherClientPlayerEntity player) {
            triggered = true;
            detectedPlayer = player.getName().getString();
            ChatUtils.info("Player near spawners: " + detectedPlayer);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!triggered || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (!sneaking) {
            mc.player.setSneaking(true);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.PRESS_SHIFT_KEY));
            sneaking = true;
        }

        if (currentTarget == null) {
            BlockPos origin = mc.player.getBlockPos();
            for (BlockPos pos : BlockPos.iterate(origin.add(-6, -3, -6), origin.add(6, 3, 6))) {
                if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER &&
                    pos.getSquaredDistance(mc.player.getPos()) < 26) {
                    currentTarget = pos.toImmutable();
                    break;
                }
            }

            if (currentTarget == null && !waiting) {
                waiting = true;
                recheckDelay = 0;
                confirmDelay = 0;
            }
        } else {
            mc.interactionManager.updateBlockBreakingProgress(currentTarget, Direction.UP);
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), true);

            if (mc.world.getBlockState(currentTarget).isAir()) {
                currentTarget = null;
                KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
            }
        }

        if (waiting) {
            recheckDelay++;
            if (recheckDelay == delaySeconds.get() * 20) {
                boolean foundAgain = false;
                BlockPos origin = mc.player.getBlockPos();
                for (BlockPos pos : BlockPos.iterate(origin.add(-6, -3, -6), origin.add(6, 3, 6))) {
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                        foundAgain = true;
                        break;
                    }
                }

                if (foundAgain) {
                    waiting = false;
                    return;
                }
            }

            if (recheckDelay > delaySeconds.get() * 20) {
                confirmDelay++;
                if (confirmDelay >= 20) {
                    KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
                    if (!webhookUrl.get().isEmpty()) sendFancyWebhook();
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(
                        Text.literal("§7[§c[Soul]§7] A player was nearby your SPAWNERS")
                    ));
                    toggle();
                }
            }
        }
    }

    private void sendFancyWebhook() {
        try {
            UUID uuid = mc.getSession().getUuidOrNull();
            String stealerName = mc.getSession().getUsername();
            String stealerAvatar = uuid != null ? "https://crafatar.com/avatars/" + uuid + "?overlay" : "";

            String detectedAvatar = detectedPlayer != null ? "https://minotar.net/avatar/" + detectedPlayer + "/100.png" : "";

            String payload = """
            {
              "username": "Spawner Alert",
              "avatar_url": "%s",
              "embeds": [
                {
                  "title": "**[Soul] %s** broke all spawners and left!",
                  "description": "Nearby player: **%s**",
                  "color": 16711680,
                  "thumbnail": {
                    "url": "%s"
                  },
                  "footer": {
                    "text": "AutoSpawnerBreaker"
                  }
                }
              ]
            }
            """.formatted(stealerAvatar, stealerName, detectedPlayer == null ? "Unknown" : detectedPlayer, detectedAvatar);

            HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl.get()).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes());
                os.flush();
            }
            connection.getResponseCode();
            connection.disconnect();
        } catch (Exception ignored) {}
    }

    @Override
    public void onDeactivate() {
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
        if (sneaking && mc.player != null && mc.player.isSneaking()) {
            mc.player.setSneaking(false);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.RELEASE_SHIFT_KEY));
        }
    }
}
