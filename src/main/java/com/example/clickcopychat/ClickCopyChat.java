package com.example.clickcopychat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

// ProtocolLib
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
import java.util.concurrent.atomic.AtomicInteger;

public final class ClickCopyChat extends JavaPlugin implements Listener {
    private ProtocolManager protocol;
    private volatile boolean debug = false;

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

    // === Player chat (renderer path; works with EssentialsChat, prefixes, etc.) ===
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = original.render(source, displayName, message, viewer);
            Component out = decorateForce(rendered);
            if (debug) {
                String plain = PlainTextComponentSerializer.plainText().serialize(out);
                getLogger().info("[DEBUG] Renderer applied for " + (viewer instanceof Player p ? p.getName() : "viewer")
                        + " | len=" + plain.length() + " | preview=\"" + preview(plain) + "\"");
            }
            return out;
        });
    }

    // === System/plugin messages (ProtocolLib packet hook) ===
    private void registerPacketHook() {
        protocol.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
                PacketType.Play.Server.SYSTEM_CHAT // Covers broadcasts, command outputs, Skript send/error lines, etc.
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                int touched = 0;
                // 1) Adventure Component fields (modern path). Touch ALL of them.
                StructureModifier<Component> adv = packet.getModifier().withType(Component.class);
                for (int i = 0; i < adv.size(); i++) {
                    Component c = adv.readSafely(i);
                    if (c != null) {
                        adv.write(i, decorateForce(c));
                        touched++;
                    }
                }

                // 2) Fallback: WrappedChatComponent JSON fields (compat path). Touch ALL of them.
                StructureModifier<WrappedChatComponent> wrap = packet.getModifier().withType(WrappedChatComponent.class);
                for (int i = 0; i < wrap.size(); i++) {
                    WrappedChatComponent wc = wrap.readSafely(i);
                    if (wc != null && wc.getJson() != null) {
                        Component c = GsonComponentSerializer.gson().deserialize(wc.getJson());
                        Component out = decorateForce(c);
                        String jsonOut = GsonComponentSerializer.gson().serialize(out);
                        wrap.write(i, WrappedChatComponent.fromJson(jsonOut));
                        touched++;
                    }
                }

                if (debug && touched > 0) {
                    String who = (event.getPlayer() != null ? event.getPlayer().getName() : "viewer");
                    getLogger().info("[DEBUG] SYSTEM_CHAT modified for " + who + " | fields=" + touched);
                }
            }
        });
    }

    // === Force copy-to-clipboard on root + all children ===
    private Component decorateForce(Component rendered) {
        if (rendered == null) return null;
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        ClickEvent click = ClickEvent.copyToClipboard(plain);
        HoverEvent<?> hover = HoverEvent.showText(Component.text("Click to copy"));
        return applyDeep(rendered, click, hover);
    }

    private Component applyDeep(Component node, ClickEvent click, HoverEvent<?> hover) {
        Component styled = node.style(s -> s.clickEvent(click).hoverEvent(hover));
        List<Component> children = node.children();
        if (children.isEmpty()) return styled;
        List<Component> newChildren = new ArrayList<>(children.size());
        for (Component child : children) {
            newChildren.add(applyDeep(child, click, hover));
        }
        return styled.children(newChildren);
    }

    private String preview(String s) {
        String p = s.replace('\n', ' ').trim();
        return p.length() > 60 ? p.substring(0, 60) + "…" : p;
    }

    // === Tiny debug toggle: /cccdebug (permission: clickcopychat.debug) ===
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("cccdebug")) return false;
        if (!sender.hasPermission("clickcopychat.debug")) {
            sender.sendMessage("No permission.");
            return true;
        }
        debug = !debug;
        sender.sendMessage("ClickCopyChat debug: " + (debug ? "ON" : "OFF"));
        getLogger().info("Debug now " + (debug ? "ON" : "OFF") + " (toggling packet logs).");
        return true;
    }
}
