package de.legoshi.parkourcalc.forge8.render;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class Forge8SkinResolver {

    private static final Logger LOG = LogManager.getLogger("ParkourCalculator");

    private final Map<String, GameProfile> resolved = new ConcurrentHashMap<String, GameProfile>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "pkc-skin-resolver");
            t.setDaemon(true);
            return t;
        }
    });
    private GameProfileRepository repository;

    public GameProfile resolved(String name) {
        return resolved.get(key(name));
    }

    public void request(Collection<String> names) {
        final List<String> todo = new ArrayList<String>();
        for (String name : names) {
            String k = key(name);
            if (k.isEmpty() || resolved.containsKey(k) || failed.contains(k) || !pending.add(k)) continue;
            todo.add(name);
        }
        if (todo.isEmpty()) return;
        pool.submit(new Runnable() {
            @Override
            public void run() {
                lookup(todo);
            }
        });
    }

    private void lookup(List<String> names) {
        final MinecraftSessionService session = Minecraft.getMinecraft().getSessionService();
        try {
            repository().findProfilesByNames(names.toArray(new String[0]), Agent.MINECRAFT, new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(GameProfile profile) {
                    String k = key(profile.getName());
                    try {
                        resolved.put(k, session.fillProfileProperties(profile, true));
                    } catch (RuntimeException e) {
                        failed.add(k);
                        LOG.info("Skin textures unavailable for " + profile.getName() + ": " + e);
                    }
                    pending.remove(k);
                }

                @Override
                public void onProfileLookupFailed(GameProfile profile, Exception exception) {
                    String k = key(profile.getName());
                    failed.add(k);
                    pending.remove(k);
                    LOG.info("No Mojang profile for replay " + profile.getName() + ": " + exception);
                }
            });
        } catch (RuntimeException e) {
            LOG.info("Skin lookup failed: " + e);
        } finally {
            for (String name : names) {
                String k = key(name);
                if (pending.remove(k) && !resolved.containsKey(k)) failed.add(k);
            }
        }
    }

    private synchronized GameProfileRepository repository() {
        if (repository == null) {
            repository = new YggdrasilAuthenticationService(Minecraft.getMinecraft().getProxy(),
                    UUID.randomUUID().toString()).createProfileRepository();
        }
        return repository;
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
