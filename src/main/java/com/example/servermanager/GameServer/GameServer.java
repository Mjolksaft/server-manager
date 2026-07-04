package com.example.servermanager.GameServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.ErrorEvent;
import com.example.servermanager.dto.GameAction;
import com.example.servermanager.dto.GameActionEvent;
import com.example.servermanager.dto.ModResponse;
import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.LogEvent;
import com.example.servermanager.dto.PasswordRequest;
import com.example.servermanager.dto.PlayerResponse;
import com.example.servermanager.dto.ServerEvent;
import com.example.servermanager.dto.ServerStates;
import com.example.servermanager.dto.StateEvent;
import com.example.servermanager.dto.SayRequest;
import com.example.servermanager.dto.TimeRequest;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import tools.jackson.databind.ObjectMapper;

public abstract class GameServer {
    protected long id;
    protected int port;
    protected String worldName;
    protected String serverPath;
    protected String worldPath;
    protected ServerStates state = ServerStates.OFFLINE;

    protected PtyProcess process;
    protected LogWebSocketHandler logHandler;
    protected ObjectMapper mapper = new ObjectMapper();

    protected final List<String> connectedPlayers = Collections.synchronizedList(new ArrayList<>());
    protected volatile CompletableFuture<String> pendingTimeQuery;
    protected volatile CompletableFuture<String> pendingSeedQuery;
    protected CompletableFuture<Void> pendingModListQuery;
    protected List<String> currentModList;

    public GameServer(long id, int port, String worldName, String worldPath, String serverPath,
            LogWebSocketHandler logHandler) {
        this.id = id;
        this.port = port;
        this.worldName = worldName;
        this.logHandler = logHandler;
        this.worldPath = worldPath;
        this.serverPath = serverPath;
    }

    public abstract String getExecutableName();

    public abstract String getWorkingDirectory();

    protected String[] buildCommand() {
        return new String[] {
                serverPath + getExecutableName(),
                "-world", worldPath,
                "-port", String.valueOf(port)
        };
    }

