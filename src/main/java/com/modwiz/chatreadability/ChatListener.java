package com.modwiz.chatreadability;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatListener implements Listener {

    private final ChatReadability plugin;

    // Probably don't really need local caches for performance, might be slower too honestly
    //private final List<ChatColor> lineColors; // Can store here and be final for now since config reload not supported
    //private final List<ChatColor> nameColors; // same as above, caching here is probably good,

    private AtomicInteger alternateLine = new AtomicInteger(0); // Atomic since chat can be async, int for future multi-color alternating idea
    private ConcurrentHashMap<String, ChatColor> assignedColors = new ConcurrentHashMap<>(); // Concurrent because assignments might come from multiple places

    public ChatListener(ChatReadability plugin) {
        this.plugin = plugin;
    }

    /* Lets use main thread events since we don't know if this is called too early or too late
    @EventHandler
    public void onAsyncPlayerJoin(AsyncPlayerPreLoginEvent)
    */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Is this slow as all hell? No idea really. Does that matter? Probably not.
        int index = (event.getPlayer().getName().hashCode()*31+LocalTime.now().getMinute()) % plugin.getNameColors().size();
        ChatColor nameColor = plugin.getNameColors().get(index);
        assignedColors.put(event.getPlayer().getName(), nameColor);

        // Later we set the format string so we can change the player name color in messages but it would be nice if the name list looked
        // different too (under tab). Set the player list name name to make this happen
        // This would obviously need to move to a scheduled event if we did name color generation async
        event.getPlayer().setPlayerListName(nameColor + event.getPlayer().getPlayerListName());
    }

    /*
    Important to free up the map, this is probably the easiest (but like most of bukkit it might not actually be safe)
     */
    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        // Clean up afterwards
        assignedColors.remove(event.getPlayer().getName());
    }

    /*
    Important reminder is that this is called asynchronously sometimes, I think in the past it was basically always
    synchronous on bukkit servers, (at least without bungeecord) but that's not a given, be careful.
     */
    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) {
            // We care not if ye olde chat message is nullified
            return;
        }
        // TODO: Make this use some kind of nice modern Java text templating and allow it in config
        // for now format is like vanilla <username> chat message

        // above is slightly wrong, since I read the event docs, we have to edit the format string or resend the chat
        // ourselves if we want to change player color. I opt for the former, we change the format string and use legacy text
        // in the assumption it will work
        ChatColor nameColor = assignedColors.get(event.getPlayer().getName());

        // I'm sure this logic is very wrong but if it works I'll leave it as it's kind of on the right track
        // I will think about how this should really work later, I'd like to implement wrap around logic on the atomic setting
        // just to know how to do it properly and efficiently
        int lineColorIndex = alternateLine.getAndIncrement() % plugin.getMessageColors().size();
        ChatColor messageColor = plugin.getMessageColors().get(lineColorIndex);

        // Build up the new format by using the component system only to convert to legacy text so we can change it on the
        // event level rather than queuing a sync task to send a BaseComponent message via Server

        // Built by specifying parts then modifying properties, repeated, not setting properties then adding parts,
        // that makes more sense for how the json output actually works.
        String newFormat = BaseComponent.toLegacyText(
                new ComponentBuilder("<").color(ChatColor.GRAY)
                .append("%1$s").color(nameColor)
                .append("> ").color(ChatColor.GRAY)
                .append("%2$s").color(messageColor).create());
        // Set the new message format
        event.setFormat(newFormat);
    }
}
