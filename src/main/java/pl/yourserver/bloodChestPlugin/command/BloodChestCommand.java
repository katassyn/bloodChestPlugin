package pl.yourserver.bloodChestPlugin.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.yourserver.bloodChestPlugin.config.PluginConfiguration;
import pl.yourserver.bloodChestPlugin.gui.MenuManager;

public class BloodChestCommand implements CommandExecutor {

    private final PluginConfiguration configuration;
    private final MenuManager menuManager;

    public BloodChestCommand(PluginConfiguration configuration, MenuManager menuManager) {
        this.configuration = configuration;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command is only available to players.");
            return true;
        }
        String permission = configuration.getPermission();
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        menuManager.openMainMenu(player);
        return true;
    }
}
