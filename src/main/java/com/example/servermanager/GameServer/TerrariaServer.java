package com.example.servermanager.GameServer;

import com.example.servermanager.WebSocket.LogWebSocketHandler;

public class TerrariaServer extends GameServer {

    public TerrariaServer(long id, int port, String worldName, LogWebSocketHandler logHandler) {
        super(id, port, worldName,
                String.format("C:/Users/kalla/Documents/My Games/Terraria/Worlds/%s.wld", worldName),
                "D:/steam/steamapps/common/Terraria/",
                logHandler);
    }

    @Override
    public String getExecutableName() {
        return "TerrariaServer.exe";
    }

    @Override
    public String getWorkingDirectory() {
        return "D:/steam/steamapps/common/Terraria";
    }
}
