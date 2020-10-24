package com.modwiz.chatreadability;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
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

    // So I planned to do something clever here but then after deciding it might have actual problems I'll lay out the two
    // sane clever approaches
    // 1. Use ConcurrentHashMap, we basically only get during runtime, put is only used during the initial construction
    //    ConcurrentHashMap doesn't lock during gets (mostly apparently), and so it will be very fast and we will not
    //    have to think very hard about safety when reloading config at runtime gets implemented. Clearing the map isn't
    //    blocking so reloads would have to figure that out, probably best to rework this during reload
    // 2. Use an array or arraylist or AtomicInteger array stand in if required. This would be baked during construction
    //    and at reloads the plugin could unregister the old listener object and register a new one which would be rebaked
    //    or it would be baked again. This would likely be coupled with baking an array storing the configured colors
    //    instead of getting it all the time.
    // Because 2 sounds complicated, might not even work, and seems like a bad idea, we're going with 1, yes this is dumb
    // yes this is pointlessly optimized/considered, but it's fun to think big and surprisingly hard to choose an optimal
    // approach even on simple projects like this one
    //private ConcurrentHashMap<ChatColor, AtomicInteger> collisionCounter = new ConcurrentHashMap<>();

    // The counter approach has some issues in terms of "how do we find the new value if we need to check" and seemingly
    // being optimized for the uncommon case (not having any collisions), which might be avoidable, all we need though
    // is to get the values list from the assignedColors ConcurrentHashMap, then find the unused ones if we try to
    // add a duplicate

    // Eventually reload should be supported, which I now have an idea of how to do
    // first the base plugin would reload the config, which means the list of colors to index into will be correct
    // by the next join (not sure about memory model we may have to wrap that access so that the transition is seen
    // even tho it's immutable
    // Then after that the reload method can be called on this, which would keep only alternateLine (for consistency)
    // the other maps would be reassigned (which is probably safe?), and everything would be rebuilt using the new config
    // if reassigning isn't safe (which never mind I bet it's not), then we can clear, but in the interium, some entries
    // will be the same. Woo concurrency is fun to think about!!!!

    public ChatListener(ChatReadability plugin) {
        this.plugin = plugin;
    }

    /* Lets use main thread events since we don't know if this is called too early or too late
    @EventHandler
    public void onAsyncPlayerJoin(AsyncPlayerPreLoginEvent)
    */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // This is delegated elsewhere
        attachNameColor(event.getPlayer());
    }

    /**
     * Generate and store the matching name color for a player, handled during join and when an entry isn't found
     * @param player Player to use
     * @return Assigned color for easier use during missing entries
     */
    private ChatColor attachNameColor(Player player) {
        // Use a local copy of the list so that if it changes during a reload this is still valid
        List<ChatColor> nameColors = plugin.getNameColors();

        // Is this slow as all hell? No idea really. Does that matter? Probably not.

        // Hash codes can be negative and % doesn't positive, technically abs can give you a negative for
        // Integer.MIN_VALUE but that's never going to appear since it's already wrapped around and .size() will never
        // return that
        int computedValue = player.getName().hashCode()*31+LocalTime.now().getMinute();
        int index = Math.abs(computedValue % nameColors.size());
        ChatColor nameColor = nameColors.get(index);

        if (plugin.shouldAvoidCollisions()) {
            // If this was happening on every join (especially for bungeecord/velocity) we would want to partition these
            // somehow, probably by server or into buckets of players so that we aren't reconstructing this list

            // we can also probably avoid this check if more players are online than colors exist, but we would loose
            // the benefit of if all of one color left it would still not be run
            Collection<ChatColor> usedColors = assignedColors.values();
            if (usedColors.contains(nameColor)) {
                // If this color is already in use, we should look for an unused color, need a mutable list for this tho
                nameColors = new ArrayList<>(nameColors);
                nameColors.removeAll(usedColors);
                if (nameColors.size() > 0) {
                    // Is this messy or cool? Index into the unused colors using our original computed index
                    nameColor = nameColors.get(Math.abs(computedValue % nameColors.size()));
                }
            }

            // TODO: should the counter be disabled if the feature is?
            //AtomicInteger counter = collisionCounter.computeIfAbsent(nameColor, k -> new AtomicInteger());
            //int count = counter.get();
            // Opting for a simpler approach where we just keep track of the unused colors and grab one
        }
        // Update the mappings and the counter
        assignedColors.put(player.getName(), nameColor);

        // Later we set the format string so we can change the player name color in messages but it would be nice if the name list looked
        // different too (under tab). Set the player list name name to make this happen
        // This would obviously need to move to a scheduled event if we did name color generation async
        player.setPlayerListName(nameColor + player.getPlayerListName());

        return nameColor;
    }

    /*
    Important to free up the map, this is probably the easiest (but like most of bukkit it might not actually be safe)
     */
    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        // Remove it from the assignment map
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
        if (nameColor == null) {
            nameColor = attachNameColor(event.getPlayer());
        }

        // I'm sure this logic is very wrong but if it works I'll leave it as it's kind of on the right track
        // I will think about how this should really work later, I'd like to implement wrap around logic on the atomic setting
        // just to know how to do it properly and efficiently

        // This logic should properly disable itself when the disable is set, but for now lets make sure it can't go negative during
        // wrap around either
        int lineColorIndex = Math.abs(alternateLine.getAndIncrement() % plugin.getMessageColors().size());
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
