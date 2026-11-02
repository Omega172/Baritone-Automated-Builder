package xyz.omegaware.builder.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShulkerLedger {
    public static class Entry {
        public final BlockPos origin;
        public final int iOriginSlot;
        public Map<Item, Integer> contents;

        public Entry(BlockPos origin, int iOriginSlot, Map<Item, Integer> contents) {
            this.origin = origin;
            this.iOriginSlot = iOriginSlot;
            this.contents = contents;
        }
    }

    private static final ShulkerLedger s_instance = new ShulkerLedger();

    public static int iUseCycles = 0;

    private final List<Entry> m_aEntries = new ArrayList<>();

    public static ShulkerLedger get() {
        return s_instance;
    }

    public Entry add(BlockPos origin, int iOriginSlot, Map<Item, Integer> contents) {
        Entry entry = new Entry(origin.immutable(), iOriginSlot, Map.copyOf(contents));
        m_aEntries.add(entry);
        return entry;
    }

    public Entry matching(Map<Item, Integer> contents) {
        for (Entry entry : m_aEntries) {
            if (entry.contents.equals(contents)) return entry;
        }
        return null;
    }

    public void remove(Entry entry) {
        m_aEntries.remove(entry);
    }

    public void clear() {
        m_aEntries.clear();
    }

    public List<Entry> all() {
        return List.copyOf(m_aEntries);
    }

    public List<Entry> empties() {
        return m_aEntries.stream().filter(entry -> entry.contents.isEmpty()).toList();
    }
}
