package com.example.servermanager.GameServer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.GameType;
import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.SayRequest;
import com.example.servermanager.dto.ServerRequest;
import com.example.servermanager.dto.ServerResponse;
import com.example.servermanager.dto.ServerStates;
import com.example.servermanager.dto.PlayerResponse;
import com.example.servermanager.dto.PasswordRequest;
import com.example.servermanager.dto.TimeRequest;

@Service
public class GameServerService {
    @Autowired
    private LogWebSocketHandler logHandler;

    private Map<Long, GameServer> serverMap = new HashMap<>();
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
        GameServer server = findServerOrThrow(id);
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerResponse create(ServerRequest request) {
        Long id = idCounter.getAndIncrement();
        System.out.println(request.worldName());

        GameServer newInstance;
        if (request.type() == GameType.TMODLOADER) {
            newInstance = new TModLoaderServer(id, request.port(), request.worldName(), logHandler);
        } else {
            newInstance = new TerrariaServer(id, request.port(), request.worldName(), logHandler);
        }

        serverMap.put(id, newInstance);
        return new ServerResponse(newInstance.getId(), newInstance.getPort(), newInstance.getWorldName(),
                newInstance.getServerPath(), newInstance.getState());
    }

    public ServerResponse start(long id) {
        GameServer server = findServerOrThrow(id);
        server.start();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerResponse save(long id) {
        GameServer server = findServerOrThrow(id);
        server.save();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerResponse stop(long id) {
        GameServer server = findServerOrThrow(id);
        server.stop();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState());
    }

    public ServerStates getState(Long id) {
        GameServer server = findServerOrThrow(id);
        return server.getState();
    }

    public KickResponse kick(long id, KickRequest request) {
        GameServer server = findServerOrThrow(id);
        return server.kick(request);
    }

    public KickResponse ban(long id, KickRequest request) {
        GameServer server = findServerOrThrow(id);
        return server.ban(request);
    }

    public List<KickResponse> bans(long id) {
        GameServer server = findServerOrThrow(id);
        return server.bans();
    }

    public KickResponse unban(long id, KickRequest request) {
        GameServer server = findServerOrThrow(id);
        return server.unban(request);
    }

    public void say(long id, SayRequest request) {
        GameServer server = findServerOrThrow(id);
        server.say(request);
    }

    public void setTime(long id, TimeRequest request) {
        GameServer server = findServerOrThrow(id);
        server.setTime(request);
    }

    public void setPassword(long id, PasswordRequest request) {
        GameServer server = findServerOrThrow(id);
        server.setPassword(request);
    }

    public List<PlayerResponse> getPlayers(long id) {
        GameServer server = findServerOrThrow(id);
        return server.getPlayers();
    }

    public void settle(long id) {
        GameServer server = findServerOrThrow(id);
        server.settle();
    }

    public String queryTime(long id) {
        GameServer server = findServerOrThrow(id);
        return server.queryTime();
    }

    public String querySeed(long id) {
        GameServer server = findServerOrThrow(id);
        return server.querySeed();
    }

    public void installMod(long id, String modName) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            tmod.installMod(modName);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Server " + id + " is not a TModLoader server");
        }
    }

    public void reloadMods(long id) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            tmod.reloadMods();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Server " + id + " is not a TModLoader server");
        }
    }

    private GameServer findServerOrThrow(long id) {
        GameServer server = serverMap.get(id);
        if (server == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Server with ID " + id + " not found");
        }
        return server;
    }
}
