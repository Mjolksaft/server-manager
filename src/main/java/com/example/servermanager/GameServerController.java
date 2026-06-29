package com.example.servermanager;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("server")
public class GameServerController {
    @Autowired
    public GameServerService service;

    @PostMapping("/start")
    public String start() {
        return service.start();
    }

    @PostMapping("/stop")
    public String stop() {
        return service.stop();
    }

    @GetMapping("/isAlive")
    public boolean isAlive() {
        return service.isAlive();
    }

    @GetMapping("/logs")
    public List<String> getLogs() {
        return service.getLogs();
    }
    
}
