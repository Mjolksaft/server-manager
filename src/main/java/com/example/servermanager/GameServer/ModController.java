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
    private LogWebSocketHandler logHandler;

    @Autowired
    private ObjectMapper mapper;

    @PostMapping("/{port}/log")
    public ResponseEntity<ActionResponse> logAction(@PathVariable int port, @RequestBody ModActionEvent event) {
        try {
            var obj = mapper.createObjectNode();
            obj.put("type", "game_action");
            obj.put("serverId", -1);
            obj.put("port", port);
            obj.put("action", event.action() != null ? event.action() : "mod_event");
            obj.put("sender", event.sender());
            obj.put("target", event.target());
            obj.put("detail", event.detail());
            obj.put("result", event.result());
            String json = mapper.writeValueAsString(obj);
            System.out.println("[ModController] broadcasting: " + json);
            logHandler.broadcast(json);
        } catch (Exception e) {
            System.err.println("Failed to broadcast mod event: " + e.getMessage());
            e.printStackTrace();
        }
        return ResponseEntity.ok(new ActionResponse("log", event.action(), "logged"));
    }
}
