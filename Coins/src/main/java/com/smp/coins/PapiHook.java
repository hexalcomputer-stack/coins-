package com.smp.coins;

/**
 * Kept in its own class so PlaceholderAPI classes are only loaded when PlaceholderAPI is actually installed.
 */
final class PapiHook {

    private static CoinsExpansion expansion;

    private PapiHook() {
    }

    static void register(CoinsPlugin plugin) {
        expansion = new CoinsExpansion(plugin);
        expansion.register();
    }

    static void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }
}
