package com.example.servermanager.dto;

public record PingEvent(long serverId, long timestamp) implements ServerEvent {

}
