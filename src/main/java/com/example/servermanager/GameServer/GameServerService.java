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
import org.springframework.stereotype.Service;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.ServerRequest;
import com.example.servermanager.dto.ServerResponse;
import com.example.servermanager.dto.ServerStates;
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
            server.getState()
        )).toList();
    }

    public ServerResponse getServer(long id) {
        GameServerInstance server = serverMap.get(id);
        ServerResponse response = new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(), server.getState());
        return response;
    }

    public ServerResponse create(ServerRequest request) {
        Long id = idCounter.getAndIncrement();
        System.out.println(request.worldName());
        GameServerInstance newInstance = new GameServerInstance(id, request.port(), request.worldName(), String.format("C:/Users/kalla/Documents/My Games/Terraria/Worlds/%s.wld", request.worldName()), "D:/steam/steamapps/common/Terraria/", logHandler);

        serverMap.put(id, newInstance);
        ServerResponse response = new ServerResponse(newInstance.getId(), newInstance.getPort(), newInstance.getWorldName(), newInstance.getServerPath(), newInstance.getState());
        return response;
    }

    public void start(long id) {
        GameServerInstance server = serverMap.get(id);
        server.start();
    }

    public void stop(long id) {
        GameServerInstance server = serverMap.get(id);
        server.stop();
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public ServerStates getState(Long id) {
        GameServerInstance server = serverMap.get(id);
        return server.getState();
    }

    public void kick(long id, KickRequest request) {
        GameServerInstance server = serverMap.get(id);
        server.kick(request);
    }

    public void ban(long id, KickRequest request) {
        GameServerInstance server = serverMap.get(id);
        server.ban(request);
    }

    public List<KickResponse> bans(long id) {
        GameServerInstance server = serverMap.get(id);
        return server.bans();
    }

    public void unban(long id, KickRequest request) {
        GameServerInstance server = serverMap.get(id);
        server.unban(request);
    }
}
