package com.tagtusvr.cuddlyterminal;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class ShellUserService extends IShellService.Stub {
    public ShellUserService() { }

    @Override public String exec(String command) {
        if (command == null || command.trim().isEmpty()) return "";
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            int code = process.waitFor();
            if (code != 0) output.append("[exit ").append(code).append("]\n");
            return output.toString();
        } catch (Throwable error) {
            return "Shell service error: " + error.getClass().getSimpleName()
                + ": " + error.getMessage();
        }
    }
}
