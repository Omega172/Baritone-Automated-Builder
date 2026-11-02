package xyz.omegaware.builder.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("BaritoneAutomatedBuilder");
    private static final String s_szPrefix = "[Builder] ";

    private Log() {}

    public static void chat(String szMessage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s_szPrefix + szMessage));
        LOGGER.info(szMessage);
    }
}
