package com.flowcity.core.protocol;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protocol spike, option A: checks whether this Bukkit plugin can reach classes of the
 * FlowCityForge mod on the same Arclight server. Purely diagnostic, read-only, and only
 * JDK types cross the boundary. The result decides the plugin-to-mod architecture.
 */
public final class ForgeBridgeProbe {

    public static final String BRIDGE_CLASS = "com.flowcity.forge.bridge.ServerBridge";

    private final boolean reachable;
    private final String summary;

    private ForgeBridgeProbe(boolean reachable, String summary) {
        this.reachable = reachable;
        this.summary = summary;
    }

    public static ForgeBridgeProbe run(ClassLoader pluginLoader) {
        Map<String, ClassLoader> loaders = new LinkedHashMap<>();
        loaders.put("plugin", pluginLoader);
        loaders.put("context", Thread.currentThread().getContextClassLoader());
        loaders.put("bukkit", org.bukkit.Bukkit.class.getClassLoader());
        loaders.put("system", ClassLoader.getSystemClassLoader());

        StringBuilder failures = new StringBuilder();
        for (Map.Entry<String, ClassLoader> e : loaders.entrySet()) {
            ClassLoader loader = e.getValue();
            if (loader == null) {
                continue;
            }
            try {
                Class<?> type = Class.forName(BRIDGE_CLASS, true, loader);
                Method ping = type.getMethod("ping");
                Object answer = ping.invoke(null);
                return new ForgeBridgeProbe(true, "reachable via " + e.getKey() + " class loader: " + answer);
            } catch (ClassNotFoundException ex) {
                failures.append(e.getKey()).append("=not found; ");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                failures.append(e.getKey()).append('=').append(ex.getClass().getSimpleName())
                        .append(": ").append(ex.getMessage()).append("; ");
            }
        }
        return new ForgeBridgeProbe(false, "not reachable (" + failures.toString().trim() + ")");
    }

    public boolean reachable() {
        return reachable;
    }

    public String summary() {
        return summary;
    }
}
