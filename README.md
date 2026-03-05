# LegendarySpawner

Mod Fabric **serveur** pour Minecraft **1.21.1** qui automatise l'apparition de Pokémon légendaires via [Cobblemon](https://cobblemon.com/). Hautement configurable : conditions par biome/dimension/météo/heure, poids de spawn, cooldowns, IVs parfaits, shiny, filtre AFK, système de chance accumulée, logs, statistiques, et bien plus.

---

## Prérequis

| Dépendance | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | ≥ 0.15.0 |
| Fabric API | any |
| Cobblemon | compatible 1.21.1 |
| Java | ≥ 21 |
| LuckPerms *(optionnel)* | any |

---

## Installation

1. Placer le `.jar` dans le dossier `mods/` du serveur.
2. Démarrer le serveur une première fois pour générer les fichiers de config.
3. Configurer selon vos besoins (voir section Configuration).
4. Recharger avec `/ls reload` ou redémarrer.

---

## Structure des fichiers de config

```
config/legendaryspawner/
├── config.json          # Configuration globale
├── lang.json            # Messages personnalisables
├── chances.json         # Bonus de chance accumulé (géré automatiquement)
├── stats.json           # Statistiques de spawn (géré automatiquement)
└── legendaries/
    ├── mewtwo.json
    ├── lugia.json
    ├── mew.json
    └── ...              # Un fichier JSON par légendaire
```

---

## config.json — Configuration globale

```json
{
  "intervalMinutes": 30,
  "legendaryLevel": 60,
  "spawnChance": 50.0,
  "chanceIncrement": 5.0,
  "maxChance": 100.0,
  "warnMinutesBefore": 5,
  "preventRepeat": true,
  "recentSpawnMemory": 3,
  "perfectIvCount": 3,
  "shinyChance": 0.5,
  "spawnRadiusMin": 10,
  "spawnRadiusMax": 30,
  "ignoreAfkSeconds": 300,
  "minPlayersToTick": 1,
  "logSpawns": true
}
```

| Champ | Description |
|---|---|
| `intervalMinutes` | Temps entre chaque tentative de spawn (en minutes) |
| `legendaryLevel` | Niveau par défaut des légendaires (si non surchargé par légendaire) |
| `spawnChance` | Chance de base de spawn à chaque intervalle (en %) |
| `chanceIncrement` | Bonus de chance ajouté à chaque tick raté |
| `maxChance` | Plafond de la chance effective (base + bonus accumulé) |
| `warnMinutesBefore` | Annonce X minutes avant le prochain spawn (0 = désactivé) |
| `preventRepeat` | Empêche le même légendaire de spawner consécutivement |
| `recentSpawnMemory` | Nombre de spawns mémorisés pour l'anti-répétition |
| `perfectIvCount` | Nombre d'IVs à 31 parmi les 6 stats (0 à 6) |
| `shinyChance` | Probabilité qu'un légendaire soit shiny (en %, 0 = désactivé) |
| `spawnRadiusMin` | Distance minimale de spawn autour du joueur ciblé (en blocs) |
| `spawnRadiusMax` | Distance maximale de spawn autour du joueur ciblé (en blocs) |
| `ignoreAfkSeconds` | Ignorer les joueurs AFK depuis X secondes (0 = désactivé) |
| `minPlayersToTick` | Nombre minimum de joueurs en ligne pour que le timer tourne (0 = toujours) |
| `logSpawns` | Activer le log des spawns dans `logs/legendaryspawner-spawns.log` |

### Système de chance accumulée

À chaque intervalle sans spawn, le bonus augmente de `chanceIncrement`. Quand un légendaire spawne, le bonus est remis à 0. La chance effective est : `min(spawnChance + bonus, maxChance)`.

---

## legendaries/\<nom\>.json — Config par légendaire

```json
{
  "enabled": true,
  "displayName": "",
  "weight": 8,
  "minLevel": -1,
  "maxLevel": -1,
  "cooldownMinutes": 0,
  "biomes": [],
  "dimension": "any",
  "timeOfDay": "any",
  "weather": "any"
}
```

