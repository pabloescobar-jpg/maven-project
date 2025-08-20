package com.example.clickcopychat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import java.util.ArrayList;
import java.util.List;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = original.render(source, displayName, message, viewer);
            return decorateForce(rendered);
        });
    }

    private void registerPacketHook() {
        protocol.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
                PacketType.Play.Server.SYSTEM_CHAT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                // Adventure Component path
                StructureModifier<Component> adv = packet.getModifier().withType(Component.class);
                if (!adv.getValues().isEmpty()) {
                    Component c = adv.read(0);
                    if (c != null) {
                        adv.write(0, decorateForce(c));
                        return;
                    }
                }

                // Wrapped JSON path
                StructureModifier<WrappedChatComponent> wrap =
                        packet.getModifier().withType(WrappedChatComponent.class);
                if (!wrap.getValues().isEmpty()) {
                    WrappedChatComponent wc = wrap.read(0);
                    if (wc != null && wc.getJson() != null) {
                        Component c = GsonComponentSerializer.gson().deserialize(wc.getJson());
                        Component out = decorateForce(c);
                        String jsonOut = GsonComponentSerializer.gson().serialize(out);
                        wrap.write(0, WrappedChatComponent.fromJson(jsonOut));
                    }
                }
            }
        });
    }

    // ===== FIX: no mapChildrenDeep; use recursive styling instead =====
    private Component decorateForce(Component rendered) {
        if (rendered == null) return null;

        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        ClickEvent click = ClickEvent.copyToClipboard(plain);
        HoverEvent<?> hover = HoverEvent.showText(Component.text("Click to copy"));

        return applyDeep(rendered, click, hover);
    }

    private Component applyDeep(Component node, ClickEvent click, HoverEvent<?> hover) {
        // style this node
        Component styled = node.style(s -> s.clickEvent(click).hoverEvent(hover));

        // recurse into children
        List<Component> children = node.children();
        if (children.isEmpty()) return styled;

        List<Component> newChildren = new ArrayList<>(children.size());
        for (Component child : children) {
            newChildren.add(applyDeep(child, click, hover));
        }
        return styled.children(newChildren);
    }
}

