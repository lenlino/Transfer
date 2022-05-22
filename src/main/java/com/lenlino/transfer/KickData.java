package com.lenlino.transfer;

import net.kyori.adventure.text.Component;

public class KickData {
    private final String server;
    private final String message;

    public KickData(String server, Component message) {
        this.server = server;
        this.message = message.insertion();
    }

    public String getServer() { return server; }

    public String getMessage() { return message; }
}
