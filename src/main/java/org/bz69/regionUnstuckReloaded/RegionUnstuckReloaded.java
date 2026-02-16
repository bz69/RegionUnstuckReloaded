package org.bz69.regionUnstuckReloaded;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RegionUnstuckReloaded extends JavaPlugin {

    private UnstuckCommand unstuckCommand;
    private final Set<String> registeredAliases = new HashSet<>();

    /** Initializes plugin and registers commands/listeners. */
    @Override
    public void onEnable() {
        saveDefaultConfig();

        unstuckCommand = new UnstuckCommand(this);
        getCommand("regionunstuckreloaded").setExecutor(new ReloadCommand(this, unstuckCommand));

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        registerAliases();

        getLogger().info("RegionUnstuckReloaded v" + getDescription().getVersion() + " enabled.");
    }

    /** Applies prefix and color formatting. */
    public String formatMessage(String message) {
        String prefix = getConfig().getString("messages.prefix", "§8[§6RegionUnstuck§8]§r ");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    /** Returns debug flag from config. */
    public boolean isDebugEnabled() {
        return getConfig().getBoolean("settings.debug", false);
    }

    /** Registers dynamic aliases from config. */
    public void registerAliases() {
        unregisterAliases();

        List<String> aliases = getAliasesFromConfig();
        if (aliases == null || aliases.isEmpty()) {
            return;
        }

        CommandMap commandMap = getCommandMap();
        if (commandMap == null) {
            return;
        }

        for (String rawAlias : aliases) {
            if (rawAlias == null) {
                continue;
            }
            String alias = rawAlias.trim().toLowerCase(Locale.ROOT);
            if (alias.isEmpty() || alias.equals("regionunstuckreloaded")) {
                continue;
            }
            if (commandMap.getCommand(alias) != null) {
                if (isDebugEnabled()) {
                    getLogger().warning("Alias already exists and was skipped: " + alias);
                }
                continue;
            }

            commandMap.register(getDescription().getName(), new AliasForwardCommand(alias, this, unstuckCommand));
            registeredAliases.add(alias);
        }
    }

    private List<String> getAliasesFromConfig() {
        Object raw = getConfig().get("command.aliases");
        if (raw instanceof List) {
            return getConfig().getStringList("command.aliases");
        }
        if (raw instanceof String) {
            String text = (String) raw;
            return java.util.Arrays.stream(text.split("[,\\n\\r\\t ]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        return java.util.Collections.emptyList();
    }

    /** Unregisters previously registered aliases. */
    private void unregisterAliases() {
        CommandMap commandMap = getCommandMap();
        if (commandMap == null || registeredAliases.isEmpty()) {
            registeredAliases.clear();
            return;
        }

        Map<String, Command> known = getKnownCommands(commandMap);
        if (known == null) {
            registeredAliases.clear();
            return;
        }

        for (String alias : registeredAliases) {
            known.remove(alias);
        }
        registeredAliases.clear();
    }

    /** Gets Bukkit command map via reflection. */
    private CommandMap getCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (Exception e) {
            if (isDebugEnabled()) {
                getLogger().warning("Failed to access command map: " + e.getMessage());
            }
            return null;
        }
    }

    /** Gets knownCommands map from command map. */
    private Map<String, Command> getKnownCommands(CommandMap commandMap) {
        try {
            Field field = commandMap.getClass().getDeclaredField("knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(commandMap);
        } catch (Exception e) {
            if (isDebugEnabled()) {
                getLogger().warning("Failed to access known commands: " + e.getMessage());
            }
            return null;
        }
    }

    private static final class AliasForwardCommand extends Command {

        private final RegionUnstuckReloaded plugin;
        private final UnstuckCommand executor;

        protected AliasForwardCommand(String name, RegionUnstuckReloaded plugin, UnstuckCommand executor) {
            super(name);
            this.plugin = plugin;
            this.executor = executor;
            setDescription("Alias for /regionunstuckreloaded unstuck");
            setUsage("/" + name);
        }

        /** Forwards alias execution to main command handler. */
        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return executor.onCommand(sender, plugin.getCommand("regionunstuckreloaded"), label, args);
        }
    }
}
