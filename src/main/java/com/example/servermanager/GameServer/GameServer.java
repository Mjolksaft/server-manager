package com.example.servermanager.GameServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
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

    protected volatile CompletableFuture<String> pendingTimeQuery;
    protected volatile CompletableFuture<String> pendingSeedQuery;
    protected CompletableFuture<Void> pendingModListQuery;
    protected List<String> currentModList;
    protected CompletableFuture<Void> pendingPlayingQuery;
    protected List<String> currentPlayerList;
    protected final List<PendingConfirmation> pendingConfirmations = new ArrayList<>();

    private static class PendingConfirmation {
        final List<String> keywords;
        final CompletableFuture<String> future;

        PendingConfirmation(List<String> keywords, CompletableFuture<String> future) {
            this.keywords = keywords;
            this.future = future;
        }
    }

    private String stripAnsi(String line) {
        return line.replaceAll("\u001B\\[[0-9;?]*[a-zA-Z]", "").strip();
    }

    protected CompletableFuture<String> expectConfirmation(String... keywords) {
        CompletableFuture<String> future = new CompletableFuture<>();
        synchronized (pendingConfirmations) {
            pendingConfirmations.add(new PendingConfirmation(Arrays.asList(keywords), future));
        }
        return future;
    }

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

            broadcastState(new StateEvent(id, ServerStates.OFFLINE, "Server is offline"), ServerStates.OFFLINE);

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().getMessage(), err.getMessage()), ServerStates.CRASHED);
        }
    }

    public String save() {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("save");
            writer.newLine();
            writer.flush();

            CompletableFuture<String> cf = expectConfirmation("saved");
            try {
                return cf.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                return "save command sent";
            }

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

            CompletableFuture<String> cf = expectConfirmation(" kicked ");
            try {
                String result = cf.get(3, TimeUnit.SECONDS);
                broadcast(new GameActionEvent(id, GameAction.KICK, request.name(), result));
                return new KickResponse(request.name(), result);
            } catch (Exception e) {
                broadcast(new GameActionEvent(id, GameAction.KICK, request.name(), request.details()));
                return new KickResponse(request.name(), request.details() != null ? request.details() : "Kicked");
            }

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

            CompletableFuture<String> cf = expectConfirmation(" banned ");
            try {
                String result = cf.get(3, TimeUnit.SECONDS);
                broadcast(new GameActionEvent(id, GameAction.BAN, request.name(), result));
                return new KickResponse(request.name(), result);
            } catch (Exception e) {
                broadcast(new GameActionEvent(id, GameAction.BAN, request.name(), request.details()));
                return new KickResponse(request.name(), request.details() != null ? request.details() : "Banned");
            }

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

    public String settle() {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("settle");
            writer.newLine();
            writer.flush();

            CompletableFuture<String> cf = expectConfirmation("Settled");
            try {
                return cf.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                return "settle command sent";
            }

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

    public List<PlayerResponse> queryPlayers() {
        ensureProcessRunning();
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingPlayingQuery = future;
        currentPlayerList = Collections.synchronizedList(new ArrayList<>());

        try {
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("playing");
            writer.newLine();
            writer.flush();
        } catch (Exception err) {
            pendingPlayingQuery = null;
            throw new RuntimeException("Failed to send playing command", err);
        }

        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
        }

        pendingPlayingQuery = null;

        List<String> rawLines;
        synchronized (currentPlayerList) {
            rawLines = new ArrayList<>(currentPlayerList);
        }

        return rawLines.stream()
                .map(this::stripAnsi)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.contains("playing"))
                .filter(s -> !s.strip().equals(":"))
                .filter(s -> !s.matches("\\d+ players? connected\\.?"))
                .map(s -> s.replaceFirst("^:\\s*", "").strip())
                .map(s -> {
                    int ipStart = s.lastIndexOf('(');
                    int ipEnd = s.lastIndexOf(')');
                    if (ipStart >= 0 && ipEnd > ipStart) {
                        String name = s.substring(0, ipStart).strip();
                        String ip = s.substring(ipStart + 1, ipEnd).strip();
                        return new PlayerResponse(name, ip);
                    }
                    return new PlayerResponse(s, null);
                })
                .toList();
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

    private void checkQueryResponse(String line) {
        String cleaned = stripAnsi(line);

        CompletableFuture<String> timeFuture = pendingTimeQuery;
        if (timeFuture != null && !timeFuture.isDone()) {
            if (cleaned.startsWith("Time:")) {
                pendingTimeQuery = null;
                timeFuture.complete(cleaned.substring("Time:".length()).trim());
                return;
            }
        }

        CompletableFuture<String> seedFuture = pendingSeedQuery;
        if (seedFuture != null && !seedFuture.isDone()) {
            String prefix = "World seed is:";
            int idx = cleaned.indexOf(prefix);
            if (idx >= 0) {
                pendingSeedQuery = null;
                seedFuture.complete(cleaned.substring(idx + prefix.length()).trim());
                return;
            }
        }

        if (pendingModListQuery != null && !pendingModListQuery.isDone()) {
            if (cleaned.equals(":")) {
                pendingModListQuery.complete(null);
                return;
            }
            currentModList.add(line);
            return;
        }

        if (pendingPlayingQuery != null && !pendingPlayingQuery.isDone()) {
            if (cleaned.equals(":")) {
                pendingPlayingQuery.complete(null);
                return;
            }
            currentPlayerList.add(line);
            return;
        }

        synchronized (pendingConfirmations) {
            Iterator<PendingConfirmation> it = pendingConfirmations.iterator();
            while (it.hasNext()) {
                PendingConfirmation pc = it.next();
                if (pc.future.isDone()) {
                    it.remove();
                } else {
                    for (String kw : pc.keywords) {
                        if (cleaned.contains(kw)) {
                            pc.future.complete(cleaned);
                            it.remove();
                            return;
                        }
                    }
                }
            }
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
