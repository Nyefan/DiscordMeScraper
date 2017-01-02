package DiscordMe;

import org.jetbrains.annotations.Contract;

import java.util.Optional;

/**
 * Created by Nyefan on 1/2/2017.
 * Contact at nyefancoding@gmail.com
 * or through Github at github.com/nyefan
 */
public class DiscordServer {

    private final String name;
    private final String link;
    private final String status;

    /**
     * Prevents this object from being created without being connected to a database
     */
    @Contract(" -> fail")
    private DiscordServer() {
        throw new IllegalStateException("This contructor should not be used.");
    }

    public DiscordServer(String name, String link, String status) {
        this.name = name;
        this.link = link;
        this.status = Optional.of(status).orElse("");
    }

    public String name() {
        return name;
    }

    public String link() {
        return link;
    }

    public String status() {
        return status;
    }
}