| Champ | Description |
|---|---|
| `enabled` | Active ou désactive ce légendaire |
| `displayName` | Nom affiché dans les messages (vide = nom formaté automatiquement) |
| `weight` | Poids dans la sélection pondérée — plus élevé = plus fréquent |
| `minLevel` | Niveau minimum (-1 = utiliser le niveau global) |
| `maxLevel` | Niveau maximum (-1 = utiliser le niveau global) |
| `cooldownMinutes` | Cooldown après spawn de ce légendaire en minutes (0 = aucun) |
| `biomes` | Liste de biomes requis (ex: `["minecraft:forest"]`). Vide = partout |
| `dimension` | Dimension requise : `any`, `overworld`, `nether`, `end` ou ID complet |
| `timeOfDay` | Heure requise : `any`, `day`, `night` |
| `weather` | Météo requise : `any`, `clear`, `rain`, `thunder` |

**Exemple — Lugia restreint :**
```json
{
  "enabled": true,
  "weight": 8,
  "minLevel": 70,
  "maxLevel": 85,
  "cooldownMinutes": 60,
  "biomes": ["minecraft:ocean", "minecraft:deep_ocean", "minecraft:frozen_ocean"],
  "dimension": "overworld",
  "timeOfDay": "night",
  "weather": "rain"
}
```

---

## Commandes

Toutes les commandes utilisent `/legendaryspawner` ou l'alias **`/ls`**.

### Commandes générales

| Commande | Description |
|---|---|
| `/ls help` | Affiche l'aide |
| `/ls reload` | Recharge tous les fichiers de config sans redémarrer |
| `/ls timer` | Affiche le temps restant avant le prochain spawn |
| `/ls setinterval <minutes>` | Modifie l'intervalle de spawn à la volée |
| `/ls stats` | Affiche les statistiques globales de spawn |

### Force spawn

| Commande | Description |
|---|---|
| `/ls forcespawn` | Force un spawn aléatoire (ignore les conditions et la chance) |
| `/ls forcespawn <joueur>` | Force un spawn autour d'un joueur spécifique |
| `/ls forcespawn pokemon <nom>` | Force le spawn d'un légendaire précis |
| `/ls forcespawn pokemon <nom> <joueur>` | Force le spawn d'un légendaire précis autour d'un joueur |

### Gestion des légendaires

| Commande | Description |
|---|---|
| `/ls legendary list [page]` | Liste tous les légendaires et leur config |
| `/ls legendary info <pokemon>` | Détails complets d'un légendaire |
| `/ls legendary enable <pokemon>` | Active un légendaire |
| `/ls legendary disable <pokemon>` | Désactive un légendaire |
| `/ls legendary set <pokemon> <param> <valeur>` | Modifie un paramètre |
| `/ls legendary biome add <pokemon> <biome>` | Ajoute un biome requis |
| `/ls legendary biome remove <pokemon> <biome>` | Retire un biome |
| `/ls legendary biome clear <pokemon>` | Supprime tous les biomes (= partout) |

**Paramètres disponibles pour `set` :**

| Param | Valeurs | Exemple |
|---|---|---|
| `weight` | entier ≥ 1 | `set mewtwo weight 5` |
| `minlevel` | -1 à 100 | `set mewtwo minlevel 70` |
| `maxlevel` | -1 à 100 | `set mewtwo maxlevel 100` |
| `cooldown` | 0+ (minutes) | `set mewtwo cooldown 60` |
| `timeofday` | `any` `day` `night` | `set mewtwo timeofday night` |
| `weather` | `any` `clear` `rain` `thunder` | `set mewtwo weather rain` |
| `dimension` | `any` `overworld` `nether` `end` | `set mewtwo dimension overworld` |
| `displayname` | texte libre | `set mewtwo displayname Mewtwo Ombre` |

### Commandes de masse

| Commande | Description |
|---|---|
| `/ls legendary all enable` | Active tous les légendaires |
| `/ls legendary all disable` | Désactive tous les légendaires |
| `/ls legendary all reset` | Remet les conditions (biome/dim/météo/heure) à `any` pour tous |
| `/ls legendary all biome <biome>` | Ajoute un biome à tous les légendaires |
| `/ls legendary all biome clear` | Retire les restrictions de biome de tous |

### Logs

