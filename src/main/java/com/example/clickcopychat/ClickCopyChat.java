package com.example.clickcopychat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
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
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

public final class ClickCopyChat extends JavaPlugin implements Listener {
    private ProtocolManager protocol;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            protocol = ProtocolLibrary.getProtocolManager();
            registerPacketHook();
            getLogger().info("ProtocolLib detected: system & plugin messages will be copyable too.");
        } else {
            getLogger().warning("ProtocolLib not found: only player chat will be copyable.");
        }
    }

    // Player chat: wrap the active renderer (EssentialsChat, etc.) then decorate
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = original.render(source, displayName, message, viewer);
            return decorate(rendered);
        });
    }

    // System / plugin messages via ProtocolLib
    private void registerPacketHook() {
        protocol.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
                PacketType.Play.Server.SYSTEM_CHAT   // modern path for broadcasts/command outputs/etc.
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                // Try direct Adventure Component first (modern Paper mappings)
                StructureModifier<Component> adv = packet.getModifier().withType(Component.class);
                if (!adv.getValues().isEmpty()) {
                    Component c = adv.read(0);
                    if (c != null) {
                        adv.write(0, decorate(c));
                        return;
                    }
                }

                // Fallback: JSON-based chat component (older/compat path)
                StructureModifier<WrappedChatComponent> wrap =
                        packet.getModifier().withType(WrappedChatComponent.class);
                if (!wrap.getValues().isEmpty()) {
                    WrappedChatComponent wc = wrap.read(0);
                    if (wc != null && wc.getJson() != null) {
                        // decode JSON -> Adventure, decorate, encode back to JSON
                        Component c = GsonComponentSerializer.gson().deserialize(wc.getJson());
                        Component out = decorate(c);
                        String jsonOut = GsonComponentSerializer.gson().serialize(out);
                        wrap.write(0, WrappedChatComponent.fromJson(jsonOut));
                    }
                }
            }
        });
    }

    // Add copy-to-clipboard unless already present
    private Component decorate(Component rendered) {
        if (rendered == null) return null;
        if (rendered.clickEvent() != null) return rendered; // don’t override existing click handlers
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        return rendered
                .clickEvent(ClickEvent.copyToClipboard(plain))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy")));
    }
}
