package com.example.servermanager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModActionEvent(
    int port,
    String action,
    String sender,
    String target,
    String detail,
    String result
) {}
