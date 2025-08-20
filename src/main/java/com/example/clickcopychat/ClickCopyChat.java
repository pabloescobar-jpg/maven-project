package com.example.clickcopychat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClickCopyChat extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ClickCopyChat enabled");
    }

    /**
     * Wrap the active chat renderer (e.g., EssentialsChat) and then add click-to-copy.
     * Running at MONITOR ensures we decorate the final formatted component.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        final ChatRenderer original = event.renderer();

        event.renderer((source, displayName, message, viewer) -> {
            // Render the message using whoever formatted it (EssentialsChat, etc.)
            Component rendered = original.render(source, displayName, message, viewer);

            // Use plain text of the fully-rendered line as the clipboard payload
            String plain = PlainTextComponentSerializer.plainText().serialize(rendered);

            // Add click-to-copy and a hover hint; keep the visual as-is
            return rendered
                .clickEvent(ClickEvent.copyToClipboard(plain))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy")));
        });
    }
}
