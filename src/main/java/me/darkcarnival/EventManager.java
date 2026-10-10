package me.darkcarnival;

import java.io.File;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class EventManager {

    private record Saved(int x, int y, int z, BlockData data) {
    }

    private final DarkCarnival plugin;

    // настройки
    private String prefix = "";
    private String worldName = "grief-1";
    private ZoneId zone = ZoneId.of("Europe/Moscow");
    private String zoneLabel = "МСК";
    private final List<LocalTime> times = new ArrayList<>();
    private List<Integer> announceMinutes = new ArrayList<>();
    private int durationMin = 15;
    private int centerX;
    private int centerZ;
    private int minR = 300;
    private int maxR = 3000;
    private int size = 17;
    private int depth = 15;
    private int height = 2;
    private double cryingChance = 0.35;
    private int pumpkinCount = 25;
    private final long[] rewards = {50, 20, 10};
    private String currencyName = "Лей✞";
    private boolean removeAfter = true;
    private PointsHook points;
    private SchematicLoader.Schem schem;
    private int schemYOffset;
    private boolean pasteAir;
    private boolean protectStructure = true;
    private boolean fillInterior = true;
    private final Set<Material> mineable = new HashSet<>();

    // состояние
    private boolean active;
    private long endMillis;
    private World world;
    private int cx;
    private int cy;
    private int cz;
    private final Set<Long> blocks = new HashSet<>();
    /** Блоки схематики, которые нельзя ломать (не приносят очков). */
    private final Set<Long> protectedBlocks = new HashSet<>();
    private final List<Saved> saved = new ArrayList<>();
    private final Map<UUID, Integer> scores = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private BossBar bar;
    private BukkitTask task;
    private String lastStartKey = "";
    private final Set<String> announced = new HashSet<>();

    public EventManager(DarkCarnival plugin) {
        this.plugin = plugin;
    }

    // ===================== Настройки =====================

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        prefix = color(c.getString("prefix", "&6&lДарк Карнавал &8» &7"));
        worldName = c.getString("world", "grief-1");
        try {
            zone = ZoneId.of(c.getString("timezone", "Europe/Moscow"));
        } catch (Exception ex) {
            zone = ZoneId.of("Europe/Moscow");
        }
        zoneLabel = c.getString("timezone-label", "МСК");
        times.clear();
        for (String s : c.getStringList("start-times")) {
            try {
                times.add(LocalTime.parse(s.trim()));
            } catch (Exception ex) {
                plugin.getLogger().warning("Не понял время запуска '" + s + "' (нужен формат 08:00)");
            }
        }
        Collections.sort(times);
        announceMinutes = c.getIntegerList("announce-before-minutes");
        durationMin = Math.max(1, c.getInt("duration-minutes", 15));
        centerX = c.getInt("location.center-x", 0);
        centerZ = c.getInt("location.center-z", 0);
        minR = Math.max(0, c.getInt("location.min-radius", 300));
        maxR = Math.max(minR + 1, c.getInt("location.max-radius", 3000));
        size = Math.max(5, c.getInt("structure.size", 17));
        if (size % 2 == 0) {
            size++;
        }
        depth = Math.max(3, c.getInt("structure.depth", 15));
        height = Math.max(0, c.getInt("structure.height", 2));
        cryingChance = Math.max(0, Math.min(1, c.getDouble("structure.crying-obsidian-chance", 0.35)));
        pumpkinCount = Math.max(0, c.getInt("structure.pumpkins", 25));

        mineable.clear();
        for (String s : c.getStringList("structure.mineable-blocks")) {
            Material m = Material.matchMaterial(s.trim());
            if (m != null) {
                mineable.add(m);
            } else {
                plugin.getLogger().warning("Неизвестный материал в mineable-blocks: " + s);
            }
        }
        if (mineable.isEmpty()) {
            mineable.add(Material.OBSIDIAN);
            mineable.add(Material.CRYING_OBSIDIAN);
            mineable.add(Material.PUMPKIN);
            mineable.add(Material.CARVED_PUMPKIN);
            mineable.add(Material.JACK_O_LANTERN);
        }
        schem = null;
        schemYOffset = c.getInt("structure.schematic-y-offset", 0);
        pasteAir = c.getBoolean("structure.schematic-paste-air", false);
        protectStructure = c.getBoolean("structure.protect-structure", true);
        fillInterior = c.getBoolean("structure.fill-interior", true);
        if ("schematic".equalsIgnoreCase(c.getString("structure.mode", "generated"))) {
            File dir = new File(plugin.getDataFolder(), "schematics");
            dir.mkdirs();
            File f = new File(dir, c.getString("structure.schematic-file", "carnival.schem"));
            if (!f.isFile()) {
                plugin.getLogger().warning("Файл схематики не найден: " + f.getPath()
                        + ". Использую встроенную шахту.");
            } else {
                try {
                    schem = SchematicLoader.load(f);
                    plugin.getLogger().info("Схематика загружена: " + f.getName() + " (" + schem.width + "x"
                            + schem.height + "x" + schem.length + ")");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Не удалось прочитать схематику " + f.getName() + ": " + ex
                            + ". Использую встроенную шахту.");
                }
            }
        }
        rewards[0] = c.getLong("rewards.1", 50);
        rewards[1] = c.getLong("rewards.2", 20);
        rewards[2] = c.getLong("rewards.3", 10);
        currencyName = c.getString("currency-name", "Лей✞");
        removeAfter = c.getBoolean("remove-structure-after-end", true);
        points = PointsHook.create();
        if (points == null) {
            plugin.getLogger().warning("PlayerPoints не найден: награды выдаваться не будут!");
        }
    }

    public String prefix() {
        return prefix;
    }

    public boolean isActive() {
        return active;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // ===================== Расписание =====================

    public void startScheduler() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        if (active) {
            cleanup();
        }
    }

    private ZonedDateTime occurrence(LocalTime t, ZonedDateTime now, int dayOffset) {
        return now.toLocalDate().plusDays(dayOffset).atTime(t).atZone(now.getZone());
    }

    private ZonedDateTime nextStart(ZonedDateTime now) {
        ZonedDateTime best = null;
        for (LocalTime t : times) {
            for (int off = 0; off <= 1; off++) {
                ZonedDateTime z = occurrence(t, now, off);
                if (z.isAfter(now) && (best == null || z.isBefore(best))) {
                    best = z;
                }
            }
        }
        return best;
    }

    private ZonedDateTime lastStart(ZonedDateTime now) {
        ZonedDateTime best = null;
        for (LocalTime t : times) {
            for (int off = -1; off <= 0; off++) {
                ZonedDateTime z = occurrence(t, now, off);
                if (!z.isAfter(now) && (best == null || z.isAfter(best))) {
                    best = z;
                }
            }
        }
        return best;
    }

    private void tick() {
        try {
            if (active) {
                updateBar();
                if (System.currentTimeMillis() >= endMillis) {
                    finish();
                }
                return;
            }
            if (times.isEmpty()) {
                return;
            }
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime last = lastStart(now);
            if (last != null && Duration.between(last, now).getSeconds() < 5
                    && !last.toString().equals(lastStartKey)) {
                lastStartKey = last.toString();
                start();
                return;
            }
            ZonedDateTime next = nextStart(now);
            if (next != null) {
                long sec = Duration.between(now, next).getSeconds();
                for (int m : announceMinutes) {
                    String key = next + ":" + m;
                    if (sec <= m * 60L && sec > m * 60L - 10 && announced.add(key)) {
                        Bukkit.broadcastMessage(prefix + "§6Дарк Карнавал §7начнётся через §e" + m
                                + " мин§7! Награды: §6" + rewards[0] + "§7/§6" + rewards[1] + "§7/§6" + rewards[2]
                                + " " + currencyName + "§7. Подробнее: §e/event");
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка в тике ивента: " + ex);
        }
    }

    // ===================== Запуск и окончание =====================

    public boolean start() {
        if (active) {
            return false;
        }
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            plugin.getLogger().warning("Мир '" + worldName + "' не найден!");
            return false;
        }
        int[] loc = findLocation(w);
        if (loc == null) {
            plugin.getLogger().warning("Не удалось найти подходящее место для шахты.");
            return false;
        }
        world = w;
        cx = loc[0];
        cy = loc[1];
        cz = loc[2];
        scores.clear();
        names.clear();
        announced.clear();
        build();

        active = true;
        endMillis = System.currentTimeMillis() + durationMin * 60_000L;
        bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
        updateBar();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(prefix + "§6§lДАРК КАРНАВАЛ НАЧАЛСЯ!");
        Bukkit.broadcastMessage(prefix + "§7В мире §e" + worldName + " §7появилась обсидиановая шахта с тыквами.");
        Bukkit.broadcastMessage(prefix + "§7Координаты: §eX " + cx + " Y " + cy + " Z " + cz);
        Bukkit.broadcastMessage(prefix + "§7Копай больше всех! Награды: §6" + rewards[0] + "§7/§6" + rewards[1]
                + "§7/§6" + rewards[2] + " " + currencyName + " §7за топ-3. Идёт §e" + durationMin + " мин§7.");
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§6§lДарк Карнавал", "§7Шахта появилась в мире " + worldName, 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1f);
        }
        return true;
    }

    /** Остановка без наград (командой админа). */
    public void stop() {
        if (!active) {
            return;
        }
        Bukkit.broadcastMessage(prefix + "§eДарк Карнавал остановлен администратором.");
        cleanup();
    }

    private void finish() {
        List<Map.Entry<UUID, Integer>> top = sortedTop();
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(prefix + "§6§lДАРК КАРНАВАЛ ЗАВЕРШЁН!");
        if (top.isEmpty()) {
            Bukkit.broadcastMessage(prefix + "§7Никто ничего не накопал. Награды не выдаются.");
        } else {
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                UUID id = top.get(i).getKey();
                int sc = top.get(i).getValue();
                String name = names.getOrDefault(id, "???");
                Bukkit.broadcastMessage(prefix + "§e" + (i + 1) + " место: §f" + name + " §7— §a" + sc
                        + " очк. §7(+§6" + rewards[i] + " " + currencyName + "§7)");
                giveReward(id, name, i);
            }
        }
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        cleanup();
    }

    private void giveReward(UUID id, String name, int place) {
        long amount = rewards[place];
        if (amount <= 0) {
            return;
        }
        if (points == null) {
            plugin.getLogger().warning("Не выдана награда " + amount + " игроку " + name + ": нет PlayerPoints");
            return;
        }
        boolean ok = points.give(id, amount);
        if (!ok) {
            plugin.getLogger().warning("Не удалось выдать " + amount + " игроку " + name);
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.sendMessage(prefix + "§aТы занял §e" + (place + 1) + " место §aи получил §6" + amount + " "
                    + currencyName + "§a!");
        }
    }

    private void cleanup() {
        if (removeAfter) {
            for (Saved s : saved) {
                world.getBlockAt(s.x(), s.y(), s.z()).setBlockData(s.data(), false);
            }
        }
        saved.clear();
        blocks.clear();
        protectedBlocks.clear();
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        active = false;
        world = null;
    }

    // ===================== Место и шахта =====================

    private int[] findLocation(World w) {
        Random r = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = minR + r.nextDouble() * (maxR - minR);
            int x = centerX + (int) (Math.cos(ang) * dist);
            int z = centerZ + (int) (Math.sin(ang) * dist);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) {
                continue;
            }
            boolean bad = false;
            int rr = locRadius();
            int[][] pts = {{0, 0}, {rr, 0}, {-rr, 0}, {0, rr}, {0, -rr}};
            int y = 0;
            for (int[] d : pts) {
                int yy = w.getHighestBlockYAt(x + d[0], z + d[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Material m = w.getBlockAt(x + d[0], yy, z + d[1]).getType();
                if (m == Material.WATER || m == Material.LAVA || m == Material.ICE || m == Material.BLUE_ICE
                        || m == Material.PACKED_ICE || m == Material.BEDROCK) {
                    bad = true;
                    break;
                }
                if (d[0] == 0 && d[1] == 0) {
                    y = yy;
                }
            }
            if (bad) {
                continue;
            }
            return new int[]{x, y + 1, z};
        }
        return null;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    private static long key(Block b) {
        return key(b.getX(), b.getY(), b.getZ());
    }

    private int locRadius() {
        if (schem != null) {
            return Math.max(schem.width, schem.length) / 2 + 1;
        }
        return size / 2 + 1;
    }

    private void build() {
        blocks.clear();
        protectedBlocks.clear();
        saved.clear();
        if (schem != null) {
            buildSchematic();
        } else {
            buildGenerated();
        }
    }

    private void buildSchematic() {
        int ox = cx - schem.width / 2;
        int oz = cz - schem.length / 2;
        int oy = cy + schemYOffset;
        boolean[] interior = fillInterior ? findInterior() : null;
        for (int y = 0; y < schem.height; y++) {
            for (int z = 0; z < schem.length; z++) {
                for (int x = 0; x < schem.width; x++) {
                    BlockData d = schem.data[x + z * schem.width + y * schem.width * schem.length];
                    if (d == null) {
                        continue;
                    }
                    Material m = d.getMaterial();
                    if (interior != null && interior[x + z * schem.width + y * schem.width * schem.length]) {
                        continue; // внутренность заполним отдельно
                    }
                    if (m.isAir() && !pasteAir) {
                        continue;
                    }
                    int wx = ox + x;
                    int wy = oy + y;
                    int wz = oz + z;
                    if (wy < world.getMinHeight() || wy >= world.getMaxHeight()) {
                        continue;
                    }
                    Block b = world.getBlockAt(wx, wy, wz);
                    saved.add(new Saved(wx, wy, wz, b.getBlockData()));
                    b.setBlockData(d, false);
                    if (mineable.contains(m)) {
                        blocks.add(key(wx, wy, wz));
                    } else if (protectStructure && !m.isAir()) {
                        protectedBlocks.add(key(wx, wy, wz));
                    }
                }
            }
        }
        if (interior != null) {
            fillSchematicInterior(interior, ox, oy, oz);
        }
    }

    /**
     * Внутренность схематики: воздух, у которого в том же ряду по X и по Z
     * есть твёрдый блок с обеих сторон (стены). Открытый верх при этом не мешает.
     */
    private boolean[] findInterior() {
        int w = schem.width;
        int h = schem.height;
        int l = schem.length;
        boolean[] solid = new boolean[w * h * l];
        boolean[] air = new boolean[w * h * l];
        for (int i = 0; i < solid.length; i++) {
            BlockData d = schem.data[i];
            boolean a = d != null && d.getMaterial().isAir();
            air[i] = a;
            solid[i] = !a;
        }
        boolean[] in = new boolean[solid.length];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int i = x + z * w + y * w * l;
                    if (!air[i]) {
                        continue;
                    }
                    boolean xl = false;
                    boolean xr = false;
                    boolean zl = false;
                    boolean zr = false;
                    for (int k = x - 1; k >= 0 && !xl; k--) {
                        xl = solid[k + z * w + y * w * l];
                    }
                    for (int k = x + 1; k < w && !xr; k++) {
                        xr = solid[k + z * w + y * w * l];
                    }
                    for (int k = z - 1; k >= 0 && !zl; k--) {
                        zl = solid[x + k * w + y * w * l];
                    }
                    for (int k = z + 1; k < l && !zr; k++) {
                        zr = solid[x + k * w + y * w * l];
                    }
                    in[i] = xl && xr && zl && zr;
                }
            }
        }
        return in;
    }

    /** Заполняет внутренность схематики обсидианом / плачущим обсидианом и прячет тыквы. */
    private void fillSchematicInterior(boolean[] interior, int ox, int oy, int oz) {
        Random rnd = ThreadLocalRandom.current();
        int w = schem.width;
        int h = schem.height;
        int l = schem.length;
        List<int[]> inner = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    if (!interior[x + z * w + y * w * l]) {
                        continue;
                    }
                    int wx = ox + x;
                    int wy = oy + y;
                    int wz = oz + z;
                    if (wy < world.getMinHeight() || wy >= world.getMaxHeight()) {
                        continue;
                    }
                    Block b = world.getBlockAt(wx, wy, wz);
                    saved.add(new Saved(wx, wy, wz, b.getBlockData()));
                    b.setType(rnd.nextDouble() < cryingChance ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN, false);
                    blocks.add(key(wx, wy, wz));
                    // тыквы прячем только там, где со всех 6 сторон тоже заполнено (не на поверхности)
                    if (isIn(interior, x + 1, y, z) && isIn(interior, x - 1, y, z)
                            && isIn(interior, x, y + 1, z) && isIn(interior, x, y - 1, z)
                            && isIn(interior, x, y, z + 1) && isIn(interior, x, y, z - 1)) {
                        inner.add(new int[]{wx, wy, wz});
                    }
                }
            }
        }
        Collections.shuffle(inner, rnd);
        Material[] pumpkins = {Material.PUMPKIN, Material.CARVED_PUMPKIN, Material.JACK_O_LANTERN};
        for (int i = 0; i < Math.min(pumpkinCount, inner.size()); i++) {
            int[] p = inner.get(i);
            world.getBlockAt(p[0], p[1], p[2]).setType(pumpkins[rnd.nextInt(pumpkins.length)], false);
        }
    }

    private boolean isIn(boolean[] interior, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= schem.width || y >= schem.height || z >= schem.length) {
            return false;
        }
        return interior[x + z * schem.width + y * schem.width * schem.length];
    }

    private void buildGenerated() {
        Random rnd = ThreadLocalRandom.current();
        List<int[]> inner = new ArrayList<>();
        int half = size / 2;
        int topDy = height - 1; // верхний слой относительно cy (при height=0 это -1, т.е. уровень земли)
        if (height == 0) {
            topDy = -1;
        }
        int bottomDy = -depth;

        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = bottomDy; dy <= topDy; dy++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                        continue;
                    }
                    Block b = world.getBlockAt(x, y, z);
                    saved.add(new Saved(x, y, z, b.getBlockData()));
                    b.setType(rnd.nextDouble() < cryingChance ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN, false);
                    blocks.add(key(x, y, z));
                    // тыквы прячем только внутри, не на внешней оболочке
                    if (Math.abs(dx) <= half - 1 && Math.abs(dz) <= half - 1
                            && dy >= bottomDy + 1 && dy <= topDy - 1) {
                        inner.add(new int[]{x, y, z});
                    }
                }
            }
        }

        Collections.shuffle(inner, rnd);
        Material[] pumpkins = {Material.PUMPKIN, Material.CARVED_PUMPKIN, Material.JACK_O_LANTERN};
        for (int i = 0; i < Math.min(pumpkinCount, inner.size()); i++) {
            int[] p = inner.get(i);
            world.getBlockAt(p[0], p[1], p[2]).setType(pumpkins[rnd.nextInt(pumpkins.length)], false);
        }
    }

    // ===================== Игровая логика =====================

    public boolean isEventBlock(Block b) {
        return active && world != null && b.getWorld().equals(world) && blocks.contains(key(b));
    }

    /** Неломаемый блок постройки (не приносит очков). */
    public boolean isProtectedBlock(Block b) {
        return active && world != null && b.getWorld().equals(world) && protectedBlocks.contains(key(b));
    }

    /** Любой блок постройки: очковый или защищённый. */
    public boolean isStructureBlock(Block b) {
        return isEventBlock(b) || isProtectedBlock(b);
    }

    public void onBlockMined(Player p, Block b) {
        Material type = b.getType();
        blocks.remove(key(b));
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        int pts = plugin.getConfig().getInt("points." + type.name(), 1);
        UUID id = p.getUniqueId();
        int total = scores.getOrDefault(id, 0) + pts;
        scores.put(id, total);
        names.put(id, p.getName());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§6+" + pts + " §7| Всего очков: §a" + total));
        if (pts > 1) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
    }

    private List<Map.Entry<UUID, Integer>> sortedTop() {
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(scores.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return l;
    }

    // ===================== Боссбар =====================

    private void updateBar() {
        if (bar == null) {
            return;
        }
        long left = Math.max(0, (endMillis - System.currentTimeMillis()) / 1000);
        double total = durationMin * 60.0;
        bar.setTitle("§6§lДарк Карнавал §7— осталось §e" + mmss(left) + " §7| /event");
        bar.setProgress(Math.max(0.0, Math.min(1.0, left / total)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
        }
    }

    public void showBar(Player p) {
        if (active && bar != null) {
            bar.addPlayer(p);
        }
    }

    // ===================== Тексты для /event =====================

    private static String mmss(long s) {
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String human(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        if (h > 0) {
            return h + " ч " + m + " мин";
        }
        if (m > 0) {
            return m + " мин";
        }
        return totalSeconds + " сек";
    }

    private String scheduleText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) {
                sb.append("§7, ");
            }
            sb.append("§e").append(times.get(i).toString());
        }
        return sb + " §7(" + zoneLabel + ")";
    }

    public List<String> topLines() {
        List<String> out = new ArrayList<>();
        List<Map.Entry<UUID, Integer>> top = sortedTop();
        out.add(prefix + (active ? "§6Текущий топ:" : "§7Ивент сейчас не идёт."));
        if (active) {
            if (top.isEmpty()) {
                out.add("§7  Пока никто ничего не накопал.");
            }
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                out.add("§e  " + (i + 1) + ". §f" + names.getOrDefault(top.get(i).getKey(), "???")
                        + " §7— §a" + top.get(i).getValue() + " очк.");
            }
        }
        return out;
    }

    public List<String> infoLines(UUID viewer) {
        List<String> out = new ArrayList<>();
        out.add("§8§m                    §r");
        out.add("§6§lДарк Карнавал");
        if (active) {
            long left = Math.max(0, (endMillis - System.currentTimeMillis()) / 1000);
            out.add("§aИвент идёт прямо сейчас! §7Осталось: §e" + mmss(left));
            out.add("§7Мир: §e" + worldName + "§7, координаты: §eX " + cx + " Y " + cy + " Z " + cz);
            List<Map.Entry<UUID, Integer>> top = sortedTop();
            out.add("§7Топ сейчас:");
            if (top.isEmpty()) {
                out.add("§7  Пока никто ничего не накопал.");
            }
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                out.add("§e  " + (i + 1) + ". §f" + names.getOrDefault(top.get(i).getKey(), "???")
                        + " §7— §a" + top.get(i).getValue() + " очк.");
            }
            if (viewer != null) {
                out.add("§7Твои очки: §a" + scores.getOrDefault(viewer, 0));
            }
        } else {
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime next = nextStart(now);
            if (next == null) {
                out.add("§7Время запуска не настроено.");
            } else {
                String day = next.toLocalDate().equals(now.toLocalDate()) ? "сегодня" : "завтра";
                long sec = Duration.between(now, next).getSeconds();
                out.add("§7Ближайший ивент: §e" + day + " в " + next.toLocalTime().toString() + " §7("
                        + zoneLabel + "), через §e" + human(sec));
            }
            out.add("§7Расписание: " + scheduleText());
        }
        out.add("§7Задача: добыть больше всех блоков в обсидиановой шахте. В ней спрятаны тыквы (дают больше очков).");
        out.add("§7Награды: §61 место — " + rewards[0] + " " + currencyName + "§7, 2 место — §6" + rewards[1]
                + "§7, 3 место — §6" + rewards[2]);
        out.add("§8§m                    §r");
        return out;
    }
}
