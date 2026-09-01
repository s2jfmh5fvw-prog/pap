package dev.pap;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class PapPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final String HOME_TITLE = ChatColor.DARK_AQUA + "Deine Homes";
    private static final String TEAM_TITLE = ChatColor.DARK_PURPLE + "Dein Team";
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = YamlConfiguration.loadConfiguration(getDataFile());
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("home").setExecutor(this);
        getCommand("home").setTabCompleter(this);
        getCommand("team").setExecutor(this);
        getCommand("team").setTabCompleter(this);
        getCommand("purge").setExecutor(this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private java.io.File getDataFile() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        return new java.io.File(getDataFolder(), "data.yml");
    }

    private void saveData() {
        try {
            data.save(getDataFile());
        } catch (java.io.IOException e) {
            getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("purge")) {
            sendPhase(player);
            return true;
        }
        if (command.getName().equalsIgnoreCase("home")) return homeCommand(player, args);
        return teamCommand(player, args);
    }

    private boolean homeCommand(Player player, String[] args) {
        if (args.length == 0) {
            openHomes(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("set") && args.length == 2) {
            setHome(player, args[1]);
        } else if ((action.equals("teleport") || action.equals("tp")) && args.length == 2) {
            teleportHome(player, args[1]);
        } else if ((action.equals("delete") || action.equals("remove")) && args.length == 2) {
            String path = homePath(player, args[1]);
            if (data.contains(path)) {
                data.set(path, null);
                saveData();
                message(player, "&aHome &f" + args[1] + " &agelöscht.");
            } else message(player, "&cDieses Home existiert nicht.");
        } else {
            message(player, "&e/home [set|teleport|delete] <name>");
        }
        return true;
    }

    private void setHome(Player player, String name) {
        if (!validName(name)) {
            message(player, "&cDer Name darf 1-16 Zeichen, Zahlen, _ oder - enthalten.");
            return;
        }
        String path = homePath(player, name);
        if (!data.contains(path) && homeNames(player).size() >= homeLimit(player)) {
            message(player, "&cDein Home-Limit (" + homeLimit(player) + ") ist erreicht.");
            return;
        }
        if (!charge(player, "home-set-cost")) return;
        data.set(path, player.getLocation());
        saveData();
        message(player, "&aHome &f" + name + " &agesetzt.");
    }

    private void teleportHome(Player player, String name) {
        Location location = data.getLocation(homePath(player, name));
        if (location == null) {
            message(player, "&cDieses Home existiert nicht.");
            return;
        }
        if (!charge(player, "home-teleport-cost")) return;
        player.teleportAsync(location).thenAccept(success -> {
            if (!success) refund(player, "home-teleport-cost");
        });
    }

    private boolean teamCommand(Player player, String[] args) {
        if (args.length == 0) {
            openTeam(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        String team = teamOf(player.getUniqueId());
        if (action.equals("create") && args.length == 3) createTeam(player, args[1], args[2]);
        else if (action.equals("invite") && args.length == 2) invite(player, team, args[1]);
        else if (action.equals("accept") && args.length == 2) accept(player, args[1]);
        else if (action.equals("leave")) leave(player, team);
        else if (action.equals("disband")) disband(player, team);
        else if (action.equals("home")) teamHome(player, team, args);
        else message(player, "&e/team [create <name> <allgemein|peace|purge>|invite <spieler>|accept <team>|leave|disband|home [set]]");
        return true;
    }

    private void createTeam(Player player, String name, String type) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (teamOf(player.getUniqueId()) != null) message(player, "&cDu bist bereits in einem Team.");
        else if (!validName(name) || data.contains("teams." + normalized)) message(player, "&cDieser Teamname ist ungültig oder bereits vergeben.");
        else if (!List.of("allgemein", "peace", "purge").contains(type.toLowerCase(Locale.ROOT))) message(player, "&cTyp: allgemein, peace oder purge.");
        else if (!charge(player, "team-create-cost")) { return; }
        else {
            data.set("teams." + normalized + ".name", name);
            data.set("teams." + normalized + ".type", type.toLowerCase(Locale.ROOT));
            data.set("teams." + normalized + ".owner", player.getUniqueId().toString());
            data.set("members." + player.getUniqueId(), normalized);
            saveData();
            message(player, "&aTeam &f" + name + " &aerstellt.");
        }
    }

    private void invite(Player player, String team, String targetName) {
        if (team == null || !isOwner(player, team)) { message(player, "&cNur der Teamleiter kann einladen."); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || teamOf(target.getUniqueId()) != null) { message(player, "&cSpieler ist nicht verfügbar oder bereits im Team."); return; }
        data.set("invites." + target.getUniqueId() + "." + team, System.currentTimeMillis() + 300000);
        saveData();
        message(target, "&d" + player.getName() + " &ahat dich in Team &f" + team + " &aeingeladen. &e/team accept " + team);
        message(player, "&aEinladung gesendet.");
    }

    private void accept(Player player, String team) {
        long expires = data.getLong("invites." + player.getUniqueId() + "." + team);
        if (teamOf(player.getUniqueId()) != null || expires < System.currentTimeMillis() || !data.contains("teams." + team)) {
            message(player, "&cKeine gültige Einladung gefunden."); return;
        }
        data.set("members." + player.getUniqueId(), team);
        data.set("invites." + player.getUniqueId() + "." + team, null);
        saveData();
        message(player, "&aDu bist Team &f" + team + " &abeigetreten.");
    }

    private void leave(Player player, String team) {
        if (team == null) { message(player, "&cDu bist in keinem Team."); return; }
        if (isOwner(player, team)) { message(player, "&cÜbertrage erst die Leitung oder löse das Team mit /team disband auf."); return; }
        data.set("members." + player.getUniqueId(), null); saveData();
        message(player, "&aDu hast das Team verlassen.");
    }

    private void disband(Player player, String team) {
        if (team == null || !isOwner(player, team)) {
            message(player, "&cNur der Teamleiter kann das Team auflösen.");
            return;
        }
        for (String member : new ArrayList<>(data.getConfigurationSection("members") == null
                ? List.<String>of() : data.getConfigurationSection("members").getKeys(false))) {
            if (team.equals(data.getString("members." + member))) data.set("members." + member, null);
        }
        data.set("teams." + team, null);
        saveData();
        message(player, "&aTeam &f" + team + " &aaufgelöst.");
    }

    private void teamHome(Player player, String team, String[] args) {
        if (team == null) { message(player, "&cDu bist in keinem Team."); return; }
        String path = "teams." + team + ".home";
        if (args.length == 2 && args[1].equalsIgnoreCase("set")) {
            if (!isOwner(player, team)) { message(player, "&cNur der Teamleiter kann das Team-Home setzen."); return; }
            if (!charge(player, "team-home-set-cost")) return;
            data.set(path, player.getLocation()); saveData(); message(player, "&aTeam-Home gesetzt.");
        } else {
            Location home = data.getLocation(path);
            if (home == null) { message(player, "&cDein Team hat noch kein Home."); return; }
            if (charge(player, "team-home-teleport-cost")) player.teleportAsync(home);
        }
    }

    private void openHomes(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, HOME_TITLE);
        List<String> homes = homeNames(player);
        for (int i = 0; i < homeLimit(player); i++) {
            if (i < homes.size()) inventory.setItem(10 + i, item(Material.RED_BED, "&b" + homes.get(i), "&7Klicken zum Teleportieren", "&8Shift-Klick zum Löschen"));
            else inventory.setItem(10 + i, item(Material.GRAY_BED, "&7Freier Slot", "&7/home set <name>"));
        }
        inventory.setItem(22, item(Material.BOOK, "&eHomes: " + homes.size() + "/" + homeLimit(player), "&7Setzen und Teleportieren kosten $"));
        player.openInventory(inventory);
    }

    private void openTeam(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, TEAM_TITLE);
        String team = teamOf(player.getUniqueId());
        if (team == null) inventory.setItem(13, item(Material.LIME_DYE, "&aTeam erstellen", "&7/team create <name> <typ>"));
        else {
            String type = data.getString("teams." + team + ".type", "allgemein");
            inventory.setItem(11, item(Material.NAME_TAG, "&d" + data.getString("teams." + team + ".name"), "&7Typ: &f" + type));
            inventory.setItem(13, item(Material.ENDER_PEARL, "&aTeam-Home", "&7Klicken zum Teleportieren"));
            inventory.setItem(15, item(Material.PLAYER_HEAD, "&eMitglieder", "&7/team invite <spieler>"));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(HOME_TITLE) && !title.equals(TEAM_TITLE)) return;
        event.setCancelled(true);
        if (title.equals(HOME_TITLE) && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.RED_BED) {
            String name = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
            if (event.isShiftClick()) homeCommand(player, new String[]{"delete", name});
            else teleportHome(player, name);
            player.closeInventory();
        } else if (title.equals(TEAM_TITLE) && event.getSlot() == 13 && teamOf(player.getUniqueId()) != null) {
            teamHome(player, teamOf(player.getUniqueId()), new String[]{"home"});
            player.closeInventory();
        }
    }

    @EventHandler public void close(InventoryCloseEvent ignored) { }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(color(line));
        meta.setLore(lines); stack.setItemMeta(meta);
        return stack;
    }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    private void message(Player player, String value) { player.sendMessage(color("&8[&byuuh92&8] &r" + value)); }
    private boolean validName(String name) { return name.matches("[A-Za-z0-9_-]{1,16}"); }
    private String homePath(Player p, String name) { return "homes." + p.getUniqueId() + "." + name.toLowerCase(Locale.ROOT); }
    private List<String> homeNames(Player p) {
        ConfigurationSection section = data.getConfigurationSection("homes." + p.getUniqueId());
        return section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
    }
    private int homeLimit(Player p) {
        if (p.hasPermission("yuuh92.homes.vip3")) return 5;
        if (p.hasPermission("yuuh92.homes.vip2")) return 4;
        if (p.hasPermission("yuuh92.homes.vip1")) return 3;
        return 2;
    }
    private String teamOf(UUID uuid) { return data.getString("members." + uuid); }
    private boolean isOwner(Player p, String team) { return p.getUniqueId().toString().equals(data.getString("teams." + team + ".owner")); }
    private boolean charge(Player p, String key) {
        double amount = getConfig().getDouble(key, 0);
        if (amount <= 0) return true;
        Object economy = vaultEconomy();
        if (economy != null) {
            try {
                if (!(Boolean) economy.getClass().getMethod("has", OfflinePlayer.class, double.class).invoke(economy, p, amount)) {
                    message(p, "&cDu benötigst $" + amount + "."); return false;
                }
                economy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(economy, p, amount);
                return true;
            } catch (ReflectiveOperationException e) {
                getLogger().warning("Vault-Economy konnte nicht genutzt werden: " + e.getMessage());
            }
        }
        double balance = data.getDouble("balances." + p.getUniqueId(), getConfig().getDouble("starting-balance", 0));
        if (balance < amount) { message(p, "&cDu benötigst $" + amount + "."); return false; }
        data.set("balances." + p.getUniqueId(), balance - amount); saveData(); return true;
    }
    private void refund(Player p, String key) {
        double amount = getConfig().getDouble(key);
        Object economy = vaultEconomy();
        if (economy != null) {
            try {
                economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(economy, p, amount);
                return;
            } catch (ReflectiveOperationException e) {
                getLogger().warning("Vault-Economy konnte nicht erstattet werden: " + e.getMessage());
            }
        }
        data.set("balances." + p.getUniqueId(), data.getDouble("balances." + p.getUniqueId()) + amount); saveData();
    }
    private Object vaultEconomy() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(economyClass);
            return registration == null ? null : registration.getClass().getMethod("getProvider").invoke(registration);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
    private void sendPhase(Player p) {
        int hour = LocalTime.now().getHour();
        boolean peace = hour >= getConfig().getInt("peace-start-hour", 8) && hour < getConfig().getInt("purge-start-hour", 20);
        message(p, peace ? "&aPeace-Phase &7(08:00–20:00): Bauen und Erkunden." : "&cPurge-Phase &7(20:00–08:00): Krieg und PvP.");
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("home") && args.length == 1) return List.of("set", "teleport", "delete");
        if (command.getName().equalsIgnoreCase("home") && args.length == 2 && !args[0].equalsIgnoreCase("set")) return sender instanceof Player p ? homeNames(p) : List.of();
        if (command.getName().equalsIgnoreCase("team") && args.length == 1) return List.of("create", "invite", "accept", "leave", "disband", "home");
        if (command.getName().equalsIgnoreCase("team") && args.length == 3 && args[0].equalsIgnoreCase("create")) return List.of("allgemein", "peace", "purge");
        return List.of();
    }
}
