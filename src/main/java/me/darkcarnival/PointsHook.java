package me.darkcarnival;

import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Доступ к PlayerPoints через рефлексию, чтобы плагин собирался и работал
 * без жёсткой зависимости от PlayerPoints.
 */
public final class PointsHook {
    private final Object api;
    private final Method look;
    private final Method take;
    private final Method give;

    private PointsHook(Object api, Method look, Method take, Method give) {
        this.api = api;
        this.look = look;
        this.take = take;
        this.give = give;
    }

    public static PointsHook create() {
        Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pp == null || !pp.isEnabled()) {
            return null;
        }
        try {
            Object api = pp.getClass().getMethod("getAPI").invoke(pp);
            if (api == null) {
                return null;
            }
            Class<?> ac = api.getClass();
            return new PointsHook(
                    api,
                    ac.getMethod("look", UUID.class),
                    ac.getMethod("take", UUID.class, int.class),
                    ac.getMethod("give", UUID.class, int.class));
        } catch (Exception ex) {
            return null;
        }
    }

    private static int clamp(long amount) {
        return (int) Math.max(0L, Math.min(amount, Integer.MAX_VALUE));
    }

    public long look(UUID id) {
        try {
            Object r = look.invoke(api, id);
            return (r instanceof Number) ? ((Number) r).longValue() : 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }

    public boolean take(UUID id, long amount) {
        try {
            return Boolean.TRUE.equals(take.invoke(api, id, clamp(amount)));
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean give(UUID id, long amount) {
        try {
            return Boolean.TRUE.equals(give.invoke(api, id, clamp(amount)));
        } catch (Exception ex) {
            return false;
        }
    }
}
