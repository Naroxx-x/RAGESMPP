package fr.ragesmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class RageSMP extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Integer> rage = new HashMap<>();
    private final Map<UUID, UUID> comboTarget = new HashMap<>();
    private final Map<UUID, Integer> comboHits = new HashMap<>();
    private final Set<UUID> criticalMode = new HashSet<>();
    private final Set<UUID> hitThisTick = new HashSet<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    private final Map<UUID, Long> lastSwingTime = new HashMap<>();

    private NamespacedKey rageKey;
    private NamespacedKey rageShardKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rageKey = new NamespacedKey(this, "rage");
        rageShardKey = new NamespacedKey(this, "rage_shard_amount");
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("rage")).setExecutor(this);
        Objects.requireNonNull(getCommand("setrage")).setExecutor(this);
        Objects.requireNonNull(getCommand("rage")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("setrage")).setTabCompleter(this);

        // Vérifie régulièrement les effets passifs et sauvegarde la Rage dans le PDC.
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    applyPassiveEffects(p);
                    saveRage(p);
                }
            }
        }.runTaskTimer(this, 1L, 20L);

        getLogger().info("RageSMP activé pour Paper 1.21.11.");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) saveRage(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        loadRage(p);
        applyPassiveEffects(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        saveRage(e.getPlayer());
    }

    private int getRage(Player p) {
        return Math.max(0, Math.min(getConfig().getInt("max-rage", 5), rage.getOrDefault(p.getUniqueId(), 0)));
    }

    private void setRage(Player p, int value) {
        int max = getConfig().getInt("max-rage", 5);
        int newValue = Math.max(0, Math.min(max, value));
        rage.put(p.getUniqueId(), newValue);
        saveRage(p);
        applyPassiveEffects(p);
    }

    private void addRage(Player p, int amount) {
        addRage(p, amount, null);
    }

    private void addRage(Player p, int amount, String reason) {
        int old = getRage(p);
        int next = old + amount;
        setRage(p, next);
        int delta = getRage(p) - old;
        if (delta != 0) {
            broadcastRageChange(p, delta, reason);
        }
    }

    // Affiche le gain/perte de Rage en gras violet dans le chat, en plus de l'action bar.
    private void broadcastRageChange(Player p, int delta, String reason) {
        int max = getConfig().getInt("max-rage", 5);
        p.sendActionBar("§5RAGE §f» §d" + getRage(p) + "§7/§d" + max);

        String sign = delta > 0 ? "+" : "-";
        StringBuilder msg = new StringBuilder("§5§l" + sign + Math.abs(delta) + " RAGE");
        msg.append(" §r§5§l(§d").append(getRage(p)).append("§5§l/§d").append(max).append("§5§l)");
        if (reason != null && !reason.isEmpty()) {
            msg.append(" §r§7- ").append(reason);
        }
        p.sendMessage(msg.toString());
    }

    private void loadRage(Player p) {
        Integer stored = p.getPersistentDataContainer().get(rageKey, PersistentDataType.INTEGER);
        rage.put(p.getUniqueId(), stored == null ? 0 : Math.max(0, Math.min(5, stored)));
    }

    private void saveRage(Player p) {
        p.getPersistentDataContainer().set(rageKey, PersistentDataType.INTEGER, getRage(p));
    }

    private void applyPassiveEffects(Player p) {
        int r = getRage(p);

        // Rage 2 = Speed I
        if (r >= 2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, getConfig().getInt("speed-amplifier", 0), false, false, true));
        } else {
            p.removePotionEffect(PotionEffectType.SPEED);
        }

        // Coeurs supplémentaires : Rage 3 = +2, Rage 4 = +4, Rage 5 = +6.
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double extraHearts = getConfig().getDouble("extra-hearts." + r, 0.0);
            double target = 20.0 + extraHearts * 2.0;
            if (Math.abs(maxHealth.getBaseValue() - target) > 0.01) {
                double oldMax = maxHealth.getBaseValue();
                maxHealth.setBaseValue(target);
                if (p.getHealth() > target) p.setHealth(target);
                // N'augmente pas artificiellement les PV actuels : seulement le maximum.
            }
        }
    }

    private boolean isSword(ItemStack item) {
        if (item == null) return false;
        return switch (item.getType()) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;

        int r = getRage(attacker);
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        boolean sword = isSword(weapon);

        // Toute attaque réussie marque que le swing actuel a touché.
        hitThisTick.add(attacker.getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> hitThisTick.remove(attacker.getUniqueId()));

        // Si la cible change, le combo repart à 1.
        UUID targetId = target.getUniqueId();
        UUID oldTarget = comboTarget.get(attacker.getUniqueId());
        if (!targetId.equals(oldTarget)) {
            comboTarget.put(attacker.getUniqueId(), targetId);
            comboHits.put(attacker.getUniqueId(), 0);
            criticalMode.remove(attacker.getUniqueId());
        }

        int hits = comboHits.getOrDefault(attacker.getUniqueId(), 0) + 1;
        comboHits.put(attacker.getUniqueId(), hits);
        lastHitTime.put(attacker.getUniqueId(), System.currentTimeMillis());

        // Rage 1 : après 3 coups consécutifs à l'épée, les coups sont "critiques"
        // jusqu'à ce qu'un swing rate.
        if (r >= 1 && sword && !criticalMode.contains(attacker.getUniqueId())
                && hits >= getConfig().getInt("blood-combo-hits", 3)) {
            criticalMode.add(attacker.getUniqueId());
            attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 10, .3, .3, .3, .05);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
        }

        double multiplier = 1.0 + getConfig().getDouble("damage-bonus." + r, 0.0) / 100.0;
        if (r >= 1 && sword && criticalMode.contains(attacker.getUniqueId())) {
            multiplier *= getConfig().getDouble("critical-multiplier", 1.5);
        }

        // Rage 4 : Armor Break après 5 coups consécutifs sur la même cible.
        if (r >= 4 && sword && hits >= getConfig().getInt("armor-break-hits", 5)) {
            double armorMultiplier = 1.0 - getConfig().getDouble("armor-break-percent", 25.0) / 100.0;
            // Vanilla expose déjà une réduction d'armure. On simule un Armor Break
            // en ajoutant une portion des dégâts finaux avant l'événement.
            multiplier *= (1.0 / Math.max(0.1, armorMultiplier));
            comboHits.put(attacker.getUniqueId(), 0);
            attacker.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 15, .4, .4, .4, .05);
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
        }

        // Rage 5 : l'épée désactive le bouclier.
        if (r >= 5 && sword && target instanceof Player victim) {
            if (victim.isBlocking()) {
                victim.setCooldown(Material.SHIELD, getConfig().getInt("shield-disable-seconds", 3) * 20);
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, .3, .3, .3, .05);
            }
        }

        e.setDamage(e.getDamage() * multiplier);
    }

    // Un swing qui ne touche personne casse Blood Combo.
    @EventHandler
    public void onAnimation(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (getRage(p) < 1) return;
        if (!isSword(p.getInventory().getItemInMainHand())) return;

        UUID id = p.getUniqueId();
        lastSwingTime.put(id, System.currentTimeMillis());

        Bukkit.getScheduler().runTask(this, () -> {
            if (!hitThisTick.contains(id) && criticalMode.contains(id)) {
                criticalMode.remove(id);
                comboHits.put(id, 0);
                comboTarget.remove(id);
            }
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        int loss = getConfig().getInt("death-rage-loss", 1);
        addRage(dead, -loss, "mort");

        Player killer = dead.getKiller();
        if (killer != null && killer != dead) {
            int max = getConfig().getInt("max-rage", 5);
            boolean overkillDrop = getConfig().getBoolean("overkill-drop-enabled", true);

            if (overkillDrop && getRage(killer) >= max) {
                // Le tueur est déjà au maximum : la Rage qu'il aurait gagnée
                // tombe au sol sous forme d'éclat que n'importe qui peut ramasser.
                int shardAmount = Math.max(1, getConfig().getInt("overkill-drop-rage-amount", 1));
                dead.getWorld().dropItemNaturally(dead.getLocation(), createRageShardItem(shardAmount));
                dead.getWorld().playSound(dead.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                dead.getWorld().spawnParticle(Particle.WITCH, dead.getLocation().add(0, 1, 0), 20, .3, .5, .3, .02);
                killer.sendMessage("§5§lRAGE §r§7Tu es déjà au maximum : un §d§léclat de Rage§r§7 tombe au sol !");
            } else {
                addRage(killer, getConfig().getInt("kill-rage", 1), "kill");
            }
        }

        comboHits.remove(dead.getUniqueId());
        comboTarget.remove(dead.getUniqueId());
        criticalMode.remove(dead.getUniqueId());
    }

    private ItemStack createRageShardItem(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§lÉclat de Rage");
            meta.setLore(List.of(
                    "§7Clic droit pour gagner",
                    "§7§d+" + amount + " Rage§7 instantanément."
            ));
            meta.getPersistentDataContainer().set(rageShardKey, PersistentDataType.INTEGER, amount);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Donne totalAmount points de Rage sous forme d'éclats (1 Rage par éclat),
    // répartis en stacks de max 64 ; le surplus tombe au sol si l'inventaire est plein.
    private void giveShards(Player p, int totalAmount) {
        int remaining = totalAmount;
        int maxStack = Material.AMETHYST_SHARD.getMaxStackSize();
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack shard = createRageShardItem(1);
            shard.setAmount(stackSize);
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(shard);
            for (ItemStack leftover : overflow.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            remaining -= stackSize;
        }
    }

    // Clic droit sur un éclat de Rage en main : consomme 1 éclat et donne de la Rage.
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack stack = p.getInventory().getItemInMainHand();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(rageShardKey, PersistentDataType.INTEGER)) return;

        e.setCancelled(true);
        int amount = meta.getPersistentDataContainer().getOrDefault(rageShardKey, PersistentDataType.INTEGER, 1);

        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }

        addRage(p, amount, "éclat utilisé");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 15, .3, .5, .3, .02);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rage")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("withdraw")) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cSeul un joueur peut retirer de la Rage.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c/rage withdraw <montant>");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cLe montant doit être un nombre.");
                    return true;
                }
                if (amount <= 0) {
                    sender.sendMessage("§cLe montant doit être supérieur à 0.");
                    return true;
                }
                if (getRage(p) < amount) {
                    sender.sendMessage("§cTu n'as que §d" + getRage(p) + " §cRage.");
                    return true;
                }
                addRage(p, -amount, "retrait");
                giveShards(p, amount);
                p.sendMessage("§5§lRAGE §r§7Retiré sous forme de §d" + amount + " éclat(s)§7 dans ton inventaire.");
                return true;
            }

            if (args.length == 0) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Utilise /rage <joueur> depuis la console.");
                    return true;
                }
                sender.sendMessage("§5§lRAGE §f» §d" + getRage(p) + "§7/§d" + getConfig().getInt("max-rage", 5));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cJoueur introuvable.");
                return true;
            }
            sender.sendMessage("§5§lRAGE §f» §d" + target.getName() + " §7a §d" + getRage(target) + "§7/§d" + getConfig().getInt("max-rage", 5));
            return true;
        }

        if (command.getName().equalsIgnoreCase("setrage")) {
            if (!sender.hasPermission("ragesmp.admin")) {
                sender.sendMessage("§cPas de permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§c/setrage <joueur> <0-5>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cJoueur introuvable.");
                return true;
            }
            try {
                int oldValue = getRage(target);
                int value = Integer.parseInt(args[1]);
                setRage(target, value);
                int delta = getRage(target) - oldValue;
                if (delta != 0) {
                    broadcastRageChange(target, delta, "admin");
                }
                sender.sendMessage("§aRage de " + target.getName() + " définie à " + getRage(target) + ".");
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cLa Rage doit être un nombre.");
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("setrage")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("setrage")) {
            return List.of("0", "1", "2", "3", "4", "5");
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("rage")) {
            List<String> options = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            options.add("withdraw");
            return options;
        }
        if (args.length == 2 && command.getName().equalsIgnoreCase("rage") && args[0].equalsIgnoreCase("withdraw")) {
            return List.of("1", "2", "3", "4", "5");
        }
        return List.of();
    }
}
