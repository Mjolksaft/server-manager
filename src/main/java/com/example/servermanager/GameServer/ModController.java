package com.example.servermanager.GameServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.ActionResponse;
import com.example.servermanager.dto.ModActionEvent;

import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("mod")
public class ModController {

    @Autowired
    private GameServerService service;

    @Autowired
    private LogWebSocketHandler logHandler;

    private ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/{port}/log")
    public ResponseEntity<ActionResponse> logAction(@PathVariable int port, @RequestBody ModActionEvent event) {
        service.findByPort(port);
        try {
            String json = mapper.writeValueAsString(event);
            logHandler.broadcast(json);
        } catch (Exception e) {
            System.err.println("Failed to broadcast mod event: " + e.getMessage());
            e.printStackTrace();
        }
        return ResponseEntity.ok(new ActionResponse("log", event.action(), "logged"));
    }
}
