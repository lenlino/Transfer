package com.lenlino.transfer;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import util.Collection;
import util.CollectionList;
import util.ICollectionList;
import util.JSONAPI;
import util.StringCollection;
import util.promise.Promise;
import xyz.acrylicstyle.mcutil.bungeecord.ProxiedPlayer;
import xyz.acrylicstyle.mcutil.lang.MCVersion;
import xyz.acrylicstyle.sql.options.UpsertOptions;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

import static java.text.NumberFormat.Field.PREFIX;

@Plugin(
        id = "transfer",
        name = "Transfer",
        version = "1.0"
)
public class Transfer {

    static {
        try {
            Class.forName("util.JSONAPI");
            Class.forName("util.CollectionSet");
            Class.forName("util.CollectionList");
            Class.forName("util.ICollectionList");
            Class.forName("util.promise.Promise");
            //Class.forName("util.MultiCollection");
            Class.forName("xyz.acrylicstyle.mcutil.lang.MCVersion");
        } catch (ClassNotFoundException ignore) {
            ignore.printStackTrace();
        }
    }

    private final ProxyServer server;
    private final Logger logger;
    public static final TextComponent PREFIX = Component.text(NamedTextColor.GREEN + "[" + NamedTextColor.AQUA + "Transfer" + NamedTextColor.GREEN + "] " + NamedTextColor.YELLOW);

    public static Logger log;
    final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
            .path(Path.of("./plugins/BungeeWaiter/config.yml"))
            .build();
    public static ConfigurationNode config = null;
    public static boolean isTargetOnline = false;
    public static Map<UUID, TimerTask> tasks = new HashMap<>();
    public static final List<UUID> notification = new ArrayList<>(); // invert
    public static ICollectionList<UUID> noWarp = new CollectionList<>();
    public static final Timer timer = new Timer();
    public static ConnectionHolder db;
    public static final StringCollection<String> label = new StringCollection<>();

