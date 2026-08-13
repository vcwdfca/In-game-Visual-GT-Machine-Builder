package com.cleanroommc.ivgtmb.client.gui;

import com.google.gson.Gson;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable snapshot of a custom machine (id, meta, multiblock structure and
 * letter map). Used to save machines into {@code groovy/IVGTMB} and load them
 * back from the history list.
 */
@SideOnly(Side.CLIENT)
public class MachineData {

    private static final Gson GSON = new Gson();

    public String machineId = "";
    public String machineMeta = "32000";
    public int structX = 3;
    public int structY = 3;
    public int structZ = 3;
    public String[][][] grid; // [z][y][x], each cell a single character
    public Map<String, String> letterMap = new LinkedHashMap<>();
    public String frontOverlay = "";
    public String baseTexture = "";

    public String toJson() {
        return GSON.toJson(this);
    }

    public static MachineData fromJson(String json) {
        return GSON.fromJson(json, MachineData.class);
    }

    public static String[][] charsToStrings(char[][] chars) {
        if (chars == null) {
            return new String[0][0];
        }
        String[][] result = new String[chars.length][];
        for (int i = 0; i < chars.length; i++) {
            result[i] = new String[chars[i].length];
            for (int j = 0; j < chars[i].length; j++) {
                result[i][j] = chars[i][j] == 0 ? "" : String.valueOf(chars[i][j]);
            }
        }
        return result;
    }

    public static char[][] stringsToChars(String[][] strings) {
        if (strings == null) {
            return new char[0][0];
        }
        char[][] result = new char[strings.length][];
        for (int i = 0; i < strings.length; i++) {
            result[i] = new char[strings[i].length];
            for (int j = 0; j < strings[i].length; j++) {
                String s = strings[i][j];
                result[i][j] = (s == null || s.isEmpty()) ? 0 : s.charAt(0);
            }
        }
        return result;
    }

    public static Map<String, String> charsToMap(Map<Character, String> source) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<Character, String> entry : source.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    public static Map<Character, String> stringsToMap(Map<String, String> source) {
        Map<Character, String> map = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (!entry.getKey().isEmpty()) {
                    map.put(entry.getKey().charAt(0), entry.getValue());
                }
            }
        }
        return map;
    }
}
