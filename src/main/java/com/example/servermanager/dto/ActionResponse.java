package com.example.servermanager.dto;

public record ActionResponse(
    String action,
    String target,
    String message
) {}