| Commande | Description |
|---|---|
| `/ls log [lignes]` | Affiche les derniers spawns (défaut : 10, max : 100) |
| `/ls log clear` | Efface le fichier de log |

### Informations joueur

| Commande | Description |
|---|---|
| `/nextleg` | Affiche le timer, la chance de spawn actuelle, et les légendaires éligibles |

### Informations admin

| Commande | Description |
|---|---|
| `/nextlegadmin global [page]` | Distribution pondérée de tous les légendaires activés avec % de spawn |
| `/nextlegadmin user [joueur]` | Pool éligible pour un joueur avec % réels selon ses conditions |

---

## Permissions (LuckPerms)

Si LuckPerms n'est pas installé, le fallback est le niveau OP (level 2 pour les commandes admin, 0 pour les commandes joueur).

| Nœud | Description | Fallback OP |
|---|---|---|
| `legendaryspawner.command.forcespawn` | `/ls forcespawn` | 2 |
| `legendaryspawner.command.reload` | `/ls reload` | 2 |
| `legendaryspawner.command.setinterval` | `/ls setinterval` | 2 |
| `legendaryspawner.command.timer` | `/ls timer` | 2 |
| `legendaryspawner.command.stats` | `/ls stats` | 2 |
| `legendaryspawner.command.legendary.list` | `/ls legendary list` | 2 |
| `legendaryspawner.command.legendary.info` | `/ls legendary info` | 2 |
| `legendaryspawner.command.legendary.manage` | Toutes les modifs de légendaires | 2 |
| `legendaryspawner.command.log` | `/ls log` | 2 |
| `legendaryspawner.nextleg` | `/nextleg` | 0 (tous) |
| `legendaryspawner.nextleg.details` | Vue admin de `/nextleg` (liste par joueur) | 2 |
| `legendaryspawner.nextleg.admin` | `/nextlegadmin` | 2 |

---

## Fonctionnement du spawn

1. **Timer** : toutes les `intervalMinutes` minutes, une tentative de spawn est effectuée.
2. **Chance** : un roll aléatoire est comparé à la chance effective (base + bonus accumulé). En cas d'échec, le bonus augmente de `chanceIncrement`.
3. **Éligibilité** : pour chaque légendaire activé, le mod vérifie les conditions (biome, dimension, météo, heure, cooldown) contre chaque joueur en ligne non-AFK.
4. **Sélection** : un légendaire éligible est choisi par sélection pondérée (weight). L'anti-répétition exclut les derniers légendaires spawnés.
5. **Spawn** : le légendaire apparaît au sol entre `spawnRadiusMin` et `spawnRadiusMax` blocs autour du joueur ciblé, avec `perfectIvCount` IVs à 31 choisis aléatoirement, et éventuellement en shiny.
6. **Broadcast** : un message est envoyé à tous les joueurs, un son est joué.

---

## lang.json — Messages personnalisables

Le fichier `lang.json` permet de personnaliser tous les messages du mod. Les placeholders disponibles sont indiqués entre `{accolades}`.

Principaux messages :

| Clé | Placeholders |
|---|---|
| `spawn.broadcast` | `{pokemon}`, `{player}` |
| `spawn.broadcast_shiny` | `{pokemon}`, `{player}` |
| `spawn.notify_player` | `{pokemon}` |
| `spawn.warn` | `{minutes}` |
| `nextleg.header` | `{timer}`, `{chance}`, `{bonus}` |

---

## Noms des Pokémon (Cobblemon)

Les noms des fichiers dans `legendaries/` doivent correspondre exactement aux identifiants Cobblemon :

- Espèces normales : `mewtwo`, `lugia`, `rayquaza`, `mew`…
- Formes spéciales : `giratina_altered`, `giratina_origin`, `meloetta`
- Paradoxes Gen 9 : `wochien`, `chienpao`, `tinglu`, `chiyu`
- Cas particulier : `typenull` (pas de underscore)

---

## Fichier de log

Les spawns sont enregistrés dans `logs/legendaryspawner-spawns.log` (si `logSpawns: true`) :

```
[2026-03-06 14:32:11] Mewtwo (niv.85) spawné près de Steve à (1204, 68, -340) dans minecraft:overworld
```

---

## Licence

Ce mod est développé pour le serveur **CombblemonServer**. Usage interne.
