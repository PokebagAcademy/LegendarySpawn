package fr.combblemon.legendaryspawner;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Gestion des permissions via LuckPerms (optionnel).
 * Fallback sur le niveau OP si LuckPerms n'est pas présent ou si l'utilisateur n'est pas trouvé.
 */
public class PermissionManager {

    // ---- Nœuds de permission ----

    public static final String FORCE_SPAWN       = "legendaryspawner.command.forcespawn";
    public static final String RELOAD            = "legendaryspawner.command.reload";
    public static final String SET_INTERVAL      = "legendaryspawner.command.setinterval";
    public static final String TIMER             = "legendaryspawner.command.timer";
    public static final String LEGENDARY_LIST    = "legendaryspawner.command.legendary.list";
    public static final String LEGENDARY_INFO    = "legendaryspawner.command.legendary.info";
    public static final String LEGENDARY_MANAGE  = "legendaryspawner.command.legendary.manage";
    public static final String NEXTLEG           = "legendaryspawner.nextleg";
    public static final String NEXTLEG_DETAILS   = "legendaryspawner.nextleg.details";
    public static final String NEXTLEG_ADMIN     = "legendaryspawner.nextleg.admin";
    public static final String LOG_VIEW          = "legendaryspawner.command.log";
    public static final String STATS             = "legendaryspawner.command.stats";
    public static final String INFO              = "legendaryspawner.info";

    // ---- API ----

    /**
     * Vérifie la permission sur un ServerCommandSource.
     * - Console / command block : autorisé si fallbackOpLevel == 0, sinon vérifie l'OP level.
     * - Joueur : vérifie LuckPerms si disponible, sinon vérifie l'OP level.
     *
     * @param src            source de la commande
     * @param permission     nœud LuckPerms à vérifier
     * @param fallbackOpLevel niveau OP minimum si LuckPerms indisponible (0 = tout le monde)
     */
    public static boolean check(ServerCommandSource src, String permission, int fallbackOpLevel) {
        Entity entity = src.getEntity();
        if (entity instanceof ServerPlayerEntity player) {
            return hasPermission(player, permission, fallbackOpLevel);
        }
        // Console, RCON, command blocks
        return src.hasPermissionLevel(fallbackOpLevel == 0 ? 0 : fallbackOpLevel);
    }

    private static boolean hasPermission(ServerPlayerEntity player, String permission, int fallbackOpLevel) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUuid());
            if (user != null) {
                return user.getCachedData()
                        .getPermissionData()
                        .checkPermission(permission)
                        .asBoolean();
            }
        } catch (IllegalStateException ignored) {
            // LuckPerms non chargé sur ce serveur
        }
        return player.hasPermissionLevel(fallbackOpLevel);
    }
}
