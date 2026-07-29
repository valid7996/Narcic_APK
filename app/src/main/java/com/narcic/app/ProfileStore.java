package com.narcic.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProfileStore {
    private static final String PREFS = "narcic_profiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_SELECTED_MANUAL = "selectedManual";
    private static final String KEY_SELECTED_SUGGESTED = "selectedSuggested";
    private static final String KEY_AUTO_MODE = "autoMode";
    private static final String KEY_PICK_BEST_MANUAL = "pickBestManual";
    private static final String KEY_SEEDED_VERSION = "seededVersion";
    private static final int SEEDED_VERSION = 10;
    public static final String ORIGIN_BUILTIN = "builtin";
    public static final String ORIGIN_REMOTE = "github";
    private static final String BUILTIN_CONFIGS =
            "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2F9XYDaLaF4fMbAPWf%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NarGes-1999.PAges.dev#Narcic%201\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FDNWDkHk7g0nplzx6%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=nARGeS-1999.pAges.dEV#Narcic%202\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2F85aAikDueTdXGNeN%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NarGEs-1999.PAGES.dev#Narcic%203\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FogwodGSdl6qqZXIc%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=naRgEs-1999.pAGEs.dev#Narcic%204\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FdHQ1hsPCxI8qeBqE%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=naRgEs-1999.pAGEs.dEV#Narcic%205\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FMmMpiCOhasZCkcj9%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NArgEs-1999.pageS.DEV#Narcic%206\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2Fwi1tG1ElSplefDTf%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=naRgES-1999.PAges.dEv#Narcic%207\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FAuJkWwnKa7NWE2CX%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NARgEs-1999.PAgEs.Dev#Narcic%208\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FLMW7bV5ijr7Q76MD%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=nArGeS-1999.pagEs.Dev#Narcic%209\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FmHPJZC2RZd4OO2dS%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NaRGeS-1999.pages.deV#Narcic%2010\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FJUWgC5VDMy6ETdb1%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=naRgES-1999.PAGes.DEv#Narcic%2011\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2Fua2WwoGo8IyJSqF6%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NaRGEs-1999.pAGes.dEv#Narcic%2012\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FbQCBqI2Zs6t4abEm%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NARgeS-1999.PAgES.DEV#Narcic%2013\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2F9xrxfQwg5WMgjQ9b%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=nArGes-1999.PAGEs.DEV#Narcic%2014\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2FYdSGLRnro9UsKM1P%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NARges-1999.pAGeS.dEv#Narcic%2015\n"
                    + "vless://baad4fab-ceee-4a9d-a714-e641d39405f4@127.0.0.1:40443?path=%2F3E6nZ9O3WPzyHH3K%3Fed%3D2560&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&host=narges-1999.pages.dev&fp=randomized&type=ws&sni=NaRgeS-1999.PagES.DEv#Narcic%2016";

    private final SharedPreferences prefs;

    public ProfileStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<ProxyConfig> loadProfiles() {
        List<ProxyConfig> profiles = new ArrayList<>();
        String raw = prefs.getString(KEY_PROFILES, "");
        if (!raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    profiles.add(ProxyConfig.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception ignored) {
                profiles.clear();
            }
        }
        boolean firstLaunchSeeded = false;
        if (raw.isEmpty() && profiles.isEmpty()) {
            firstLaunchSeeded = addMissingBuiltins(profiles) > 0;
        }
        int storedVersion = prefs.getInt(KEY_SEEDED_VERSION, 0);
        boolean upgradingSpoofDefaults = storedVersion < SEEDED_VERSION;
        boolean removedLegacy = removeLegacyNonV2rayProfiles(profiles);
        boolean removedObsoleteBuiltins = upgradingSpoofDefaults && removeObsoleteBuiltins(profiles);
        boolean repaired = repairTargetsFromSourceUri(profiles, upgradingSpoofDefaults);
        boolean renamed = normalizeBuiltinProfileNames(profiles);
        if (upgradingSpoofDefaults) {
            addMissingBuiltins(profiles);
        }
        if (storedVersion < SEEDED_VERSION || firstLaunchSeeded) {
            saveProfiles(profiles);
            if (firstLaunchSeeded) {
                setSelectedSuggestedId(getSelectedSuggestedId(profiles));
            }
            prefs.edit().putInt(KEY_SEEDED_VERSION, SEEDED_VERSION).apply();
        } else if (removedLegacy || repaired || renamed) {
            saveProfiles(profiles);
        }
        return profiles;
    }

    public static List<ProxyConfig> defaultProfiles() {
        List<ProxyConfig> defaults = new ArrayList<>(VlessParser.parseMany(BUILTIN_CONFIGS, true));
        for (int i = 0; i < defaults.size(); i++) {
            defaults.get(i).name = "Narcic " + (i + 1);
            defaults.get(i).origin = ORIGIN_BUILTIN;
        }
        return defaults;
    }

    public int addSuggestedProfiles() {
        List<ProxyConfig> profiles = loadProfiles();
        int added = addMissingBuiltins(profiles);
        if (added > 0) {
            saveProfiles(profiles);
            if (getSelectedSuggestedId(profiles).isEmpty()) {
                setSelectedSuggestedId(profiles.get(0).id);
            }
        }
        return added;
    }

    public boolean isAutoMode() {
        return prefs.getBoolean(KEY_AUTO_MODE, true);
    }

    public void setAutoMode(boolean autoMode) {
        prefs.edit().putBoolean(KEY_AUTO_MODE, autoMode).apply();
    }

    public boolean isPickBestManual() {
        return prefs.getBoolean(KEY_PICK_BEST_MANUAL, false);
    }

    public void setPickBestManual(boolean enabled) {
        prefs.edit().putBoolean(KEY_PICK_BEST_MANUAL, enabled).apply();
    }

    public int syncRemoteProfiles(List<ProxyConfig> remoteProfiles) {
        List<ProxyConfig> profiles = loadProfiles();
        String selectedBefore = getSelectedId(profiles);
        profiles.removeIf(profile -> ORIGIN_REMOTE.equals(profile.origin));
        int added = 0;
        for (ProxyConfig remote : remoteProfiles) {
            if (remote.sourceUri == null || remote.sourceUri.trim().isEmpty() || containsSource(profiles, remote.sourceUri)) {
                continue;
            }
            remote.origin = ORIGIN_REMOTE;
            if (!remote.name.startsWith("GitHub ")) {
                remote.name = "GitHub " + remote.name;
            }
            profiles.add(remote);
            added++;
        }
        saveProfiles(profiles);
        boolean selectedStillExists = false;
        for (ProxyConfig profile : profiles) {
            if (profile.id.equals(selectedBefore)) {
                selectedStillExists = true;
                break;
            }
        }
        setSelectedId(selectedStillExists ? selectedBefore : (profiles.isEmpty() ? "" : profiles.get(0).id));
        return added;
    }

    private boolean containsSource(List<ProxyConfig> profiles, String sourceUri) {
        String normalized = stripFragment(sourceUri);
        for (ProxyConfig profile : profiles) {
            if (!profile.sourceUri.isEmpty() && stripFragment(profile.sourceUri).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean removeObsoleteBuiltins(List<ProxyConfig> profiles) {
        List<ProxyConfig> currentBuiltins = defaultProfiles();
        List<String> currentSources = new ArrayList<>();
        for (ProxyConfig builtin : currentBuiltins) {
            currentSources.add(stripFragment(builtin.sourceUri));
        }
        boolean selectedRemoved = false;
        String selectedBefore = getSelectedId(profiles);
        int before = profiles.size();
        profiles.removeIf(profile -> ORIGIN_BUILTIN.equals(profile.origin)
                && !currentSources.contains(stripFragment(profile.sourceUri)));
        boolean changed = profiles.size() != before;
        if (changed) {
            boolean stillExists = false;
            for (ProxyConfig profile : profiles) {
                if (profile.id.equals(selectedBefore)) {
                    stillExists = true;
                    break;
                }
            }
            if (!stillExists) {
                selectedRemoved = true;
            }
        }
        if (selectedRemoved && !profiles.isEmpty()) {
            setSelectedId(profiles.get(0).id);
        }
        return changed;
    }

    private boolean removeLegacyNonV2rayProfiles(List<ProxyConfig> profiles) {
        int before = profiles.size();
        profiles.removeIf(profile -> profile.id.startsWith("default-hcaptcha")
                || profile.name.toLowerCase().contains("hcaptcha")
                || !isV2rayProtocol(profile.protocol));
        return profiles.size() != before;
    }

    private boolean isV2rayProtocol(String protocol) {
        String value = protocol == null ? "" : protocol.toLowerCase();
        return value.equals("vless") || value.equals("trojan") || value.equals("vmess") || value.equals("ss");
    }

    private boolean repairTargetsFromSourceUri(List<ProxyConfig> profiles, boolean forceSpoofDefaults) {
        boolean changed = false;
        for (ProxyConfig profile : profiles) {
            ProxyConfig parsed = VlessParser.parseOne(profile.sourceUri);
            if (parsed == null) {
                continue;
            }
            if (forceSpoofDefaults
                    || profile.address == null
                    || profile.address.trim().isEmpty()
                    || profile.address.equals(parsed.configHost)
                    || profile.address.equals(parsed.address)) {
                profile.address = ProxyConfig.DEFAULT_SPOOF_ADDRESS;
                changed = true;
            }
            if (forceSpoofDefaults || profile.fallbackAddress == null || profile.fallbackAddress.trim().isEmpty()) {
                profile.fallbackAddress = ProxyConfig.DEFAULT_SPOOF_FALLBACK;
                changed = true;
            }
            if (forceSpoofDefaults || profile.sni == null || profile.sni.trim().isEmpty()) {
                profile.sni = ProxyConfig.DEFAULT_SPOOF_SNI;
                changed = true;
            }
            if (forceSpoofDefaults || profile.port <= 0 || profile.port == 40443 || profile.port == parsed.configPort) {
                profile.port = 443;
                changed = true;
            }
            if (profile.protocol == null || profile.protocol.trim().isEmpty()) {
                profile.protocol = parsed.protocol;
                changed = true;
            }
            if (profile.configHost == null || profile.configHost.trim().isEmpty()) {
                profile.configHost = parsed.configHost;
                changed = true;
            }
            if (profile.configPort <= 0) {
                profile.configPort = parsed.configPort;
                changed = true;
            }
        }
        return changed;
    }

    public static boolean isUserProfile(ProxyConfig profile) {
        return profile == null
                || profile.origin == null
                || profile.origin.trim().isEmpty()
                || "user".equals(profile.origin);
    }

    public static boolean isSuggestedProfile(ProxyConfig profile) {
        return profile != null && !isUserProfile(profile);
    }

    private int addMissingBuiltins(List<ProxyConfig> profiles) {
        int added = 0;
        for (ProxyConfig builtin : defaultProfiles()) {
            boolean exists = false;
            for (ProxyConfig profile : profiles) {
                if (!profile.sourceUri.isEmpty() && profile.sourceUri.equals(builtin.sourceUri)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                profiles.add(builtin);
                added++;
            }
        }
        return added;
    }

    private boolean normalizeBuiltinProfileNames(List<ProxyConfig> profiles) {
        boolean changed = false;
        List<ProxyConfig> builtins = defaultProfiles();
        for (ProxyConfig profile : profiles) {
            for (ProxyConfig builtin : builtins) {
                if (!profile.sourceUri.isEmpty() && isSameBuiltinProfile(profile, builtin)
                        && !profile.name.equals(builtin.name)) {
                    profile.name = builtin.name;
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private boolean isSameBuiltinProfile(ProxyConfig profile, ProxyConfig builtin) {
        return stripFragment(profile.sourceUri).equals(stripFragment(builtin.sourceUri));
    }

    private String stripFragment(String uri) {
        int hash = uri == null ? -1 : uri.indexOf('#');
        return hash >= 0 ? uri.substring(0, hash) : (uri == null ? "" : uri);
    }

    public void saveProfiles(List<ProxyConfig> profiles) {
        JSONArray array = new JSONArray();
        for (ProxyConfig profile : profiles) {
            try {
                array.put(profile.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply();
    }

    public String getSelectedId(List<ProxyConfig> profiles) {
        String selected = prefs.getString(KEY_SELECTED, "");
        for (ProxyConfig profile : profiles) {
            if (profile.id.equals(selected)) {
                return selected;
            }
        }
        String fallback = profiles.isEmpty() ? "" : profiles.get(0).id;
        setSelectedId(fallback);
        return fallback;
    }

    public String getSelectedManualId(List<ProxyConfig> profiles) {
        return getSelectedScopedId(profiles, KEY_SELECTED_MANUAL, true);
    }

    public String getSelectedSuggestedId(List<ProxyConfig> profiles) {
        return getSelectedScopedId(profiles, KEY_SELECTED_SUGGESTED, false);
    }

    private String getSelectedScopedId(List<ProxyConfig> profiles, String key, boolean manual) {
        String selected = prefs.getString(key, "");
        for (ProxyConfig profile : profiles) {
            if (profile.id.equals(selected) && (manual ? isUserProfile(profile) : isSuggestedProfile(profile))) {
                return selected;
            }
        }
        for (ProxyConfig profile : profiles) {
            if (manual ? isUserProfile(profile) : isSuggestedProfile(profile)) {
                prefs.edit().putString(key, profile.id).apply();
                return profile.id;
            }
        }
        prefs.edit().putString(key, "").apply();
        return "";
    }

    public void setSelectedId(String id) {
        prefs.edit().putString(KEY_SELECTED, id).apply();
    }

    public void setSelectedManualId(String id) {
        prefs.edit().putString(KEY_SELECTED, id).putString(KEY_SELECTED_MANUAL, id).apply();
    }

    public void setSelectedSuggestedId(String id) {
        prefs.edit().putString(KEY_SELECTED, id).putString(KEY_SELECTED_SUGGESTED, id).apply();
    }
}

