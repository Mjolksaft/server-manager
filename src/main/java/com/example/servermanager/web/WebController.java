package com.example.servermanager.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.servermanager.GameServer.GameServerService;


@Controller
public class WebController {
    @Autowired
    private GameServerService service;

    @GetMapping("/")
    public String index(Model model) {
        // model.addAttribute("serverState", state);
        return "index";
    }
}
