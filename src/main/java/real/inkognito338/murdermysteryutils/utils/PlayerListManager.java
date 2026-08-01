package real.inkognito338.murdermysteryutils.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 */

public class PlayerListManager {

    private static final File FRIENDS_FILE = new File("MurderMysteryUtils/friends.txt");
    private static final File IGNORE_FILE  = new File("MurderMysteryUtils/ignore.txt");

    private static final Set<String> friends = new HashSet<>();
    private static final Set<String> ignored = new HashSet<>();

    static {
        loadList(FRIENDS_FILE, friends);
        loadList(IGNORE_FILE, ignored);
    }

    // ── Friends ───────────────────────────────────────────────────────────────

    public static void addFriend(String name) {
        if (add(friends, name)) saveList(FRIENDS_FILE, friends);
    }

    public static void removeFriend(String name) {
        if (remove(friends, name)) saveList(FRIENDS_FILE, friends);
    }

    public static void clearFriends() {
        friends.clear();
        saveList(FRIENDS_FILE, friends);
    }

    public static boolean isFriend(String name) {
        return contains(friends, name);
    }

    public static Set<String> getFriends() {
        return new HashSet<>(friends);
    }

    public static List<String> getFriendsList() {
        List<String> list = new ArrayList<>(friends);
        Collections.sort(list);
        return list;
    }

    public static int getFriendCount() {
        return friends.size();
    }

    // ── Ignore ────────────────────────────────────────────────────────────────

    public static void addIgnore(String name) {
        if (add(ignored, name)) saveList(IGNORE_FILE, ignored);
    }

    public static void removeIgnore(String name) {
        if (remove(ignored, name)) saveList(IGNORE_FILE, ignored);
    }

    public static void clearIgnored() {
        ignored.clear();
        saveList(IGNORE_FILE, ignored);
    }

    public static boolean isIgnored(String name) {
        return contains(ignored, name);
    }

    public static Set<String> getIgnored() {
        return new HashSet<>(ignored);
    }

    public static List<String> getIgnoreList() {
        List<String> list = new ArrayList<>(ignored);
        Collections.sort(list);
        return list;
    }

    public static int getIgnoreCount() {
        return ignored.size();
    }

    // ── Внутренние хелперы ────────────────────────────────────────────────────

    private static boolean add(Set<String> set, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return set.add(name.trim().toLowerCase());
    }

    private static boolean remove(Set<String> set, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return set.remove(name.trim().toLowerCase());
    }

    private static boolean contains(Set<String> set, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return set.contains(name.trim().toLowerCase());
    }

    private static void saveList(File file, Set<String> set) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                for (String entry : set) {
                    writer.println(entry);
                }
            }
        } catch (IOException e) {
            System.err.println("[PlayerListManager] Failed to save " + file.getName() + ": " + e.getMessage());
        }
    }

    private static void loadList(File file, Set<String> set) {
        set.clear();
        if (!file.exists()) return;

        try {
            List<String> lines = Files.readAllLines(Paths.get(file.getPath()));
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed.toLowerCase());
                }
            }
        } catch (IOException e) {
            System.err.println("[PlayerListManager] Failed to load " + file.getName() + ": " + e.getMessage());
        }
    }
}