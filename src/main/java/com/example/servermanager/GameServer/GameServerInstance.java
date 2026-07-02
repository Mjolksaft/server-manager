package com.example.servermanager.GameServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.ErrorEvent;
import com.example.servermanager.dto.GameAction;
import com.example.servermanager.dto.GameActionEvent;
import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.LogEvent;
import com.example.servermanager.dto.ServerEvent;
import com.example.servermanager.dto.ServerStates;
import com.example.servermanager.dto.StateEvent;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import tools.jackson.databind.ObjectMapper;

public class GameServerInstance {
    private long id;
    private int port;
    private String worldName;
    private String serverPath;
    private String worldPath;
    private ServerStates state = ServerStates.OFFLINE;

    private PtyProcess process;
    private LogWebSocketHandler logHandler;
    private ObjectMapper mapper = new ObjectMapper();

    public GameServerInstance(Long id, int port, String worldName, String worldPath, String serverPath,
            LogWebSocketHandler logHandler) {
        this.id = id;
        this.port = port;
        this.worldName = worldName;
        this.logHandler = logHandler;
        this.worldPath = worldPath;
        this.serverPath = serverPath;
    }

    public void start() {
        try {
            broadcastState(new StateEvent(id, ServerStates.STARTING, "Server is Starting!"), ServerStates.STARTING);

            String[] command = {
                    serverPath + "TerrariaServer.exe",
                    "-world", worldPath,
                    "-port", String.valueOf(port)
            };

            process = new PtyProcessBuilder(command)
                    .setDirectory("D:/steam/steamapps/common/Terraria")
                    .start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Terraria]: " + line);
                        broadcast(new LogEvent(id, line));

                        if (line.contains("Server started")) {
                            broadcastState(new StateEvent(id, ServerStates.RUNNING, "Server has started!"),
                                    ServerStates.RUNNING);
                        }
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

    public void stop() {
        try {
            broadcastState(new StateEvent(id, ServerStates.STOPPING, "Server is stopping"), ServerStates.STOPPING);

            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("exit");
            writer.newLine();
            writer.flush();

            broadcastState(new StateEvent(id, ServerStates.OFFLINE, "Server is stopping"), ServerStates.OFFLINE);

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().getMessage(), err.getMessage()), ServerStates.CRASHED);
        }
    }

    public void broadcast(ServerEvent event) {
        String message = mapper.writeValueAsString(event);
        logHandler.broadcast(message);
    }

    public void broadcastState(ServerEvent event, ServerStates newState) {
        state = newState;
        String message = mapper.writeValueAsString(event);
        logHandler.broadcast(message);
    }

    public void kick(KickRequest request) {
        try {
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("kick %s", request.name()));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.KICK, request.name(), request.details()));

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().getMessage(), err.getMessage()), ServerStates.CRASHED);
        }
    }

    public void ban(KickRequest request) {
        try {
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("ban %s", request.name()));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.BAN, request.name(), request.details()));

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().getMessage(), err.getMessage()), ServerStates.CRASHED);
        }
    }

    public List<KickResponse> bans() {
        List<KickResponse> response = new ArrayList<>();
        try {
            Path banlist = Path.of(serverPath + "banlist.txt");

            List<String> lines = Files.readAllLines(banlist);

            for (int i = 0; i < lines.size(); i += 2) {
                String name = lines.get(i).replace("//", "");
                String ip = lines.get(i + 1);
                response.add(new KickResponse(name, ip));
            }

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().getMessage(), err.getMessage()), ServerStates.CRASHED);
        }

        return response;
    }

    public void unban(KickRequest request) {
        try {
            Path banlist = Path.of(serverPath + "banlist.txt");

            List<String> lines = Files.readAllLines(banlist);
            List<String> updated = new ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).equals("//" + request.name())) {
                    i++; // skip the IP line too

                } else {
                    updated.add(lines.get(i));
                }
            }
            Files.write(banlist, updated);

            broadcast(new GameActionEvent(id, GameAction.UNBAN, request.name(), request.details()));

        } catch (Exception err) {
            System.err.println(err);
            broadcastState(new ErrorEvent(id, err.getCause().getMessage(), err.getMessage()), ServerStates.CRASHED);
        }
    }

    public ServerStates getState() {
        return state;
    }

    public int getPort() {
        return port;
    }

    public String getWorldPath() {
        return worldPath;
    }

    public String getServerPath() {
        return serverPath;
    }

    public String getWorldName() {
        return worldName;
    }

    public Long getId() {
        return id;
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
}