    @Inject
    public Transfer(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        logger.info("Hello there! I made my first plugin with Velocity.");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event)  {
        log = logger;
        reload();
        String host = config.node("database.host").getString();
        String name = config.node("database.name").getString();
        String user = config.node("database.user").getString();
        String password = config.node("database.password").getString();
        if (host == null || name == null || user == null || password == null) {
            log.info("One of database settings is null, not using database.");
        } else {
            db = new ConnectionHolder(host, name, user, password);
            db.connect();
        }
        try {
            config.node("notification").getList(String.class).forEach(s -> notification.add(UUID.fromString(s)));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        /*server.getCommandManager().register(new AltLookupCommand());
        getProxy().getPluginManager().registerCommand(this, new IpLookupCommand());
        getProxy().getPluginManager().registerCommand(this, new PlayersCommand());
        getProxy().getPluginManager().registerCommand(this, new GKickCommand());
        getProxy().getPluginManager().registerCommand(this, new TellCommand());
        getProxy().getPluginManager().registerCommand(this, new VersionsCommand());
        getProxy().getPluginManager().registerCommand(this, new SAlertCommand());
        getProxy().getPluginManager().registerCommand(this, new PingCommand());
        getProxy().getPluginManager().registerCommand(this, new PingAllCommand());
        getProxy().getPluginManager().registerCommand(this, new Command("notification", "bungeewaiter.notification") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                if (!(sender instanceof ProxiedPlayer)) return;
                ProxiedPlayer player = (ProxiedPlayer) sender;
                if (notification.contains(player.getUniqueId())) {
                    notification.remove(player.getUniqueId());
                    sender.sendMessage(new TextComponent(PREFIX + "Turned on the notification."));
                } else {
                    notification.add(player.getUniqueId());
                    sender.sendMessage(new TextComponent(PREFIX + "Turned off the notification."));
                }
            }
        });*/
        server.getEventManager().register(this,this);
        //getProxy().registerChannel("bungeewaiter:protocol_version");
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                serversMap.forEach((limbo, target) -> {
                    ServerInfo info = server.getServer("target").get().getServerInfo();
                    if (info == null) {
                        log.warning("Could not get server info for " + target);
                        return;
                    }
                    server.getAllPlayers().forEach(player -> {
                        if (player.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(limbo) && !noWarp.contains(player.getUniqueId())) {
                            player.createConnectionRequest(server.getServer(target).get());
                        }
                    });
                });
            }
        };
        timer.schedule(task, 5000*2, 5000*2);
    }


    public static String bool(boolean bool) { return bool ? NamedTextColor.GREEN + "Yes" : NamedTextColor.RED + "No"; }

    public static String boolInverted(boolean bool) { return bool ? NamedTextColor.RED + "Yes" : NamedTextColor.GREEN + "No"; }


    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        String version = event.getPlayer().getProtocolVersion().getMostRecentSupportedVersion();
        Optional<ServerConnection> serverConnection = event.getPlayer().getCurrentServer();
        String servername = serverConnection.isPresent() ? serverConnection.get().getServerInfo().getName() : "Connect";
        server.getAllPlayers().forEach(player -> {
            if (player.hasPermission("transfer.logging")) {
                player.sendMessage(Component.text(event.getPlayer().getUsername() + NamedTextColor.GRAY
                        + "[" + version + "]" + NamedTextColor.YELLOW + ": " + servername + " -> Disconnect"));
            }
        });
    }

    public static final Collection<UUID, KickData> kickQueue = new Collection<>();

    public static StringCollection<String> serversMap = new StringCollection<>();

    private void reload() {
        try {
            config = loader.load();
            noWarp = ICollectionList.asList(config.node("nowarp").getList(String.class)).map(UUID::fromString);
            serversMap = new StringCollection<>(ICollectionList.asList(config.node("servers").getList(String.class)).toMap(s -> s.split(":")[0], s -> s.split(":")[1]).mapKeys((s, o) -> s.toLowerCase()));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        label.clear();
    }

    @Nullable
    public static String getLabel(@NotNull String addr) {
        if (label.containsKey(addr)) return label.get(addr);
        String label = config.node("labels." + addr).getString(null);
        if (label != null) Transfer.label.add(addr, label);
        return label;
    }

    @Nullable
    public static String getLabel(@NotNull Player player) {
        return getLabel(player.getVirtualHost().get().getHostName());
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent e) { reload(); }

    @Subscribe
    public void onServerKick(KickedFromServerEvent e) {
        if (e.getPlayer().getCurrentServer().isEmpty() || e.getPlayer().getCurrentServer().isEmpty()) return;
        kickQueue.add(e.getPlayer().getUniqueId(), new KickData(e.getPlayer().getCurrentServer().get().getServerInfo().getName(), e.getServerKickReason().get()));
        e.getPlayer().sendMessage(Component.text(NamedTextColor.RED + "サーバーからキックされました:"));
        e.getPlayer().sendMessage(e.getServerKickReason().get());
        String currentServer = e.getPlayer().getCurrentServer().get().getServerInfo().getName();
        String target = filter(serversMap, v -> v.equalsIgnoreCase(currentServer)).firstKey();
        if (target == null) return;
        ServerInfo targetServer = server.getServer(target).get().getServerInfo();
        if (targetServer == null) {
            log.warning("Couldn't find " + target + " server as fallback!");
            return;
        }
        /*e.setCancelled(true);
        e.setCancelServer(targetServer);*/
    }

    public static StringCollection<String> filter(StringCollection<String> thiz, Function<String, Boolean> filter) {
        StringCollection<String> newList = new StringCollection<>();
        CollectionList<String> keys = thiz.keysList();
        thiz.foreach((v, i) -> {
            if (filter.apply(v)) newList.put(keys.get(i), v);
        });
        return newList.clone();
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent e) {
        if (!(e.getConnection().getVirtualHost().get() instanceof InetSocketAddress)) return;
        final String address = getAddress((InetSocketAddress) e.getConnection().getRemoteAddress());
        try {
            db.needsUpdate(address).then(update -> {
                if (!update) return null; // if it doesn't needs to update country data, return
                if (config.node("apiKey").getString() != null) new Thread(() -> {
                    JSONObject response = new JSONAPI("http://api.ipstack.com/" + address + "?access_key=" + config.node("apiKey").getString()).call(JSONObject.class).getResponse();
                    db.setCountry(address, response.getString("country_code"), response.getString("country_name")).queue();
                }).start();
                if (config.node("apiKey2").getString() != null) new Thread(() -> {
                    JSONObject response = new JSONAPI(String.format("https://www.ipqualityscore.com/api/json/ip/%s/%s?strictness=0&allow_public_access_points=true&lighter_penalties=true", config.node("apiKey2").getString(), address)).call().getResponse();
                    if (!response.getBoolean("success")) return;
                    String isp = response.getString("ISP");
                    if (isp == null) isp = response.getString("isp");
                    db.setFraud(address, response.getInt("fraud_score"), response.getBoolean("proxy"), response.getBoolean("vpn"), isp).queue();
                }).start();
                return null;
            }).queue();
        } catch (RuntimeException ignored) {}
    }

    public static String getAddress(InetSocketAddress address) {
        return address.getAddress().getHostAddress().replaceFirst("(.*)%.*", "$1");
    }

    public static String getAddress(InetAddress address) {
        return address.getHostAddress().replaceFirst("(.*)%.*", "$1");
    }

    public static String getAddress(Player player) {
        if (player.getRemoteAddress() == null) {
            throw new IllegalArgumentException("Player " + player.getUsername() + " isn't connecting via InetSocketAddress");
        }
        return getAddress((InetSocketAddress) player.getRemoteAddress());
    }

    @Subscribe
    public void onLogin(LoginEvent e) {
        if (e.getPlayer().getRemoteAddress() != null) {
            InetAddress addr = e.getPlayer().getRemoteAddress().getAddress();
            final String address = getAddress(e.getPlayer().getRemoteAddress());
            (addr instanceof Inet4Address ? db.lastIpV4 : db.lastIpV6).upsert(
                    new UpsertOptions.Builder()
                            .addWhere("uuid", e.getPlayer().getUniqueId().toString())
                            .addValue("uuid", e.getPlayer().getUniqueId().toString())
                            .addValue("name", e.getPlayer().getUsername())
                            .addValue("ip", address)
                            .build()
            ).queue();
        }
    }

    public static final String SOCKET = null;

    @NotNull
    public Promise<@Nullable String> getCountry(@NotNull Player player) {
        SocketAddress addr = player.getRemoteAddress();
        //if (addr instanceof SocketAddress) return Promise.of(SOCKET);
        if (!(addr instanceof InetSocketAddress)) {
            log.info("Ignoring unknown socket(from " + player.getUsername() + "): " + addr.getClass().getCanonicalName());
            return Promise.of(null);
        }
        return db.getCountry(getAddress(player));
    }

    public static MCVersion getReleaseVersionIfPossible(int protocolVersion) {
        ICollectionList<MCVersion> list = ICollectionList.asList(MCVersion.getByProtocolVersion(protocolVersion));
        return list.filter(v -> !v.isSnapshot()).size() == 0 // if non-snapshot version wasn't found
                ? Objects.requireNonNull(list.first()) // return the last version anyway
                : Objects.requireNonNull(list.filter(v -> !v.isSnapshot()).first()); // return non-snapshot version instead
    }

    public static String getConnectionType(Player player) {
        SocketAddress addr = player.getRemoteAddress();
        if (addr instanceof InetSocketAddress) {
            InetAddress address = ((InetSocketAddress) addr).getAddress();
            if (address instanceof Inet4Address) {
                return "IPv4";
            } else if (address instanceof Inet6Address) {
                return "IPv6";
            } else {
                return "Unknown (" + address.getClass().getSimpleName() + ")";
            }
        }  else {
            return "Unknown (" + addr.getClass().getSimpleName() + ")";
        }
    }

    public static String getConnectionTypeColored(Player player) {
        SocketAddress addr = player.getRemoteAddress();
        if (addr instanceof InetSocketAddress) {
            InetAddress address = ((InetSocketAddress) addr).getAddress();
            if (address instanceof Inet4Address) {
                return NamedTextColor.AQUA + "IPv4";
            } else if (address instanceof Inet6Address) {
                return NamedTextColor.GREEN + "IPv6";
            } else {
                return NamedTextColor.GRAY + "Unknown (" + address.getClass().getSimpleName() + ")";
            }
        }  else {
            return NamedTextColor.GRAY + "Unknown (" + addr.getClass().getSimpleName() + ")";
        }
    }

    public static String getConnectionPath(Player player) {
        SocketAddress addr = player.getRemoteAddress();
        if (addr instanceof UnixDomainSocketAddress) {
            return ((UnixDomainSocketAddress)addr).getPath().toString();
        }
        return null;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent e) {
        @Nullable KickData kickData = kickQueue.remove(e.getPlayer().getUniqueId());
        if (!e.getPlayer().isActive()) return;
        ServerConnection server = e.getPlayer().getCurrentServer().get();
        String name = server == null || server.getServerInfo() == null ? "Connect" : server.getServerInfo().getName();
        String kickMessage;
        if (kickData == null) {
            kickMessage = null;
        } else {
            kickMessage = name.equals(kickData.getServer()) ? kickData.getMessage() : null;
        }
        String target = e.getServer().getServerInfo().getName();
        String country = getCountry(e.getPlayer()).complete();
        @Nullable ConnectionHolder.FraudScore score = e.getPlayer().getRemoteAddress() instanceof InetSocketAddress ? db.getFraudScore(getAddress(e.getPlayer())).complete() : null;
        if (country != null) country = ", " + country;
        if (country == null) country = "";
        if (score != null && score.vpn) country += NamedTextColor.GRAY + ", " + NamedTextColor.GOLD + "VPN";
        String version = getReleaseVersionIfPossible(e.getPlayer().getProtocolVersion().getProtocol()).getName();
        String lab = getLabel(e.getPlayer());
        String label = lab == null ? "" : ", " + lab;
        TextComponent tc = Component.text(PREFIX + e.getPlayer().getUsername() + NamedTextColor.GRAY + "[" + version + label + NamedTextColor.GRAY + country + NamedTextColor.GRAY + "]"
                + NamedTextColor.YELLOW + ": " + name + " -> " + target + (kickMessage != null ? NamedTextColor.GRAY + " (kicked from " + kickData.getServer() + ": " + kickMessage + ")" : ""));
        this.server.getAllPlayers().forEach(player -> {
            if (player.hasPermission("bungeewaiter.logging") || player.hasPermission("bungeewaiter.notification")) {
                if (!notification.contains(player.getUniqueId())) {
                    player.sendMessage(tc);
                }
            }
        });
        TimerTask t = tasks.remove(e.getPlayer().getUniqueId());
        if (t != null) t.cancel();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!e.getPlayer().isActive()) this.cancel();
                if (e.getPlayer().getCurrentServer() == null || e.getPlayer().getCurrentServer().get().getServerInfo() == null) {
                    log.warning(e.getPlayer().getUsername() + "'s server is null");
                    return;
                }
                if (serversMap.keysList().contains(e.getPlayer().getCurrentServer().get().getServerInfo().getName())) {
                    e.getPlayer().sendMessage(Component.text(NamedTextColor.YELLOW + "自動でサーバーに接続されます。そのままお待ちください。"));
                } else this.cancel();
            }
        };
        tasks.put(e.getPlayer().getUniqueId(), task);
        timer.schedule(task, 100, 30000); // give a small delay
    }
}
