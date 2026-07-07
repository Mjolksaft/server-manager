package com.example.servermanager.dto;

public record GiveItemRequest(
    String playerName,
    String itemName,
    Integer amount
) {}
