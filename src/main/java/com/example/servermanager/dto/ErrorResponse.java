package com.example.servermanager.dto;

public record ErrorResponse(
    int status,
    String error
) {}
