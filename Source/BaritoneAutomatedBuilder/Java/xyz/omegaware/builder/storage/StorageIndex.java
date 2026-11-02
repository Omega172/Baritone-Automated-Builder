package xyz.omegaware.builder.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import xyz.omegaware.builder.util.Log;
import xyz.omegaware.builder.util.Storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageIndex {
    public record ShulkerInfo(int slot, Map<Item, Integer> contents) {}

    public static class Entry {
        public final BlockPos pos;
        public final Map<Item, Integer> contents;
        public final List<ShulkerInfo> shulkers;

        public Entry(BlockPos pos, Map<Item, Integer> contents, List<ShulkerInfo> shulkers) {
            this.pos = pos;
            this.contents = contents;
            this.shulkers = shulkers;
        }
    }

    private static final StorageIndex s_instance = new StorageIndex();
    private static final Gson s_gson = new GsonBuilder().setPrettyPrinting().create();

    private final List<Entry> m_aEntries = new ArrayList<>();
    private BlockPos m_home = null;
    private BlockPos m_dump = null;
    private String m_strLoadedKey = null;

    public static StorageIndex get() {
        return s_instance;
    }

    private static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        String strServer = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
        String strDimension = mc.level != null ? mc.level.dimension().identifier().toString() : "unknown";
        return (strServer + "_" + strDimension).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path file(String strKey) {
        return FabricLoader.getInstance().getConfigDir().resolve("baritone-automated-builder").resolve(strKey + ".json");
    }

    public void ensureLoaded() {
        String strKey = worldKey();
        if (strKey.equals(m_strLoadedKey)) return;
        m_strLoadedKey = strKey;
        m_aEntries.clear();
        m_home = null;
        m_dump = null;

        Path path = file(strKey);
        if (!Files.exists(path)) return;
        try {
            JsonObject root = s_gson.fromJson(Files.readString(path), JsonObject.class);
            if (root.has("home")) m_home = BlockPos.of(root.get("home").getAsLong());
            if (root.has("dump")) m_dump = BlockPos.of(root.get("dump").getAsLong());
            for (var storageElement : root.getAsJsonArray("storages")) {
                JsonObject storageJson = storageElement.getAsJsonObject();
                Map<Item, Integer> contents = new HashMap<>();
                JsonObject itemsJson = storageJson.getAsJsonObject("items");
                for (String strId : itemsJson.keySet()) {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(strId));
                    contents.put(item, itemsJson.get(strId).getAsInt());
                }
                List<ShulkerInfo> shulkers = new ArrayList<>();
                if (storageJson.has("shulkers")) {
                    for (var shulkerElement : storageJson.getAsJsonArray("shulkers")) {
                        JsonObject shulkerJson = shulkerElement.getAsJsonObject();
                        Map<Item, Integer> shulkerContents = new HashMap<>();
                        JsonObject shulkerItems = shulkerJson.getAsJsonObject("items");
                        for (String strId : shulkerItems.keySet()) {
                            shulkerContents.put(BuiltInRegistries.ITEM.getValue(Identifier.parse(strId)), shulkerItems.get(strId).getAsInt());
                        }
                        shulkers.add(new ShulkerInfo(shulkerJson.get("slot").getAsInt(), shulkerContents));
                    }
                }
                m_aEntries.add(new Entry(BlockPos.of(storageJson.get("pos").getAsLong()), contents, shulkers));
            }
        } catch (Exception e) {
            Log.LOGGER.error("Failed to load storage index {}", path, e);
        }
    }

    public void save() {
        if (m_strLoadedKey == null) return;
        Path path = file(m_strLoadedKey);
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            if (m_home != null) root.addProperty("home", m_home.asLong());
            if (m_dump != null) root.addProperty("dump", m_dump.asLong());
            JsonArray storages = new JsonArray();
            for (Entry entry : m_aEntries) {
                JsonObject storageJson = new JsonObject();
                storageJson.addProperty("pos", entry.pos.asLong());
                JsonObject itemsJson = new JsonObject();
                entry.contents.forEach((item, iCount) -> itemsJson.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(), iCount));
                storageJson.add("items", itemsJson);
                JsonArray shulkersJson = new JsonArray();
                for (ShulkerInfo info : entry.shulkers) {
                    JsonObject shulkerJson = new JsonObject();
                    shulkerJson.addProperty("slot", info.slot());
                    JsonObject shulkerItems = new JsonObject();
                    info.contents().forEach((item, iCount) -> shulkerItems.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(), iCount));
                    shulkerJson.add("items", shulkerItems);
                    shulkersJson.add(shulkerJson);
                }
                storageJson.add("shulkers", shulkersJson);
                storages.add(storageJson);
            }
            root.add("storages", storages);
            Files.writeString(path, s_gson.toJson(root));
        } catch (IOException e) {
            Log.LOGGER.error("Failed to save storage index {}", path, e);
        }
    }

    public void record(BlockPos pos, Map<Item, Integer> contents) {
        record(pos, contents, List.of());
    }

    private static BlockPos canonical(BlockPos pos) {
        return Storage.canonical(Minecraft.getInstance().level, pos).immutable();
    }

    public void record(BlockPos rawPos, Map<Item, Integer> contents, List<ShulkerInfo> shulkers) {
        ensureLoaded();
        BlockPos pos = canonical(rawPos);
        m_aEntries.removeIf(entry -> entry.pos.equals(pos));
        m_aEntries.add(new Entry(pos, new HashMap<>(contents), List.copyOf(shulkers)));
        save();
    }

    public List<Entry> shulkerSourcesFor(Item item, BlockPos from) {
        ensureLoaded();
        List<Entry> aSources = new ArrayList<>();
        for (Entry entry : m_aEntries) {
            for (ShulkerInfo info : entry.shulkers) {
                if (info.contents().getOrDefault(item, 0) > 0) {
                    aSources.add(entry);
                    break;
                }
            }
        }
        aSources.sort((a, b) -> Double.compare(a.pos.distSqr(from), b.pos.distSqr(from)));
        return aSources;
    }

    public void forget(BlockPos rawPos) {
        ensureLoaded();
        BlockPos pos = canonical(rawPos);
        m_aEntries.removeIf(entry -> entry.pos.equals(pos));
        save();
    }

    public void clear() {
        ensureLoaded();
        m_aEntries.clear();
        save();
    }

    public List<Entry> all() {
        ensureLoaded();
        return List.copyOf(m_aEntries);
    }

    public List<Entry> sourcesFor(Item item, BlockPos from) {
        ensureLoaded();
        List<Entry> aSources = new ArrayList<>();
        for (Entry entry : m_aEntries) {
            if (entry.contents.getOrDefault(item, 0) > 0) aSources.add(entry);
        }
        aSources.sort((a, b) -> Double.compare(a.pos.distSqr(from), b.pos.distSqr(from)));
        return aSources;
    }

    public BlockPos home() {
        ensureLoaded();
        return m_home;
    }

    public BlockPos dump() {
        ensureLoaded();
        return m_dump;
    }

    public void setDump(BlockPos dump) {
        ensureLoaded();
        m_dump = dump == null ? null : canonical(dump);
        save();
    }

    public void setHome(BlockPos home) {
        ensureLoaded();
        m_home = home.immutable();
        save();
    }
}
