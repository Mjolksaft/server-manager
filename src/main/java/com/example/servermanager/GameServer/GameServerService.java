package com.example.servermanager.GameServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.SayRequest;
import com.example.servermanager.dto.ServerRequest;
import com.example.servermanager.dto.ServerResponse;
import com.example.servermanager.dto.ServerStates;
import com.example.servermanager.dto.PlayerResponse;
import com.example.servermanager.dto.PasswordRequest;
import com.example.servermanager.dto.TimeRequest;
import com.pty4j.PtyProcess;

@Service
public class GameServerService {
    @Autowired
    private LogWebSocketHandler logHandler;

    private PtyProcess process;

    private Map<Long, GameServerInstance> serverMap = new HashMap<>();
    private AtomicLong idCounter = new AtomicLong(0);

    public List<ServerResponse> getServers() {
        return serverMap.values().stream().map(server -> new ServerResponse(
                server.getId(),
                server.getPort(),
                server.getWorldName(),
                server.getServerPath(),
                server.getState())).toList();
    }

    public ServerResponse getServer(long id) {
        GameServerInstance server = findServerOrThrow(id);
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerResponse create(ServerRequest request) {
        Long id = idCounter.getAndIncrement();
        System.out.println(request.worldName());
        GameServerInstance newInstance = new GameServerInstance(id, request.port(), request.worldName(),
                String.format("C:/Users/kalla/Documents/My Games/Terraria/Worlds/%s.wld", request.worldName()),
                "D:/steam/steamapps/common/Terraria/", logHandler);

        serverMap.put(id, newInstance);
        return new ServerResponse(newInstance.getId(), newInstance.getPort(), newInstance.getWorldName(),
                newInstance.getServerPath(), newInstance.getState());
    }

    public ServerResponse start(long id) {
        GameServerInstance server = findServerOrThrow(id);
        server.start();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerResponse save(long id) {
        GameServerInstance server = findServerOrThrow(id);
        server.save();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerResponse stop(long id) {
        GameServerInstance server = findServerOrThrow(id);
        server.stop();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerStates getState(Long id) {
        GameServerInstance server = findServerOrThrow(id);
        return server.getState();
    }

    public KickResponse kick(long id, KickRequest request) {
        GameServerInstance server = findServerOrThrow(id);
        return server.kick(request);
    }

    public KickResponse ban(long id, KickRequest request) {
        GameServerInstance server = findServerOrThrow(id);
        return server.ban(request);
    }

    public List<KickResponse> bans(long id) {
        GameServerInstance server = findServerOrThrow(id);
        return server.bans();
    }

    public KickResponse unban(long id, KickRequest request) {
        GameServerInstance server = findServerOrThrow(id);
        return server.unban(request);
    }

    public void say(long id, SayRequest request) {
        GameServerInstance server = findServerOrThrow(id);
        server.say(request);
    }


    public void setTime(long id, TimeRequest request) {
        GameServerInstance server = findServerOrThrow(id);
        server.setTime(request);
    }

    public void setPassword(long id, PasswordRequest request) {
        GameServerInstance server = findServerOrThrow(id);
        server.setPassword(request);
    }

    public List<PlayerResponse> getPlayers(long id) {
        GameServerInstance server = findServerOrThrow(id);
        return server.getPlayers();
    }

    public void settle(long id) {
        GameServerInstance server = findServerOrThrow(id);
        server.settle();
    }

    public String queryTime(long id) {
        GameServerInstance server = findServerOrThrow(id);
        return server.queryTime();
    }

    public String querySeed(long id) {
        GameServerInstance server = findServerOrThrow(id);
        return server.querySeed();
    }

    private GameServerInstance findServerOrThrow(long id) {
        GameServerInstance server = serverMap.get(id);
        if (server == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Server with ID " + id + " not found");
        }
        return server;
    }
}
