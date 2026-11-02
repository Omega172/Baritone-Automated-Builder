package xyz.omegaware.builder.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import xyz.omegaware.builder.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Config {
    public enum PrinterSort { NONE, NEAREST, FURTHEST, TOP_DOWN, DOWN_TOP }
    public enum AirPlace { OFF, AUTO, ON }
    public enum PrinterBackend { AUTO, BUILTIN, METEOR }
    public enum ShulkerReturn { ORIGIN, DUMP }

    public static int iClickDelayTicks = 2;
    public static int iReservedSlots = 3;
    public static int iMaxStacksPerItem = 9;
    public static int iStuckSeconds = 20;
    public static int iMaxFailures = 3;
    public static boolean bAutoResupply = true;
    public static ShulkerReturn eShulkerReturn = ShulkerReturn.ORIGIN;
    public static boolean bReturnEmptyShulkers = true;
    public static boolean bReturnShulkersAtEnd = true;

    public static boolean bPrinter = false;
    public static int iPrinterDelayTicks = 2;
    public static int iPrinterBlocksPerTick = 1;
    public static double dPrinterRange = 4.4;
    public static PrinterSort ePrinterSort = PrinterSort.NEAREST;
    public static AirPlace eAirPlace = AirPlace.AUTO;
    public static PrinterBackend ePrinterBackend = PrinterBackend.AUTO;
    public static boolean bPrinterRotate = true;
    public static boolean bPrinterSwing = false;
    public static boolean bPrinterDirtAsGrass = true;

    private static final Gson s_gson = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("baritone-automated-builder").resolve("config.json");
    }

    public static Map<String, String> values() {
        Map<String, String> aValues = new LinkedHashMap<>();
        aValues.put("auto-resupply", String.valueOf(bAutoResupply));
        aValues.put("shulker-return", eShulkerReturn.name().toLowerCase());
        aValues.put("return-empty-shulkers", String.valueOf(bReturnEmptyShulkers));
        aValues.put("return-shulkers-at-end", String.valueOf(bReturnShulkersAtEnd));
        aValues.put("click-delay", String.valueOf(iClickDelayTicks));
        aValues.put("reserved-slots", String.valueOf(iReservedSlots));
        aValues.put("max-stacks-per-item", String.valueOf(iMaxStacksPerItem));
        aValues.put("stuck-seconds", String.valueOf(iStuckSeconds));
        aValues.put("max-failures", String.valueOf(iMaxFailures));
        aValues.put("printer", String.valueOf(bPrinter));
        aValues.put("printer-delay", String.valueOf(iPrinterDelayTicks));
        aValues.put("printer-blocks-per-tick", String.valueOf(iPrinterBlocksPerTick));
        aValues.put("printer-range", String.valueOf(dPrinterRange));
        aValues.put("printer-sort", ePrinterSort.name().toLowerCase());
        aValues.put("printer-air-place", eAirPlace.name().toLowerCase());
        aValues.put("printer-backend", ePrinterBackend.name().toLowerCase());
        aValues.put("printer-rotate", String.valueOf(bPrinterRotate));
        aValues.put("printer-swing", String.valueOf(bPrinterSwing));
        aValues.put("printer-dirt-as-grass", String.valueOf(bPrinterDirtAsGrass));
        return aValues;
    }

    public static String set(String strKey, String strValue) {
        try {
            switch (strKey) {
                case "auto-resupply" -> bAutoResupply = parseBool(strValue);
                case "shulker-return" -> eShulkerReturn = ShulkerReturn.valueOf(strValue.toUpperCase());
                case "return-empty-shulkers" -> bReturnEmptyShulkers = parseBool(strValue);
                case "return-shulkers-at-end" -> bReturnShulkersAtEnd = parseBool(strValue);
                case "click-delay" -> iClickDelayTicks = Math.max(1, Integer.parseInt(strValue));
                case "reserved-slots" -> iReservedSlots = Math.max(1, Integer.parseInt(strValue));
                case "max-stacks-per-item" -> iMaxStacksPerItem = Math.max(1, Integer.parseInt(strValue));
                case "stuck-seconds" -> iStuckSeconds = Math.max(5, Integer.parseInt(strValue));
                case "max-failures" -> iMaxFailures = Math.max(0, Integer.parseInt(strValue));
                case "printer" -> bPrinter = parseBool(strValue);
                case "printer-delay" -> iPrinterDelayTicks = Math.max(0, Integer.parseInt(strValue));
                case "printer-blocks-per-tick" -> iPrinterBlocksPerTick = Math.clamp(Integer.parseInt(strValue), 1, 100);
                case "printer-range" -> dPrinterRange = Math.clamp(Double.parseDouble(strValue), 1.0, 6.0);
                case "printer-sort" -> ePrinterSort = PrinterSort.valueOf(strValue.toUpperCase());
                case "printer-air-place" -> eAirPlace = AirPlace.valueOf(strValue.toUpperCase());
                case "printer-backend" -> ePrinterBackend = PrinterBackend.valueOf(strValue.toUpperCase());
                case "printer-rotate" -> bPrinterRotate = parseBool(strValue);
                case "printer-swing" -> bPrinterSwing = parseBool(strValue);
                case "printer-dirt-as-grass" -> bPrinterDirtAsGrass = parseBool(strValue);
                default -> {
                    return "Unknown setting: " + strKey;
                }
            }
        } catch (IllegalArgumentException e) {
            return "Invalid value for " + strKey + ": " + strValue;
        }
        save();
        return null;
    }

    private static boolean parseBool(String strValue) {
        if (strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("on")) return true;
        if (strValue.equalsIgnoreCase("false") || strValue.equalsIgnoreCase("off")) return false;
        throw new IllegalArgumentException(strValue);
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = s_gson.fromJson(Files.readString(path), JsonObject.class);
            for (String strKey : root.keySet()) {
                String strError = set(strKey, root.get(strKey).getAsString());
                if (strError != null) Log.LOGGER.warn(strError);
            }
        } catch (Exception e) {
            Log.LOGGER.error("Failed to load config {}", path, e);
        }
    }

    public static void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            values().forEach(root::addProperty);
            Files.writeString(path, s_gson.toJson(root));
        } catch (IOException e) {
            Log.LOGGER.error("Failed to save config {}", path, e);
        }
    }
}
