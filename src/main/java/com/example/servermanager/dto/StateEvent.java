package com.example.servermanager.dto;

public record StateEvent(long serverId, ServerStates state, String text) implements ServerEvent {

}
