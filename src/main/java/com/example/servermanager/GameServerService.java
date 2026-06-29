package com.example.servermanager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

@Service
public class GameServerService {
    private PtyProcess process;
    private List<String> logBuffer = new ArrayList<>();

    public String start() {
        try {
            String[] command = {
                    "D:/steam/steamapps/common/Terraria/TerrariaServer.exe",
                    "-world", "C:/Users/kalla/Documents/My Games/Terraria/Worlds/Testing_server.wld"
            };

            this.process = new PtyProcessBuilder(command)
                    .setDirectory("D:/steam/steamapps/common/Terraria")
                    .start();

            // Background thread to read server output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Terraria]: " + line);
                        logBuffer.add(line);
                    }
                } catch (IOException e) {
                    System.out.println("Server output stream closed.");
                }
            }).start();

        } catch (Exception err) {
            System.err.println(err);
            return "Failed to start: " + err.getMessage();
        }
        return "Server started!";
    }

    public String stop() {
        try {
            OutputStream stream = process.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            writer.write("exit");
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            System.err.println(e);
        }

        return "Server stopped";
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public List<String> getLogs() {
        return logBuffer;
    }
}
