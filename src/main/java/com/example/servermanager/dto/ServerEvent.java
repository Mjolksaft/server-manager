package com.example.servermanager.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StateEvent.class, name = "state"),
    @JsonSubTypes.Type(value = LogEvent.class,   name = "log"),
    @JsonSubTypes.Type(value = ErrorEvent.class,  name = "error"),
    @JsonSubTypes.Type(value = GameActionEvent.class,  name = "game_action"),
    @JsonSubTypes.Type(value = PingEvent.class,  name = "ping")
})

public sealed interface ServerEvent permits StateEvent, LogEvent, ErrorEvent, GameActionEvent, PingEvent {
    long serverId();
}
