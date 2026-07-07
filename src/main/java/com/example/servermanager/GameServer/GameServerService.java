package com.example.servermanager.GameServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.GameType;
import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.ModResponse;
import com.example.servermanager.dto.SayRequest;
import com.example.servermanager.dto.ServerConfig;
import com.example.servermanager.dto.ServerRequest;
import com.example.servermanager.dto.ServerResponse;
import com.example.servermanager.dto.ServerStates;
import com.example.servermanager.dto.PlayerResponse;
import com.example.servermanager.dto.PasswordRequest;
import com.example.servermanager.dto.TimeRequest;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

@Service
public class GameServerService {
    @Autowired
    private LogWebSocketHandler logHandler;

    @Autowired
    private ObjectMapper mapper;

    @Value("${terraria.server.dir}")
    private String terrariaServerDir;

    @Value("${terraria.worlds.dir}")
    private String terrariaWorldsDir;

    @Value("${tmodloader.server.dir}")
    private String tmodloaderServerDir;

    @Value("${tmodloader.worlds.dir}")
    private String tmodloaderWorldsDir;

    @Value("${tmodloader.mods.enabled.path}")
    private String tmodloaderModsEnabledPath;

    @Value("${server.data.dir}")
    private String dataDir;

    private Path DATA_DIR;
    private Path CONFIG_FILE;

    private Map<Long, GameServer> serverMap = new HashMap<>();
    private AtomicLong idCounter = new AtomicLong(0);

    @PostConstruct
    public void init() {
        DATA_DIR = Path.of(dataDir);
        CONFIG_FILE = DATA_DIR.resolve("servers.json");
        try {
            if (Files.exists(CONFIG_FILE)) {
                List<ServerConfig> configs = mapper.readValue(CONFIG_FILE.toFile(),
                        new TypeReference<List<ServerConfig>>() {});
                for (ServerConfig cfg : configs) {
                    GameServer server;
                    if (cfg.type() == GameType.TMODLOADER) {
                        server = new TModLoaderServer(cfg.id(), cfg.port(), cfg.worldName(), logHandler,
                                cfg.enabledModsFile(), tmodloaderWorldsDir, tmodloaderServerDir, tmodloaderModsEnabledPath,
                                dataDir);
                    } else {
                        server = new TerrariaServer(cfg.id(), cfg.port(), cfg.worldName(), logHandler,
                                cfg.enabledModsFile(), terrariaWorldsDir, terrariaServerDir, dataDir);
                    }
                    serverMap.put(cfg.id(), server);
                    if (cfg.id() >= idCounter.get()) {
                        idCounter.set(cfg.id() + 1);
                    }
                }
                System.out.println("[GameServerService] Loaded " + configs.size() + " servers from " + CONFIG_FILE);
            }
        } catch (Exception e) {
            System.err.println("[GameServerService] Failed to load servers: " + e.getMessage());
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(DATA_DIR);
            List<ServerConfig> configs = new ArrayList<>();
            for (GameServer server : serverMap.values()) {
                configs.add(new ServerConfig(server.getId(), server.getPort(),
                        server.getWorldName(), server.getType(), server.getEnabledModsFile()));
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), configs);
        } catch (IOException e) {
            System.err.println("[GameServerService] Failed to save servers: " + e.getMessage());
        }
    }

    public List<ServerResponse> getServers() {
        return serverMap.values().stream().map(server -> new ServerResponse(
                server.getId(),
                server.getPort(),
                server.getWorldName(),
                server.getServerPath(),
                server.getState(),
                server.getType(),
                server.getEnabledModsFile())).toList();
    }

    public ServerResponse getServer(long id) {
        GameServer server = findServerOrThrow(id);
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState(), server.getType(), server.getEnabledModsFile());
    }

    public ServerResponse create(ServerRequest request) {
        Long id = idCounter.getAndIncrement();

        GameServer newInstance;
        if (request.type() == GameType.TMODLOADER) {
            newInstance = new TModLoaderServer(id, request.port(), request.worldName(), logHandler,
                    request.enabledModsFile(), tmodloaderWorldsDir, tmodloaderServerDir, tmodloaderModsEnabledPath,
                    dataDir);
        } else {
            newInstance = new TerrariaServer(id, request.port(), request.worldName(), logHandler,
                    request.enabledModsFile(), terrariaWorldsDir, terrariaServerDir, dataDir);
        }

        serverMap.put(id, newInstance);
        saveConfig();
        return new ServerResponse(newInstance.getId(), newInstance.getPort(), newInstance.getWorldName(),
                newInstance.getServerPath(), newInstance.getState(), newInstance.getType(),
                newInstance.getEnabledModsFile());
    }

    public void delete(long id) {
        GameServer server = findServerOrThrow(id);
        if (server.getState() == ServerStates.RUNNING || server.getState() == ServerStates.STARTING) {
            server.stop();
        }
        serverMap.remove(id);
        saveConfig();
    }

    public ServerResponse start(long id) {
        GameServer server = findServerOrThrow(id);
        server.start();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState(), server.getType(), server.getEnabledModsFile());
    }

    public ServerResponse stop(long id) {
        GameServer server = findServerOrThrow(id);
        server.stop();
        return new ServerResponse(server.getId(), server.getPort(), server.getWorldName(), server.getServerPath(),
                server.getState(), server.getType(), server.getEnabledModsFile());
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

    public List<ModResponse> getMods(long id) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            return tmod.getMods();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Server " + id + " is not a TModLoader server");
    }

    public KickResponse unban(long id, KickRequest request) {
        GameServer server = findServerOrThrow(id);
        return server.unban(request);
    }

    public String tpToSpawn(long id, String playerName) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            return tmod.tpToSpawn(playerName);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Server " + id + " is not a TModLoader server");
        }
    }

    public String giveItem(long id, String playerName, String itemName, Integer amount) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            return tmod.giveItem(playerName, itemName, amount);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Server " + id + " is not a TModLoader server");
        }
    }

    public String tpToPlayer(long id, String name, String target) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            return tmod.tpToPlayer(name, target);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Server " + id + " is not a TModLoader server");
        }
    }

    public String spawnMob(long id, String npcName, String playerName) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            return tmod.spawnMob(npcName, playerName);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Server " + id + " is not a TModLoader server");
        }
    }

    public String killEntity(long id, String name) {
        GameServer server = findServerOrThrow(id);
        if (server instanceof TModLoaderServer tmod) {
            return tmod.killEntity(name);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Server " + id + " is not a TModLoader server");
        }
    }

    public String settle(long id) {
        GameServer server = findServerOrThrow(id);
        return server.settle();
    }


    public List<PlayerResponse> getPlayers(long id) {
        GameServer server = findServerOrThrow(id);
        return server.queryPlayers();
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

    public String queryTime(long id) {
        GameServer server = findServerOrThrow(id);
        return server.queryTime();
    }

    public String querySeed(long id) {
        GameServer server = findServerOrThrow(id);
        return server.querySeed();
    }

    private GameServer findServerOrThrow(long id) {
        GameServer server = serverMap.get(id);
        if (server == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Server with ID " + id + " not found");
        }
        return server;
    }

    public GameServer findByPort(int port) {
        for (GameServer server : serverMap.values()) {
            if (server.getPort() == port) {
                return server;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Server with port " + port + " not found");
    }
}