    public void start() {
        try {
            broadcastState(new StateEvent(id, ServerStates.STARTING, "Server is Starting!"), ServerStates.STARTING);

            String[] command = buildCommand();

            process = new PtyProcessBuilder(command)
                    .setDirectory(getWorkingDirectory())
                    .start();

            onProcessStarted();

            connectedPlayers.clear();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[" + getExecutableName() + "]: " + line);
                        broadcast(new LogEvent(id, line));

                        if (line.contains(getStartupDetectionString())) {
                            broadcastState(new StateEvent(id, ServerStates.RUNNING, "Server has started!"),
                                    ServerStates.RUNNING);
                        }

                        trackPlayer(line);
                        checkQueryResponse(line);
                    }
                } catch (Exception err) {
                    System.out.println("Server output stream closed.");
                }
            }).start();

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().toString(), err.getMessage()), ServerStates.CRASHED);
        }
    }

    protected void onProcessStarted() {
    }

    protected String getStartupDetectionString() {
        return "Server started";
    }

    public void stop() {
        try {
            broadcastState(new StateEvent(id, ServerStates.STOPPING, "Server is stopping"), ServerStates.STOPPING);

            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("exit");
            writer.newLine();
            writer.flush();

            connectedPlayers.clear();

            broadcastState(new StateEvent(id, ServerStates.OFFLINE, "Server is offline"), ServerStates.OFFLINE);

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().getMessage(), err.getMessage()), ServerStates.CRASHED);
        }
    }

    public void save() {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("save");
            writer.newLine();
            writer.flush();

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Save failed", err.getMessage()));
            throw new RuntimeException("Failed to save world: " + err.getMessage(), err);
        }
    }

    public void say(SayRequest request) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("say %s", request.message()));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.SAY, request.message(), null));

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Say failed", err.getMessage()));
            throw new RuntimeException("Failed to send message: " + err.getMessage(), err);
        }
    }

    public KickResponse kick(KickRequest request) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("kick %s", request.name()));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.KICK, request.name(), request.details()));
            return new KickResponse(request.name(), request.details() != null ? request.details() : "Kicked");

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Kick failed", err.getMessage()));
            throw new RuntimeException("Failed to kick player '" + request.name() + "': " + err.getMessage(), err);
        }
    }

    public KickResponse ban(KickRequest request) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("ban %s", request.name()));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.BAN, request.name(), request.details()));
            return new KickResponse(request.name(), request.details() != null ? request.details() : "Banned");

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Ban failed", err.getMessage()));
            throw new RuntimeException("Failed to ban player '" + request.name() + "': " + err.getMessage(), err);
        }
    }

    public List<KickResponse> bans() {
        List<KickResponse> response = new ArrayList<>();
        try {
            Path banlist = Path.of(serverPath + "banlist.txt");

            if (!Files.exists(banlist)) {
                return response;
            }

            List<String> lines = Files.readAllLines(banlist);

            for (int i = 0; i < lines.size(); i += 2) {
                String name = lines.get(i).replace("//", "");
                String ip = lines.get(i + 1);
                response.add(new KickResponse(name, ip));
            }

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Failed to list bans", err.getMessage()));
            throw new RuntimeException("Failed to list bans: " + err.getMessage(), err);
        }

        return response;
    }

    public KickResponse unban(KickRequest request) {
        try {
            Path banlist = Path.of(serverPath + "banlist.txt");

            if (!Files.exists(banlist)) {
                throw new IllegalStateException("banlist.txt not found at " + banlist.toString());
            }

            List<String> lines = Files.readAllLines(banlist);
            List<String> updated = new ArrayList<>();
            boolean found = false;

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).equals("//" + request.name())) {
                    i++;
                    found = true;
                } else {
                    updated.add(lines.get(i));
                }
            }

            if (!found) {
                throw new IllegalStateException("Player '" + request.name() + "' not found in banlist");
            }

            Files.write(banlist, updated);

            broadcast(new GameActionEvent(id, GameAction.UNBAN, request.name(), request.details()));
            return new KickResponse(request.name(), request.details() != null ? request.details() : "Unbanned");

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Unban failed", err.getMessage()));
            throw new RuntimeException("Failed to unban player '" + request.name() + "': " + err.getMessage(), err);
        }
    }

    public void settle() {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("settle");
            writer.newLine();
            writer.flush();

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Settle failed", err.getMessage()));
            throw new RuntimeException("Failed to settle water: " + err.getMessage(), err);
        }
    }



    public void setTime(TimeRequest request) {
        try {
            String time = request.time() != null ? request.time().toLowerCase() : "";
            if (!time.matches("dawn|noon|dusk|midnight")) {
                throw new IllegalArgumentException(
                        "Invalid time '" + request.time() + "'. Must be one of: dawn, noon, dusk, midnight");
            }

            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(time);
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.TIME_CHANGE, time, null));

        } catch (IllegalArgumentException err) {
            throw err;
        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Time change failed", err.getMessage()));
            throw new RuntimeException("Failed to change time to '" + request.time() + "': " + err.getMessage(), err);
        }
    }

    public void setPassword(PasswordRequest request) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));

            String pass = request.password();
            if (pass != null && !pass.isEmpty()) {
                writer.write(String.format("password %s", pass));
            } else {
                writer.write("password");
            }
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.PASSWORD_CHANGE, pass, null));

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Password change failed", err.getMessage()));
            throw new RuntimeException("Failed to change password: " + err.getMessage(), err);
        }
    }

    public String queryTime() {
        ensureProcessRunning();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingTimeQuery = future;

        try {
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("time");
            writer.newLine();
            writer.flush();
        } catch (Exception err) {
            pendingTimeQuery = null;
            throw new RuntimeException("Failed to send time command: " + err.getMessage(), err);
        }

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception err) {
            pendingTimeQuery = null;
            throw new RuntimeException("Timed out waiting for time response", err);
        }
    }

    public String querySeed() {
        ensureProcessRunning();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingSeedQuery = future;

        try {
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("seed");
            writer.newLine();
            writer.flush();
        } catch (Exception err) {
            pendingSeedQuery = null;
            throw new RuntimeException("Failed to send seed command: " + err.getMessage(), err);
        }

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception err) {
            pendingSeedQuery = null;
            throw new RuntimeException("Timed out waiting for seed response", err);
        }
    }

    public List<PlayerResponse> getPlayers() {
        synchronized (connectedPlayers) {
            return connectedPlayers.stream().map(PlayerResponse::new).toList();
        }
    }

    public long getId() {
        return id;
    }

    public int getPort() {
        return port;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getServerPath() {
        return serverPath;
    }

    public String getWorldPath() {
        return worldPath;
    }

    public ServerStates getState() {
        return state;
    }

    public void setPort(int newPort) {
        this.port = newPort;
    }

    public void setWorldPath(String newWorldPath) {
        this.worldPath = newWorldPath;
    }

    public void setServerPath(String newServerPath) {
        this.serverPath = newServerPath;
    }

    public void setWorldName(String newWorldName) {
        this.worldName = newWorldName;
    }

    public void broadcast(ServerEvent event) {
        try {
            String message = mapper.writeValueAsString(event);
            logHandler.broadcast(message);
        } catch (Exception err) {
            System.err.println("Failed to broadcast event: " + err.getMessage());
        }
    }

    public void broadcastState(ServerEvent event, ServerStates newState) {
        state = newState;
        try {
            String message = mapper.writeValueAsString(event);
            logHandler.broadcast(message);
        } catch (Exception err) {
            System.err.println("Failed to broadcast state event: " + err.getMessage());
        }
    }

    private void trackPlayer(String line) {
        if (line.contains("has joined")) {
            int idx = line.indexOf(" has joined");
            String name = line.substring(0, idx).trim();
            connectedPlayers.add(name);
            System.out.println("[Player Tracker] " + name + " joined");

        } else if (line.contains("has left")) {
            int idx = line.indexOf(" has left");
            String name = line.substring(0, idx).trim();
            connectedPlayers.remove(name);
            System.out.println("[Player Tracker] " + name + " left");

        } else if (line.contains("was booted")) {
            int idx = line.indexOf(" was booted");
            String target = line.substring(0, idx).trim();
            connectedPlayers.remove(target);
            System.out.println("[Player Tracker] " + target + " was booted");
        }
    }

    private void checkQueryResponse(String line) {
        CompletableFuture<String> timeFuture = pendingTimeQuery;
        if (timeFuture != null && !timeFuture.isDone()) {
            if (line.startsWith("Time:")) {
                pendingTimeQuery = null;
                timeFuture.complete(line.substring("Time:".length()).trim());
            }
        }

        CompletableFuture<String> seedFuture = pendingSeedQuery;
        if (seedFuture != null && !seedFuture.isDone()) {
            String prefix = "World seed is:";
            int idx = line.indexOf(prefix);
            if (idx >= 0) {
                pendingSeedQuery = null;
                seedFuture.complete(line.substring(idx + prefix.length()).trim());
            }
        }

        if (pendingModListQuery != null && !pendingModListQuery.isDone()) {
            String cleaned = line.replaceAll("\u001B\\[[0-9;?]*[a-zA-Z]", "").strip();
            if (cleaned.equals(":")) {
                pendingModListQuery.complete(null);
                return;
            }
            currentModList.add(line);
        }
    }

    public List<ModResponse> getMods() {
        throw new UnsupportedOperationException("Mod list is only supported on tModLoader servers");
    }

    protected void ensureProcessRunning() {
        if (process == null) {
            throw new IllegalStateException("Server process is not running. Start the server first.");
        }
    }
}
