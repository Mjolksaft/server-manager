package com.example.servermanager.GameServer;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.example.servermanager.WebSocket.LogWebSocketHandler;
import com.example.servermanager.dto.ErrorEvent;
import com.example.servermanager.dto.GameAction;
import com.example.servermanager.dto.GameActionEvent;

public class TModLoaderServer extends GameServer {

    public TModLoaderServer(long id, int port, String worldName, LogWebSocketHandler logHandler) {
        super(id, port, worldName,
                String.format("C:/Users/kalla/Documents/My Games/Terraria/tModLoader/Worlds/%s.wld", worldName),
                "D:/steam/steamapps/common/tModLoader/",
                logHandler);
    }

    @Override
    public String getExecutableName() {
        return "tModLoaderServer.exe";
    }

    @Override
    public String getWorkingDirectory() {
        return "D:/steam/steamapps/common/tModLoader";
    }

    @Override
    protected String[] buildCommand() {
        return new String[] {
                serverPath + "LaunchUtils/busybox64.exe",
                "bash",
                "start-tModLoaderServer.sh",
                "-nosteam",
                "-world", worldPath,
                "-port", String.valueOf(port)
        };
    }

    public void installMod(String modName) {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write(String.format("mod install %s", modName));
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.MOD_INSTALL, modName, null));

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Mod install failed", err.getMessage()));
            throw new RuntimeException("Failed to install mod '" + modName + "': " + err.getMessage(), err);
        }
    }

    public void reloadMods() {
        try {
            ensureProcessRunning();
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("mod reload");
            writer.newLine();
            writer.flush();

            broadcast(new GameActionEvent(id, GameAction.MOD_RELOAD, null, null));

        } catch (Exception err) {
            System.err.println(err);
            broadcast(new ErrorEvent(id, "Mod reload failed", err.getMessage()));
            throw new RuntimeException("Failed to reload mods: " + err.getMessage(), err);
        }
    }
}
