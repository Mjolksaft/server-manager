package com.example.servermanager.dto;

public record ErrorEvent(long serverId, String reason, String detail) implements ServerEvent {

}
