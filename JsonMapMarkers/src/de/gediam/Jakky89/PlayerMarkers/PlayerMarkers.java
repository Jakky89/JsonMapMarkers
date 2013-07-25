package de.gediam.Jakky89.PlayerMarkers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class PlayerMarkers extends JavaPlugin implements Runnable, Listener {
	private static Integer mUpdateDelay;
	private static Integer mUpdateTaskId;
	private static PluginDescriptionFile mPdfFile;
	private static Map<String, Location> mLastPlayerPositions;
	private static Map<String, String> mLastChatMessages;
	private static File mTargetLocationsFile;

	public void onEnable() {
		mUpdateTaskId = null;
		mPdfFile = this.getDescription();
		getConfig().options().copyDefaults(true);
		File targetLocationsFilePath = new File(getConfig().getString("TargetLocationsJsonFilePath"), "playerlocations.json");
		if (targetLocationsFilePath.isAbsolute()) {
			mTargetLocationsFile = targetLocationsFilePath;
		} else {
			mTargetLocationsFile = new File(getDataFolder(), targetLocationsFilePath.getPath());
		}
		mUpdateDelay = Math.round(getConfig().getInt("FileUpdateDelay") / 50);
		if (mUpdateDelay < 20) {
			mUpdateDelay = 20;
		}
		mLastPlayerPositions = new HashMap<String, Location>();
		mLastChatMessages = new HashMap<String, String>();
		saveConfig();
		getServer().getPluginManager().registerEvents(this, this);
		Logger.getLogger(mPdfFile.getName()).log(Level.INFO, mPdfFile.getName() + " version " + mPdfFile.getVersion() + " enabled.");
		mUpdateTaskId = getServer().getScheduler().scheduleSyncDelayedTask(this, this, mUpdateDelay);
		writePlayerPositions();
	}

	public void onDisable() {
		if (mUpdateTaskId != null) {
			getServer().getScheduler().cancelTask(mUpdateTaskId);
		}
		writePlayerPositions();
		Logger.getLogger(mPdfFile.getName()).log(Level.INFO, mPdfFile.getName() + " disabled.");
	}
	
	public void checkPositionChange(Player player, Location newLoc) {
		if (player != null && newLoc != null) {
			Location savedLoc = mLastPlayerPositions.get(player.getName());
			if (savedLoc == null || (newLoc.getX() < savedLoc.getX()-8) || (newLoc.getY() < savedLoc.getY()-8) || (newLoc.getZ() < savedLoc.getZ()-8) || (newLoc.getX() > savedLoc.getX()+8) || (newLoc.getY() > savedLoc.getY()+8) || (newLoc.getZ() > savedLoc.getZ()+8) || !newLoc.getWorld().getName().equals(savedLoc.getWorld().getName())) {
				mLastPlayerPositions.put(player.getName(), newLoc.clone());
				if (mUpdateTaskId == null) {
					mUpdateTaskId = getServer().getScheduler().scheduleSyncDelayedTask(this, this, mUpdateDelay);
				}
			}
		}
	}

	/**
	 * EVENTS
	 */
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (player != null) {
			checkPositionChange(player, player.getLocation());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (player != null) {
			mLastChatMessages.remove(player.getName());
			if (mUpdateTaskId == null) {
				mUpdateTaskId = getServer().getScheduler().scheduleSyncDelayedTask(this, this, mUpdateDelay);
			}
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event) {
		Location newLoc = event.getFrom();
		Location oldLoc = event.getTo();
		if (newLoc != null && (oldLoc == null || (newLoc.getBlockX() != oldLoc.getBlockX()) || (newLoc.getBlockY() != oldLoc.getBlockY()) || (newLoc.getBlockZ() != oldLoc.getBlockZ()) || (newLoc.getWorld().getName() != oldLoc.getWorld().getName()))) {
			checkPositionChange(event.getPlayer(), newLoc);
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		if (event.getMessage() != null && !event.getMessage().isEmpty() && event.getPlayer() != null) {
			String cmsg = ChatColor.stripColor(event.getMessage().trim()).replaceAll("[^a-zA-Z0-9]+","");
			if (!cmsg.isEmpty()) {
				String lm = mLastChatMessages.get(event.getPlayer().getName());
				if (lm == null || !lm.equals(cmsg)) {
					mLastChatMessages.put(event.getPlayer().getName(), cmsg);
					if (mUpdateTaskId == null) {
						mUpdateTaskId = getServer().getScheduler().scheduleSyncDelayedTask(this, this, mUpdateDelay);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void writePlayerPositions() {
		Player[] players = getServer().getOnlinePlayers();
		JSONArray jsonData = new JSONArray();
		if (players != null && players.length > 0) {
			for (Player p : players) {
				if (p != null && p.isOnline()) {
					JSONObject positionData = new JSONObject();
					positionData.put("name", p.getName());
					if (p.getDisplayName() != null && !p.getDisplayName().isEmpty()) {
						String nn = ChatColor.stripColor(p.getDisplayName().trim());
						if (!nn.isEmpty()) {
							positionData.put("nick", p.getDisplayName());
						}
					}
					positionData.put("world", p.getLocation().getWorld().getName());
					positionData.put("x", String.valueOf(p.getLocation().getBlockX()));
					positionData.put("y", String.valueOf(p.getLocation().getBlockY()));
					positionData.put("z", String.valueOf(p.getLocation().getBlockZ()));
					positionData.put("health", String.valueOf(p.getHealth()));
					positionData.put("level", String.valueOf(p.getLevel()));
					String cmsg = mLastChatMessages.get(p.getName());
					if (cmsg != null) {
						positionData.put("msg", cmsg);
					}
					jsonData.add(positionData);
				}
			}
		}
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new BufferedWriter(new FileWriter(mTargetLocationsFile)));
			if (!jsonData.isEmpty()) {
				writer.print(jsonData);
			} else {
				writer.print("");
			}
		} catch (Exception e) {
			Logger.getLogger(mPdfFile.getName()).log(Level.SEVERE, "[" + mPdfFile.getName() + "] Error while writing json-data to " + mTargetLocationsFile.getAbsolutePath() + ": \n" + e.getMessage());
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (Exception e) {}
			}
		}
	}
	
	@Override
	public void run() {
		writePlayerPositions();
		mUpdateTaskId = null;
	}

}
