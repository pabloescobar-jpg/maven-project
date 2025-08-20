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

// ProtocolLib
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter.AdapterParameteters;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;

public final class ClickCopyChat extends JavaPlugin implements Listener {
    private ProtocolManager protocol;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        // Optional, but recommended
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            protocol = ProtocolLibrary.getProtocolManager();
            registerPacketHook();
            getLogger().info("ProtocolLib detected: system & plugin messages will be copyable too.");
        } else {
            getLogger().warning("ProtocolLib not found: only player chat will be copyable.");
        }
    }

    // 1) Player chat: wrap the active renderer (EssentialsChat etc.) and then decorate
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = original.render(source, displayName, message, viewer);
            return decorate(rendered);
        });
    }

    // 2) All other chat/system messages via ProtocolLib
    private void registerPacketHook() {
        PacketListener listener = new PacketAdapter(this,
                ListenerPriority.NORMAL,
                PacketType.Play.Server.SYSTEM_CHAT,   // system messages, broadcasts, many plugin sends
                PacketType.Play.Server.PLAYER_CHAT    // signed/unsigned player chat variants
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                // Try Adventure Component fields first (1.19.4+ / 1.20+)
                StructureModifier<Component> comps = packet.getModifier().withType(Component.class);
                if (!comps.getValues().isEmpty()) {
                    Component c = comps.read(0);
                    if (c != null) comps.write(0, decorate(c));
                    return;
                }

                // Fallback: older wrappers may expose chat components differently;
                // if nothing found, we just leave the packet alone.
            }
        };
        protocol.addPacketListener(listener);
    }

    // Add copy-to-clipboard if it isn't already present
    private Component decorate(Component rendered) {
        if (rendered == null) return null;
        if (rendered.clickEvent() != null) return rendered; // don't override existing click handlers
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        return rendered
                .clickEvent(ClickEvent.copyToClipboard(plain))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy")));
    }
}
