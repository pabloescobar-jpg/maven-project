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
import com.comphenix.protocol.PacketType.Protocol;
import com.comphenix.protocol.PacketType.Sender;
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
import java.util.Locale;

public final class ClickCopyChat extends JavaPlugin implements Listener {
    private ProtocolManager protocol;
    private volatile boolean debug = false;
    private List<PacketType> hooked = new ArrayList<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            protocol = ProtocolLibrary.getProtocolManager();
            registerPacketHooks();
            getLogger().info("ProtocolLib detected: system & plugin messages will be copyable too.");
        } else {
            getLogger().warning("ProtocolLib not found: only player chat will be copyable.");
        }
    }

    // === Player chat (EssentialsChat etc.) ===
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

    // === System / plugin messages (ProtocolLib) ===
    private void registerPacketHooks() {
        // Find all PLAY->SERVER packets whose name includes "CHAT"
        List<PacketType> candidates = new ArrayList<>();
        for (PacketType t : PacketType.values()) {
            if (t.getProtocol() == Protocol.PLAY && t.getSender() == Sender.SERVER) {
                String n = t.name().toUpperCase(Locale.ROOT);
                if (n.contains("CHAT")) candidates.add(t);
            }
        }

        if (candidates.isEmpty()) {
            getLogger().warning("No SERVER chat packet types were discovered via ProtocolLib. System/plugin messages won’t be copyable.");
            return;
        }

        hooked = candidates;
        protocol.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, candidates) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                int touched = 0;

                // 1) Adventure Components
                StructureModifier<Component> adv = packet.getModifier().withType(Component.class);
                for (int i = 0; i < adv.size(); i++) {
                    Component c = adv.readSafely(i);
                    if (c != null) {
                        adv.write(i, decorateForce(c));
                        touched++;
                    }
                }

                // 2) Wrapped JSON chat components
                StructureModifier<WrappedChatComponent> wrap = packet.getModifier().withType(WrappedChatComponent.class);
                for (int i = 0; i < wrap.size(); i++) {
                    WrappedChatComponent wc = wrap.readSafely(i);
                    if (wc != null && wc.getJson() != null) {
                        Component c = safeDeserialize(wc.getJson());
                        if (c != null) {
                            Component out = decorateForce(c);
                            wrap.write(i, WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(out)));
                            touched++;
                        }
                    }
                }

                // 3) Raw String fields (might be JSON or plain)
                StructureModifier<String> strs = packet.getStrings();
                for (int i = 0; i < strs.size(); i++) {
                    String s = strs.readSafely(i);
                    if (s == null) continue;
                    Component c = safeDeserialize(s);
                    if (c == null) c = Component.text(s);
                    Component out = decorateForce(c);
                    strs.write(i, GsonComponentSerializer.gson().serialize(out));
                    touched++;
                }

                if (debug && touched > 0) {
                    String who = (event.getPlayer() != null ? event.getPlayer().getName() : "viewer");
                    getLogger().info("[DEBUG] " + event.getPacketType() + " modified for " + who + " | fields=" + touched);
                }
            }
        });

        getLogger().info("Hooked chat packet types: " + hooked);
    }

    private Component safeDeserialize(String json) {
        try { return GsonComponentSerializer.gson().deserialize(json); }
        catch (Throwable ignored) { return null; }
    }

    // Force copy-to-clipboard on root + children
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
        for (Component child : children) newChildren.add(applyDeep(child, click, hover));
        return styled.children(newChildren);
    }

    private String preview(String s) {
        String p = s.replace('\n', ' ').trim();
        return p.length() > 60 ? p.substring(0, 60) + "…" : p;
    }

    // Commands: /cccdebug toggles logs; /cccpackets lists hooked packet types
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("cccdebug")) {
            if (!sender.hasPermission("clickcopychat.debug")) { sender.sendMessage("No permission."); return true; }
            debug = !debug;
            sender.sendMessage("ClickCopyChat debug: " + (debug ? "ON" : "OFF"));
            getLogger().info("Debug now " + (debug ? "ON" : "OFF"));
            return true;
        }
        if (label.equalsIgnoreCase("cccpackets")) {
            if (!sender.hasPermission("clickcopychat.debug")) { sender.sendMessage("No permission."); return true; }
            if (hooked.isEmpty()) sender.sendMessage("Hooked packets: (none)");
            else {
                sender.sendMessage("Hooked packets:");
                for (PacketType t : hooked) sender.sendMessage("- " + t);
            }
            return true;
        }
        return false;
    }
}
