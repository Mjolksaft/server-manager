package com.example.servermanager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModActionEvent(
    Integer port,
    String action,
    String sender,
    String target,
    String detail,
    String result
) {}
