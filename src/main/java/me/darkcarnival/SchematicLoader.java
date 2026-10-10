package me.darkcarnival;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

/**
 * Читает схематики WorldEdit формата .schem (Sponge v2 и v3) без зависимости от WorldEdit.
 */
public final class SchematicLoader {

    public static final class Schem {
        public int width;
        public int height;
        public int length;
        /** Индекс блока: x + z * width + y * width * length. Может содержать null. */
        public BlockData[] data;
    }

    private SchematicLoader() {
    }

    @SuppressWarnings("unchecked")
    public static Schem load(File file) throws IOException {
        Map<String, Object> root;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            int type = in.readByte();
            if (type != 10) {
                throw new IOException("Это не NBT-файл");
            }
            in.readUTF();
            root = (Map<String, Object>) readPayload(in, 10);
        }

        Map<String, Object> base = root;
        if (root.get("Schematic") instanceof Map) {
            base = (Map<String, Object>) root.get("Schematic"); // формат v3
        }

        Schem s = new Schem();
        s.width = num(base.get("Width"));
        s.height = num(base.get("Height"));
        s.length = num(base.get("Length"));
        if (s.width <= 0 || s.height <= 0 || s.length <= 0) {
            throw new IOException("Неверные размеры схематики");
        }

        Map<String, Object> paletteMap;
        byte[] raw;
        if (base.get("Blocks") instanceof Map) { // v3
            Map<String, Object> blocks = (Map<String, Object>) base.get("Blocks");
            paletteMap = (Map<String, Object>) blocks.get("Palette");
            raw = (byte[]) blocks.get("Data");
        } else { // v2
            paletteMap = (Map<String, Object>) base.get("Palette");
            raw = (byte[]) base.get("BlockData");
        }
        if (paletteMap == null || raw == null) {
            throw new IOException("В файле нет палитры блоков (нужен формат .schem от WorldEdit 7+)");
        }

        Map<Integer, BlockData> palette = new HashMap<>();
        for (Map.Entry<String, Object> e : paletteMap.entrySet()) {
            try {
                palette.put(num(e.getValue()), Bukkit.createBlockData(e.getKey()));
            } catch (Exception ex) {
                // неизвестный блок — пропускаем
            }
        }

        int total = s.width * s.height * s.length;
        s.data = new BlockData[total];
        int idx = 0;
        int i = 0;
        while (i < raw.length && idx < total) {
            int value = 0;
            int shift = 0;
            while (true) {
                int b = raw[i++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            s.data[idx++] = palette.get(value);
        }
        return s;
    }

    private static int num(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : 0;
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                return a;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int t = in.readByte();
                int n = in.readInt();
                List<Object> l = new ArrayList<>();
                for (int k = 0; k < n; k++) {
                    l.add(readPayload(in, t));
                }
                return l;
            }
            case 10: {
                Map<String, Object> m = new HashMap<>();
                while (true) {
                    int t = in.readByte();
                    if (t == 0) {
                        break;
                    }
                    String name = in.readUTF();
                    m.put(name, readPayload(in, t));
                }
                return m;
            }
            case 11: {
                int[] a = new int[in.readInt()];
                for (int k = 0; k < a.length; k++) {
                    a[k] = in.readInt();
                }
                return a;
            }
            case 12: {
                long[] a = new long[in.readInt()];
                for (int k = 0; k < a.length; k++) {
                    a[k] = in.readLong();
                }
                return a;
            }
            default:
                throw new IOException("Неизвестный тег NBT: " + type);
        }
    }
}
