package paulito.tutorialmod.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PaulitoMod implements ModInitializer {

    public static PaulitoModConfig config;

    // Cache de la racha actual de mobs hostiles matados por cada jugador.
    private static final Map<UUID, Integer> hostileKillStreaks = new HashMap<>();

    // Cache de la última posición del jugador para refrescar coordenadas en movimiento.
    private static final Map<UUID, BlockPos> lastTabPositions = new HashMap<>();

    // Cache de la última salud del jugador para refrescar el porcentaje de vida al dañar o curar.
    private static final Map<UUID, Float> lastTabHealth = new HashMap<>();

    // Inicializa todos los eventos del mod.
    @Override
    public void onInitialize() {
        config = PaulitoModConfig.load();
        registerKillFeedEvent();
        registerDamageTrackingEvent();
        registerJoinEvent();
        registerRespawnEvent();
        registerDimensionChangeEvent();
        registerTabListRefresh();
    }

    // Registra el evento de muerte para mostrar el kill feed.
    private void registerKillFeedEvent() {
        // Kill feed: muestra quién mató a qué mob, con arma y dimensión.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!config.enabled || !config.killFeedEnabled || !(damageSource.getEntity() instanceof ServerPlayer)) {
                return;
            }

            ServerPlayer player = (ServerPlayer) damageSource.getEntity();
            if (!(entity instanceof LivingEntity)) {
                return;
            }

            LivingEntity livingEntity = (LivingEntity) entity;
            if (livingEntity.getType().getCategory() != MobCategory.MONSTER) {
                return;
            }

            String playerName = player.getGameProfile().getName();
            String mobName = getMobName(livingEntity);
            String weaponName = getWeaponName(player);
            String dimensionName = getDimensionName(player.level());

            int currentStreak = hostileKillStreaks.getOrDefault(player.getUUID(), 0) + 1;
            hostileKillStreaks.put(player.getUUID(), currentStreak);
            handleKillStreakReward(player, currentStreak);

            MutableComponent message = Component.literal("")
                    .append(Component.literal(playerName).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                    .append(Component.literal(" eliminó a ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(mobName).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

            if (weaponName == null || weaponName.isEmpty()) {
                message = message
                        .append(Component.literal(" con ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("la mano").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else {
                message = message
                        .append(Component.literal(" con ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(weaponName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }

            message = message
                    .append(Component.literal(" en ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(dimensionName).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

            String survivalInfo = getSurvivalInfo(player);
            if (survivalInfo != null) {
                message = message
                        .append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(survivalInfo).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }

            player.server.getPlayerList().broadcastSystemMessage(message, false);
        });
    }

    // Actualiza la caché cuando el jugador recibe daño.
    private void registerDamageTrackingEvent() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            if (config.enabled && config.damageTrackingEnabled && entity instanceof ServerPlayer player) {
                player.server.execute(() -> {
                    if (config.dimensionTrackingEnabled) {
                        updateDimensionCache(player);
                    }
                    if (config.healthTrackingEnabled) {
                        updateHealthCache(player);
                    }
                });
            }
            return true;
        });
    }

    // Guarda la dimensión y salud al entrar al servidor.
    private void registerJoinEvent() {
        // Cuando entra al servidor, cacheamos su dimensión y salud iniciales.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                if (config.enabled && config.joinStateRefreshEnabled) {
                    resetKillStreak(handler.getPlayer());
                    if (config.dimensionTrackingEnabled) {
                        updateDimensionCache(handler.getPlayer());
                    }
                    if (config.healthTrackingEnabled) {
                        updateHealthCache(handler.getPlayer());
                    }
                    refreshTabListForPlayer(handler.getPlayer());
                }
            });
        });
    }

    // Actualiza la caché cuando el jugador reaparece.
    private void registerRespawnEvent() {
        // Cuando respawnea, volvemos a registrar su dimensión actual.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            newPlayer.server.execute(() -> {
                if (config.enabled && config.respawnStateRefreshEnabled) {
                    resetKillStreak(newPlayer);
                    if (config.dimensionTrackingEnabled) {
                        updateDimensionCache(newPlayer);
                    }
                    if (config.healthTrackingEnabled) {
                        updateHealthCache(newPlayer);
                    }
                    refreshTabListForPlayer(newPlayer);
                }
            });
        });
    }

    // Refresca la caché al cambiar de dimensión o teletransportarse.
	private void registerDimensionChangeEvent() {
        // Cuando cambia de dimensión o se teletransporta, refrescamos la caché.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            player.server.execute(() -> {
                if (!config.enabled) {
                    return;
                }
                if (config.resetOnDimensionChange && origin.dimension() != destination.dimension()) {
                    resetKillStreak(player);
                }
                if (config.dimensionTrackingEnabled) {
                    updateDimensionCache(player);
                }
                if (config.healthTrackingEnabled) {
                    updateHealthCache(player);
                }
                refreshTabListForPlayer(player);
            });
        });
    }

    private void registerTabListRefresh() {
        if (!config.enabled || !config.tabListEnabled) {
            return;
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> {
                lastTabPositions.put(handler.getPlayer().getUUID(), handler.getPlayer().blockPosition());
                lastTabHealth.put(handler.getPlayer().getUUID(), handler.getPlayer().getHealth());
                refreshTabListForPlayer(handler.getPlayer());
            });
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BlockPos currentPos = player.blockPosition();
                BlockPos lastPos = lastTabPositions.get(player.getUUID());
                float currentHealth = player.getHealth();
                float lastHealth = lastTabHealth.getOrDefault(player.getUUID(), currentHealth);

                boolean moved = lastPos == null || lastPos.getX() != currentPos.getX() || lastPos.getY() != currentPos.getY() || lastPos.getZ() != currentPos.getZ();
                boolean healthChanged = Math.abs(lastHealth - currentHealth) > 0.01f;

                if (moved || healthChanged) {
                    lastTabPositions.put(player.getUUID(), currentPos);
                    lastTabHealth.put(player.getUUID(), currentHealth);
                    refreshTabListForPlayer(player);
                }
            }
        });
    }

    // Reinicia la racha de hostiles de un jugador.
    private static void resetKillStreak(ServerPlayer player) {
        hostileKillStreaks.put(player.getUUID(), 0);
    }

    // Reparte recompensas según la racha de hostiles eliminados.
    private static void handleKillStreakReward(ServerPlayer player, int streak) {
        if (!config.enabled || !config.streaksEnabled) {
            return;
        }

        if (config.grandRewardEnabled && streak % config.grandStreakThreshold == 0) {
            player.giveExperiencePoints(config.grandRewardXp);
            player.displayClientMessage(
                    Component.literal("¡Racha épica! Has matado " + streak + " hostiles. +" + config.grandRewardXp + " XP")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    false);
            return;
        }

        if (config.normalRewardEnabled && streak % config.normalStreakThreshold == 0) {
            player.giveExperiencePoints(config.normalRewardXp);
            player.displayClientMessage(
                    Component.literal("¡Racha activa! " + streak + " hostiles eliminados. +" + config.normalRewardXp + " XP")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    false);
        }
    }

    // Actualiza la caché de dimensión para el sistema de seguimiento.
    private static void updateDimensionCache(ServerPlayer player) {
        // La caché ya no se usa en la UI actual, pero se conserva para tracking del sistema.
    }

    // Actualiza la caché de salud para el sistema de seguimiento.
    private static void updateHealthCache(ServerPlayer player) {
        // La caché ya no se usa en la UI actual, pero se conserva para tracking del sistema.
    }

    private static void refreshTabListForPlayer(ServerPlayer player) {
        if (!config.enabled || !config.tabListEnabled) {
            return;
        }

        MutableComponent header = Component.empty();
        MutableComponent footer = buildPlayerStatusFooter(player);

        player.connection.send(new ClientboundTabListPacket(header, footer));
    }

    private static MutableComponent buildPlayerStatusFooter(ServerPlayer currentPlayer) {
        MutableComponent footer = Component.literal("");
        java.util.List<ServerPlayer> players = currentPlayer.server.getPlayerList().getPlayers();

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player == null) {
                continue;
            }

            String dimension = getShortDimensionName(player.level());
            BlockPos pos = player.blockPosition();
            float health = player.getHealth();
            float maxHealth = player.getMaxHealth();
            int percent = Math.max(0, Math.min(100, Math.round((health / maxHealth) * 100f)));
            String healthBar = buildPremiumHealthBar(percent);

            footer.append(Component.literal("♥ ").withStyle(ChatFormatting.RED));
            footer.append(Component.literal(healthBar).withStyle(ChatFormatting.DARK_RED));
            footer.append(Component.literal(" ").withStyle(ChatFormatting.WHITE));
            footer.append(Component.literal(String.valueOf(percent)).withStyle(ChatFormatting.GOLD));
            footer.append(Component.literal("% | ").withStyle(ChatFormatting.WHITE));
            footer.append(Component.literal(dimension).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            footer.append(Component.literal(" | ").withStyle(ChatFormatting.WHITE));
            footer.append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.GREEN));
            footer.append(Component.literal(" | ").withStyle(ChatFormatting.WHITE));
            footer.append(Component.literal(getStreakProgressLabel(player)).withStyle(ChatFormatting.GRAY));

            if (i < players.size() - 1) {
                footer.append(Component.literal("\n\n"));
            }
        }
        return footer;
    }

    private static String getStreakProgressLabel(ServerPlayer player) {
        int currentStreak = hostileKillStreaks.getOrDefault(player.getUUID(), 0);
        int target = config.normalStreakThreshold;
        int grandThresholdWindow = config.normalStreakThreshold * 2;

        if (config.grandStreakThreshold > currentStreak && config.grandStreakThreshold - currentStreak < grandThresholdWindow) {
            target = config.grandStreakThreshold;
        }
        
        // Return de la racha actual y el objetivo de la racha + cuanta XP se da por alcanzar la racha.
        return currentStreak + "/" + target + " mobs (+" + (target == config.grandStreakThreshold ? config.grandRewardXp : config.normalRewardXp) + " XP)";
    }

    // Devuelve el nombre del mob.
    private static String getMobName(LivingEntity entity) {
        return entity.getName().getString();
    }

    // Devuelve el nombre del arma principal del jugador.
    private static String getWeaponName(ServerPlayer player) {
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            return null;
        }
        return item.getDisplayName().getString();
    }

    // Convierte la dimensión en un nombre legible.
    private static String getDimensionName(Level level) {
        if (level.dimension() == Level.OVERWORLD) {
            return "el Overworld";
        }
        if (level.dimension() == Level.NETHER) {
            return "el Nether";
        }
        if (level.dimension() == Level.END) {
            return "el End";
        }

        String path = level.dimension().location().getPath();
        String readableName = path.replace('_', ' ');
        return "la dimensión " + readableName;
    }

    // Devuelve una versión compacta del nombre de la dimensión para el tab list.
    private static String getShortDimensionName(Level level) {
        if (level.dimension() == Level.OVERWORLD) {
            return "Overworld";
        }
        if (level.dimension() == Level.NETHER) {
            return "Nether";
        }
        if (level.dimension() == Level.END) {
            return "End";
        }

        String path = level.dimension().location().getPath();
        return path.replace('_', ' ');
    }

    private static String buildPremiumHealthBar(int percent) {
        int totalBlocks = 10;
        int roundedPercent = Math.max(0, Math.min(100, percent));
        int filledBlocks = Math.round(roundedPercent / 10f);
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < totalBlocks; i++) {
            if (i < filledBlocks) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        return bar.toString();
    }

    // Devuelve la información si el jugador está muy bajo de vida.
    private static String getSurvivalInfo(ServerPlayer player) {
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        double percent = (currentHealth / maxHealth) * 100.0;

        if (percent > 20.0) {
            return null;
        }

        return "quedó a " + Math.round(percent) + "% de vida ("
                + Math.round(currentHealth) + "/" + Math.round(maxHealth) + " PV)";
    }
}