package com.example.servermanager.dto;

public record ServerResponse(
    long id,
    int port,
    String worldName,
    String serverPath,
    ServerStates state
) {}
