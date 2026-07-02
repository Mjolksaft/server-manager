package com.example.servermanager.GameServer;

import com.example.servermanager.dto.ServerResponse;
import java.util.List;

import org.apache.catalina.connector.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.servermanager.dto.KickRequest;
import com.example.servermanager.dto.KickResponse;
import com.example.servermanager.dto.ServerRequest;
import com.example.servermanager.dto.ServerStates;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
    public ResponseEntity<ServerResponse> start(@RequestBody ServerRequest request) {
        ServerResponse response = service.create(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/start/{id}")
    public ResponseEntity<Void> start(@PathVariable Long id) {
        service.start(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop/{id}")
    public ResponseEntity<Void> stop(@PathVariable Long id) {
        service.stop(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/isAlive/{id}")
    public ResponseEntity<ServerStates> getState(@PathVariable Long id) {
        return ResponseEntity.ok(service.getState(id));
    }

    @PostMapping("/{id}/kick/")
    public ResponseEntity<Void> kickPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        service.kick(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/ban/")
    public ResponseEntity<Void> banPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        service.ban(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unban/")
    public ResponseEntity<Void> unbanPlayer(@PathVariable Long id, @RequestBody KickRequest request) {
        service.unban(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/ban/")
    public ResponseEntity<List<KickResponse>> banPlayer(@PathVariable Long id) {
        List<KickResponse> response = service.bans(id);
        return ResponseEntity.ok(response);
    }
}
