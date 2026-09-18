package paulito.tutorialmod.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PaulitoMod implements ModInitializer {

    public static PaulitoModConfig config;

    // Cache de la última dimensión vista por cada jugador.
    private static final Map<UUID, String> lastDimensions = new HashMap<>();

    // Cache de la última salud vista por cada jugador.
    private static final Map<UUID, Float> lastHealth = new HashMap<>();

    // Cache de la racha actual de mobs hostiles matados por cada jugador.
    private static final Map<UUID, Integer> hostileKillStreaks = new HashMap<>();

    // Inicializa todos los eventos del mod.
    @Override
    public void onInitialize() {
        config = PaulitoModConfig.load();
        registerKillFeedEvent();
        registerDamageTrackingEvent();
        registerJoinEvent();
        registerRespawnEvent();
        registerDimensionChangeEvent();
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
            });
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

    // Guarda la dimensión actual del jugador en la caché.
    private static void updateDimensionCache(ServerPlayer player) {
        lastDimensions.put(player.getUUID(), getDimensionName(player.level()));
    }

    // Guarda la salud actual del jugador en la caché.
    private static void updateHealthCache(ServerPlayer player) {
        lastHealth.put(player.getUUID(), player.getHealth());
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