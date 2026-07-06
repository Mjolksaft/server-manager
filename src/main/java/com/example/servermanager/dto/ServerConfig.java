package com.example.servermanager.dto;

public record ServerConfig(
    long id,
    int port,
    String worldName,
    GameType type
) {}
