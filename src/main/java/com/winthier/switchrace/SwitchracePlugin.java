package com.winthier.switchrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.json.simple.JSONValue;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class SwitchracePlugin extends JavaPlugin implements Listener {
    private final List<Block> levers = new ArrayList<>();
    private final List<Block> minecarts = new ArrayList<>();
    private final List<Block> spawns = new ArrayList<>();
    private BukkitRunnable task = null;
    private BukkitRunnable countdownTask = null;
    private final Random random = new Random(System.currentTimeMillis());
    private final List<Player> playersInMinecarts = new ArrayList<>();
    private World world;
    private int spawnIndex = 0;
    Objective objective;
    private final Comparator<Block> COMP = new Comparator<Block>() {
            @Override public int compare(Block a, Block b) {
                return Integer.compare(b.getY(), a.getY());
            }
        };

    @Override
    public void onEnable() {
        world = getServer().getWorlds().get(0);
        getServer().getPluginManager().registerEvents(this, this);
        scan();
        Collections.shuffle(spawns);
        setup();
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (task != null) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersInMinecarts.remove(event.getPlayer());
        objective.getScoreboard().resetScores(event.getPlayer().getName());
        if (event.getPlayer().getVehicle() != null) {
            event.getPlayer().getVehicle().remove();
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player)event.getEntity();
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (player == null) return false;
        String farg = args.length >= 1 ? args[0].toLowerCase() : null;
        if (farg == null) {
            return false;
        } else if (farg.equals("restart")) {
            if (task != null) {
                stop();
            }
            setup();
        } else if (farg.equals("flick")) {
            switchLever(levers.get(0));
        }
        return true;
    }

    void scan() {
        levers.clear();
        minecarts.clear();
        spawns.clear();
        Block cb = world.getSpawnLocation().getBlock();
        final int xradius = 80;
        for (int z = cb.getZ() - xradius; z <= cb.getZ() + xradius; z += 1) {
            for (int x = cb.getX() - xradius; x <= cb.getX() + xradius; x += 1) {
                for (int y = 0; y <= cb.getWorld().getHighestBlockYAt(x, z); y += 1) {
                    Block block = cb.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.LEVER) {
                        levers.add(block);
                    } else if (block.getType() == Material.SIGN_POST
                               || block.getType() == Material.WALL_SIGN) {
                        Sign sign = (Sign)block.getState();
                        if (sign.getLine(0).equalsIgnoreCase("[spawn]")) {
                            block.setType(Material.AIR);
                            spawns.add(block);
                        } else if (sign.getLine(0).equalsIgnoreCase("[cart]")) {
                            block.setType(Material.AIR);
                            minecarts.add(block);
                        }
                    }
                }
            }
        }
        getLogger().info("Scanned " + levers.size() + " levers");
        getLogger().info("Scanned " + spawns.size() + " spawns");
        getLogger().info("Scanned " + minecarts.size() + " minecarts");
        Collections.sort(levers, COMP);
    }

    void setup() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        levers.get(0).getRelative(0, -1, 0).setType(Material.REDSTONE_BLOCK);
        for (Player online: getServer().getOnlinePlayers()) {
            if (online.isOp()) {
                online.sendMessage("You're OP!");
                continue;
            }
            online.setGameMode(GameMode.ADVENTURE);
            Location loc = spawns.get(spawnIndex).getLocation().add(0.5, 0.1, 0.5);
            loc.setYaw(online.getLocation().getYaw());
            loc.setPitch(online.getLocation().getPitch());
            online.teleport(loc);
            spawnIndex += 1;
            if (spawnIndex >= spawns.size()) spawnIndex = 0;
        }
        for (Entity entity: world.getEntities()) {
            if (entity instanceof Minecart) entity.remove();
        }
        for (Block block: minecarts) {
            world.spawn(block.getLocation().add(0.5, 0.5, 0.5), Minecart.class);
        }
        countdownTask = new BukkitRunnable() {
                private int count = 10;
                @Override public void run() {
                    int pimc = 0;
                    for (Player online: getServer().getOnlinePlayers()) {
                        Entity vehicle = online.getVehicle();
                        if (vehicle != null && vehicle.getType() == EntityType.MINECART) {
                            pimc += 1;
                        }
                    }
                    if (pimc > 1) { // TODO 1?
                        count -= 1;
                        if (count < 0) {
                            cancel();
                            countdownTask = null;
                            start();
                            return;
                        }
                    } else {
                        count = 60;
                    }
                    for (Player online: getServer().getOnlinePlayers()) {
                        sendActionBar(online, "&aGet in your Minecarts!&r %d", count);
                    }
                }
            };
        countdownTask.runTaskTimer(this, 20, 20);
    }

    void start() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        final int count = 1;
        final int interval = 2;
        task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (levers.isEmpty()) return;
                    for (int i = 0; i < count; ++i) {
                        int index = random.nextInt(levers.size() - 1) + 1;
                        Block block = levers.get(index);
                        switchLever(block);
                    }
                    for (Player riding: new ArrayList<Player>(playersInMinecarts)) {
                        if (!riding.isValid() || riding.getVehicle() == null) {
                            if (riding.isValid()) riding.setGameMode(GameMode.SPECTATOR);
                            playersInMinecarts.remove(riding);
                            objective.getScoreboard().resetScores(riding.getName());
                        }
                    }
                    for (Entity e: world.getEntities()) {
                        if (e instanceof Minecart && e.isEmpty()) e.remove();
                    }
                    if (playersInMinecarts.size() == 1) {
                        for (Player online: getServer().getOnlinePlayers()) {
                            online.sendTitle(ChatColor.GREEN + playersInMinecarts.get(0).getName(), ChatColor.GREEN + "Wins the Game!");
                        }
                        stop();
                        setup();
                    }
                }
            };
        task.runTaskTimer(this, interval, interval);
        Scoreboard scoreboard = getServer().getScoreboardManager().getMainScoreboard();
        scoreboard.clearSlot(DisplaySlot.SIDEBAR);
        if (objective == null) objective = scoreboard.getObjective("Switchcarts");
        if (objective != null) objective.unregister();
        objective = scoreboard.registerNewObjective("Switchcarts", "dummy");
        objective.setDisplayName(ChatColor.GREEN + "Switchcarts");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        playersInMinecarts.clear();
        for (Player online: getServer().getOnlinePlayers()) {
            if (online.isOp()) continue;
            Entity vehicle = online.getVehicle();
            if (vehicle != null && vehicle.getType() == EntityType.MINECART) {
                objective.getScore(online.getName()).setScore(1);
                playersInMinecarts.add(online);
                online.sendTitle("", "" + ChatColor.GREEN + ChatColor.ITALIC + "GO!");
                online.playSound(online.getEyeLocation(), Sound.ENTITY_FIREWORK_LARGE_BLAST, SoundCategory.MASTER, 1.0f, 1.0f);
            } else {
                online.setGameMode(GameMode.SPECTATOR);
            }
        }
        for (Entity e: world.getEntities()) {
            if (e instanceof Minecart && e.isEmpty()) {
                e.remove();
            }
        }
        levers.get(0).getRelative(0, -1, 0).setType(Material.LAPIS_BLOCK);
    }

    void stop() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        levers.get(0).getRelative(0, -1, 0).setType(Material.REDSTONE_BLOCK);
        for (Entity entity: world.getEntities()) {
            if (entity instanceof Minecart) entity.remove();
        }
        playersInMinecarts.clear();
        for (Player player: getServer().getOnlinePlayers()) {
            if (player.isOp()) continue;
            player.setGameMode(GameMode.ADVENTURE);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setHealth(20.0f);
        }
        try {
            task.cancel();
        } catch (IllegalStateException iae) {
            iae.printStackTrace();
        }
        task = null;
    }

    void switchLever(Block block) {
        if (block.getType() != Material.LEVER) return;
        int data = (byte)block.getData();
        boolean isOn = (data & 8) != 0;
        if (isOn) {
            data &= ~8;
        } else {
            data |= 8;
        }
        block.setData((byte)data);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.0, 0.5), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (!event.hasBlock()) return;
        if (event.getClickedBlock().getType() == Material.REDSTONE_LAMP_ON
            || event.getClickedBlock().getType() == Material.REDSTONE_LAMP_OFF) {
            Block lever = event.getClickedBlock().getRelative(0, -1, 0);
            if (lever.getType() != Material.LEVER) return;
            switchLever(lever);
        } else if (event.getClickedBlock().getType() == Material.WOOD_BUTTON
            || event.getClickedBlock().getType() == Material.STONE_BUTTON) {
            if (!playersInMinecarts.contains(event.getPlayer())) return;
            for (Player online: getServer().getOnlinePlayers()) {
                online.sendTitle(ChatColor.GREEN + event.getPlayer().getName(), ChatColor.GREEN + "Wins the Game!");
            }
            stop();
            setup();
        }
    }

    public static String format(String msg, Object... args) {
        if (msg == null) return "";
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) {
            msg = String.format(msg, args);
        }
        return msg;
    }

    public static Object button(ChatColor color, String chat, String insertion, String tooltip, String command) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", format(chat));
        if (color != null) {
            map.put("color", color.name().toLowerCase());
        }
        if (insertion != null) {
            map.put("insertion", insertion);
        }
        if (command != null) {
            Map<String, Object> clickEvent = new HashMap<>();
            map.put("clickEvent", clickEvent);
            clickEvent.put("action", command.endsWith(" ") ? "suggest_command" : "run_command");
            clickEvent.put("value", command);
        }
        if (tooltip != null) {
            Map<String, Object> hoverEvent = new HashMap<>();
            map.put("hoverEvent", hoverEvent);
            hoverEvent.put("action", "show_text");
            hoverEvent.put("value", format(tooltip));
        }
        return map;
    }

    public static void consoleCommand(String cmd, Object... args) {
        if (args.length > 0) cmd = String.format(cmd, args);
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd);
    }

    public static void sendActionBar(Player player, String msg, Object... args) {
        Object o = button(null, format(msg, args), null, null, null);
        consoleCommand("minecraft:title %s actionbar %s", player.getName(), JSONValue.toJSONString(o));
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (spawns.isEmpty()) return;
        event.setSpawnLocation(spawns.get(spawnIndex).getLocation().add(0.5, 0.1, 0.5));
        spawnIndex += 1;
        if (spawnIndex >= spawns.size()) spawnIndex = 0;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }
}
