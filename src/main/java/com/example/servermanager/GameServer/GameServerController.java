package com.example.servermanager.GameServer;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.ModInstallRequest;
import com.example.servermanager.dto.SayRequest;
import com.example.servermanager.dto.SeedResponse;
import com.example.servermanager.dto.ServerRequest;
import com.example.servermanager.dto.ServerResponse;
import com.example.servermanager.dto.ServerStates;
import com.example.servermanager.dto.TimeResponse;
import com.example.servermanager.dto.PasswordRequest;
import com.example.servermanager.dto.PlayerResponse;
import com.example.servermanager.dto.TimeRequest;

@RestController
@RequestMapping("server")
public class GameServerController {

    @Autowired
    public GameServerService service;

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

    @PostMapping("/{id}/kick/")
    public ResponseEntity<KickResponse> kickPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        KickResponse response = service.kick(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/ban/")
    public ResponseEntity<KickResponse> banPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        KickResponse response = service.ban(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/unban/")
    public ResponseEntity<KickResponse> unbanPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        KickResponse response = service.unban(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/ban/")
    public ResponseEntity<List<KickResponse>> getBans(@PathVariable Long id) {
        List<KickResponse> response = service.bans(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/save/")
    public ResponseEntity<ServerResponse> save(@PathVariable Long id) {
        ServerResponse response = service.save(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/say/")
    public ResponseEntity<Void> say(@PathVariable Long id, @RequestBody SayRequest request) {
        service.say(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/time/")
    public ResponseEntity<Void> setTime(@PathVariable Long id, @RequestBody TimeRequest request) {
        service.setTime(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/password/")
    public ResponseEntity<Void> setPassword(@PathVariable Long id, @RequestBody PasswordRequest request) {
        service.setPassword(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/players/")
    public ResponseEntity<List<PlayerResponse>> getPlayers(@PathVariable Long id) {
        List<PlayerResponse> players = service.getPlayers(id);
        return ResponseEntity.ok(players);
    }

    @PostMapping("/{id}/settle/")
    public ResponseEntity<Void> settle(@PathVariable Long id) {
        service.settle(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/time/")
    public ResponseEntity<TimeResponse> getTime(@PathVariable Long id) {
        String time = service.queryTime(id);
        return ResponseEntity.ok(new TimeResponse(time));
    }

    @GetMapping("/{id}/seed/")
    public ResponseEntity<SeedResponse> getSeed(@PathVariable Long id) {
        String seed = service.querySeed(id);
        return ResponseEntity.ok(new SeedResponse(seed));
    }

    @PostMapping("/{id}/mod/install")
    public ResponseEntity<Void> installMod(@PathVariable Long id, @RequestBody ModInstallRequest request) {
        service.installMod(id, request.name());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/mod/reload")
    public ResponseEntity<Void> reloadMods(@PathVariable Long id) {
        service.reloadMods(id);
        return ResponseEntity.ok().build();
    }
}
