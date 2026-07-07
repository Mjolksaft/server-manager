package com.example.servermanager.GameServer;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.servermanager.dto.ActionResponse;
import com.example.servermanager.dto.GiveItemRequest;
import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.KillEntityRequest;
import com.example.servermanager.dto.ModResponse;
import com.example.servermanager.dto.SayRequest;
import com.example.servermanager.dto.SeedResponse;
import com.example.servermanager.dto.ServerRequest;
import com.example.servermanager.dto.ServerResponse;
import com.example.servermanager.dto.ServerStates;
import com.example.servermanager.dto.SpawnMobRequest;
import com.example.servermanager.dto.SpawnRequest;
import com.example.servermanager.dto.TpRequest;
import com.example.servermanager.dto.TimeResponse;
import com.example.servermanager.dto.PasswordRequest;
import com.example.servermanager.dto.PlayerResponse;
import com.example.servermanager.dto.TimeRequest;

@RestController
@RequestMapping("server")
public class GameServerController {

    @Autowired
    public GameServerService service;

    private ResponseEntity<ActionResponse> actionResponse(String action, String target, String result) {
        if (result != null && result.contains("[ERROR]")) {
            return ResponseEntity.badRequest().body(new ActionResponse(action, target, result));
        }
        return ResponseEntity.ok(new ActionResponse(action, target, result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServerResponse> getServer(@PathVariable Long id) {
        ServerResponse server = service.getServer(id);
        return ResponseEntity.ok(server);
    }

    @GetMapping("/")
    public ResponseEntity<List<ServerResponse>> getServers() {
        List<ServerResponse> servers = service.getServers();
        return ResponseEntity.ok(servers);
    }

    @PostMapping("/create")
    public ResponseEntity<ServerResponse> create(@RequestBody ServerRequest request) {
        ServerResponse response = service.create(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/start/{id}")
    public ResponseEntity<ServerResponse> start(@PathVariable Long id) {
        ServerResponse response = service.start(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stop/{id}")
    public ResponseEntity<ServerResponse> stop(@PathVariable Long id) {
        ServerResponse response = service.stop(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/isAlive/{id}")
    public ResponseEntity<ServerStates> getState(@PathVariable Long id) {
        return ResponseEntity.ok(service.getState(id));
    }

    @PostMapping("/{id}/kick")
    public ResponseEntity<KickResponse> kickPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        KickResponse response = service.kick(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<KickResponse> banPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        KickResponse response = service.ban(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/unban")
    public ResponseEntity<KickResponse> unbanPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        KickResponse response = service.unban(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/ban")
    public ResponseEntity<List<KickResponse>> getBans(@PathVariable Long id) {
        List<KickResponse> response = service.bans(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/say")
    public ResponseEntity<ActionResponse> say(@PathVariable Long id, @RequestBody SayRequest request) {
        service.say(id, request);
        return ResponseEntity.ok(new ActionResponse("say", "world", request.message()));
    }

    @PostMapping("/{id}/time")
    public ResponseEntity<ActionResponse> setTime(@PathVariable Long id, @RequestBody TimeRequest request) {
        service.setTime(id, request);
        return ResponseEntity.ok(new ActionResponse("time", "world", request.time()));
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<ActionResponse> setPassword(@PathVariable Long id, @RequestBody PasswordRequest request) {
        service.setPassword(id, request);
        return ResponseEntity.ok(new ActionResponse("password", "server", "sets the password to the server"));
    }

    @GetMapping("/{id}/mods")
    public ResponseEntity<List<ModResponse>> getMods(@PathVariable Long id) {
        List<ModResponse> modList = service.getMods(id);
        return ResponseEntity.ok(modList);
    }


    @GetMapping("/{id}/players")
    public ResponseEntity<List<PlayerResponse>> getPlayers(@PathVariable Long id) {
        List<PlayerResponse> players = service.getPlayers(id);
        return ResponseEntity.ok(players);
    }

    @PostMapping("/{id}/settle")
    public ResponseEntity<ActionResponse> settle(@PathVariable Long id) {
        return actionResponse("settle", "world", service.settle(id));
    }

    @GetMapping("/{id}/time")
    public ResponseEntity<TimeResponse> getTime(@PathVariable Long id) {
        String time = service.queryTime(id);
        return ResponseEntity.ok(new TimeResponse(time));
    }

    @GetMapping("/{id}/seed")
    public ResponseEntity<SeedResponse> getSeed(@PathVariable Long id) {
        String seed = service.querySeed(id);
        return ResponseEntity.ok(new SeedResponse(seed));
    }

    @PostMapping("/{id}/spawn")
    public ResponseEntity<ActionResponse> tpToSpawn(@PathVariable Long id, @RequestBody SpawnRequest request) {
        return actionResponse("spawn", request.name(), service.tpToSpawn(id, request.name()));
    }

    @PostMapping("/{id}/tp")
    public ResponseEntity<ActionResponse> tpToPlayer(@PathVariable Long id, @RequestBody TpRequest request) {
        return actionResponse("tp", request.name(), service.tpToPlayer(id, request.name(), request.target()));
    }

    @PostMapping("/{id}/spawnmob")
    public ResponseEntity<ActionResponse> spawnMob(@PathVariable Long id, @RequestBody SpawnMobRequest request) {
        return actionResponse("spawnmob", request.npcName(), service.spawnMob(id, request.npcName(), request.playerName()));
    }

    @PostMapping("/{id}/kill")
    public ResponseEntity<ActionResponse> kill(@PathVariable Long id, @RequestBody KillEntityRequest request) {
        return actionResponse("kill", request.name(), service.killEntity(id, request.name()));
    }

    @PostMapping("/{id}/give")
    public ResponseEntity<ActionResponse> giveItem(@PathVariable Long id, @RequestBody GiveItemRequest request) {
        return actionResponse("give", request.itemName(), service.giveItem(id, request.playerName(), request.itemName(), request.amount()));
    }
}
