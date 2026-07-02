package com.example.servermanager.dto;

public record GameActionEvent(long serverId, GameAction action, String target, String detail) implements ServerEvent {

}
