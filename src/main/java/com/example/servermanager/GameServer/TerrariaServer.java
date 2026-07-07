package com.example.servermanager.GameServer;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.GameType;

public class TerrariaServer extends GameServer {

    public TerrariaServer(long id, int port, String worldName, LogWebSocketHandler logHandler,
                          String enabledModsFile, String worldsDir, String serverDir, String dataDir) {
        super(id, port, worldName,
                worldsDir + "/" + worldName + ".wld",
                serverDir,
                logHandler, enabledModsFile, dataDir);
        this.type = GameType.TERRARIA;
    }

    @Override
    public String getExecutableName() {
        return "TerrariaServer.exe";
    }

    @Override
    public String getWorkingDirectory() {
        return serverDir;
    }
}
