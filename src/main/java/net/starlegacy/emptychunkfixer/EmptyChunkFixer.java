package net.starlegacy.emptychunkfixer;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import net.minecraft.server.v1_12_R1.Chunk;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.NibbleArray;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused") // referenced in plugin.yml
public final class EmptyChunkFixer extends JavaPlugin implements Listener {
    private static Map<String, AtomicInteger> clearedChunkSections = new HashMap<>();

    private static Field nonEmptyBlockCountField;

    private static int getNonEmptyBlockCount(ChunkSection section) {
        try {
            return nonEmptyBlockCountField.getInt(section);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            nonEmptyBlockCountField = ChunkSection.class.getDeclaredField("nonEmptyBlockCount");
            nonEmptyBlockCountField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    private Timing timing = Timings.of(this, "empty_chunk_fixing");

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        timing.startTiming();
        try {
            if (event.isNewChunk()) return; // we're only fixing broken chunks from before
            CraftChunk craftChunk = (CraftChunk) event.getChunk();
            World world = craftChunk.getWorld();
            if (world.getEnvironment() == World.Environment.THE_END) return; // only do it in space
            Chunk chunk = craftChunk.getHandle();
            ChunkSection[] sections = chunk.getSections();

            int cleared = 0;

            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == Chunk.EMPTY_CHUNK_SECTION) continue;

                int nonEmptyBlockCount = getNonEmptyBlockCount(section);
                if (nonEmptyBlockCount > 0) {
                    // only delete chunk sections with no blocks. otherwise, simply remove light
                    section.a(new NibbleArray()); // set empty light data
                    continue;
                }

                // we haven't encountered any non-air blocks, ruthlessly slaughter it
                sections[i] = null;
                cleared++;
            }

            if (cleared > 0) {
                clearedChunkSections.computeIfAbsent(world.getName(), w -> new AtomicInteger()).incrementAndGet();
            }
        } finally {
            timing.stopTiming();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Empty Chunk Fixer");
        clearedChunkSections.forEach((world, affected) ->
                sender.sendMessage(ChatColor.DARK_PURPLE + world + ": " + ChatColor.RED + affected.toString())
        );
        return true;
    }
}
