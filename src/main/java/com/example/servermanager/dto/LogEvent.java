package com.example.servermanager.dto;

public record LogEvent(long serverId, String text) implements ServerEvent {

}
