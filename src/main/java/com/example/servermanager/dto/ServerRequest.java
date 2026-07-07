package com.example.servermanager.dto;

public record ServerRequest(
    int port,
    String worldName,
    GameType type,
    String enabledModsFile
) {}
