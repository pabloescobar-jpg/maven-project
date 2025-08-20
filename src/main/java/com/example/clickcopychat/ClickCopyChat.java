package com.example.clickcopychat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ClickCopyChat extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ClickCopyChat enabled");
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Component original = event.message();
        String plain = PlainTextComponentSerializer.plainText().serialize(original);
        Component decorated = original
                .clickEvent(ClickEvent.copyToClipboard(plain))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy")));
        event.message(decorated);
    }
}
