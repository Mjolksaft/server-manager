package com.example.servermanager.GameServer;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.ErrorEvent;
import com.example.servermanager.dto.GameType;
import com.example.servermanager.dto.GameAction;
import com.example.servermanager.dto.GameActionEvent;
import com.example.servermanager.dto.ModResponse;

import tools.jackson.core.type.TypeReference;

public class TModLoaderServer extends GameServer {

    private final Path modsEnabledPath;

    public TModLoaderServer(long id, int port, String worldName, LogWebSocketHandler logHandler,
                            String enabledModsFile, String worldsDir, String serverDir, String modsEnabledPath,
                            String dataDir) {
        super(id, port, worldName,
                worldsDir + "/" + worldName + ".wld",
                serverDir,
                logHandler, enabledModsFile, dataDir);
        this.modsEnabledPath = Path.of(modsEnabledPath);
        this.type = GameType.TMODLOADER;
    }

    @Override
    protected void onBeforeStart() {
        if (enabledModsFile == null || enabledModsFile.isBlank()) return;
        try {
            Path source = enabledModsFile.contains("/") || enabledModsFile.contains("\\")
                ? Path.of(enabledModsFile)
                : Path.of(dataDir, enabledModsFile);
            if (!Files.exists(source)) {
                System.err.println("[TModLoaderServer] enabledModsFile not found: " + source.toAbsolutePath());
                return;
            }
            Files.createDirectories(modsEnabledPath.getParent());
            Files.copy(source, modsEnabledPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[TModLoaderServer] Copied " + source.toAbsolutePath() + " -> " + modsEnabledPath);
        } catch (Exception e) {
            System.err.println("[TModLoaderServer] Failed to copy enabledModsFile: " + e.getMessage());
        }
    }

    @Override
    public String getExecutableName() {
        return "tModLoaderServer.exe";
    }

    @Override
    public String getWorkingDirectory() {
        return serverDir;
    }

    @Override
    protected String[] buildCommand() {
        return new String[] {
                serverPath + "LaunchUtils/busybox64.exe",
                "bash",
                "start-tModLoaderServer.sh",
                "-nosteam",
                "-world", worldPath,
                "-port", String.valueOf(port)
        };
    }

    public String tpToSpawn(String name) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("spawn %s", name));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.SPAWN, name, null));
            return "spawn command sent";

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "tp to spawn", err.getMessage()));
            throw new RuntimeException("Failed to tp to spawn: " + err.getMessage(), err);
        }
    }

    public String tpToPlayer(String name, String secondName) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("tp %s %s", name, secondName));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.TP, name + " -> " + secondName, null));
            return "tp command sent";

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "tp to player", err.getMessage()));
            throw new RuntimeException("Failed to tp to player: " + err.getMessage(), err);
        }
    }

    public String spawnMob(String npcName, String playerName) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("spawnmob %s %s", npcName, playerName));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.SPAWN_MOB, npcName + " @" + playerName, null));
            return "spawnmob command sent";

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "spawn mob", err.getMessage()));
            throw new RuntimeException("Failed to spawn mob: " + err.getMessage(), err);
        }
    }

    public String killEntity(String name) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("kill %s", name));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.KILL_ENTITY, name, null));
            return "kill command sent";

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "kill entity", err.getMessage()));
            throw new RuntimeException("Failed to kill entity: " + err.getMessage(), err);
        }
    }

    public String giveItem(String playerName, String itemName, Integer amount) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));

            boolean hasPlayer = playerName != null && !playerName.isBlank();
            boolean hasAmount = amount != null && amount > 0;

            String cmd;
            if (hasPlayer) {
                cmd = hasAmount
                    ? String.format("give %s %s %d", playerName, itemName, amount)
                    : String.format("give %s %s", playerName, itemName);
            } else {
                cmd = hasAmount
                    ? String.format("give %s %d", itemName, amount)
                    : String.format("give %s", itemName);
            }

            writer.write(cmd);
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.GIVE, cmd, null));
            return "give command sent";

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "give item", err.getMessage()));
            throw new RuntimeException("Failed to give item: " + err.getMessage(), err);
        }
    }


    public List<ModResponse> queryModList() {
        ensureProcessRunning();
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingModListQuery = future;
        currentModList = Collections.synchronizedList(new ArrayList<>());

        try {
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("modlist");
            writer.newLine();
            writer.flush();
        } catch (Exception err) {
            pendingModListQuery = null;
            throw new RuntimeException("Failed to send modlist command", err);
        }

        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Timeout is fine — return whatever we collected
        }

        pendingModListQuery = null;

        List<String> rawLines;
        synchronized (currentModList) {
            rawLines = new ArrayList<>(currentModList);
        }

        return rawLines.stream()
                .map(raw -> raw.replaceAll("\u001B\\[[0-9;?]*[a-zA-Z]", "").strip())
                .filter(s -> !s.isEmpty() && !s.contains("modlist"))
                .map(s -> s.replaceFirst("^:\\s*", ""))
                .map(ModResponse::new)
                .toList();
    }

    @Override
    public List<ModResponse> getMods() {
        if (enabledModsFile == null || enabledModsFile.isBlank()) {
            broadcast(new GameActionEvent(id, GameAction.MOD_LIST, null, "no mods file configured"));
            return List.of();
        }
        try {
            Path source = enabledModsFile.contains("/") || enabledModsFile.contains("\\")
                ? Path.of(enabledModsFile)
                : Path.of(dataDir, enabledModsFile);

            if (!Files.exists(source)) {
                broadcast(new GameActionEvent(id, GameAction.MOD_LIST, null, "mods file not found"));
                return List.of();
            }

            List<String> modNames = mapper.readValue(source.toFile(), new TypeReference<List<String>>() {});
            List<ModResponse> mods = modNames.stream().map(ModResponse::new).toList();

            broadcast(new GameActionEvent(id, GameAction.MOD_LIST, null, null));
            return mods;

        } catch (Exception e) {
            System.err.println("[TModLoaderServer] Failed to read enabled mods: " + e.getMessage());
            broadcast(new ErrorEvent(id, "mod list", e.getMessage()));
            throw new RuntimeException("Failed to read enabled mods: " + e.getMessage(), e);
        }
    }


}
