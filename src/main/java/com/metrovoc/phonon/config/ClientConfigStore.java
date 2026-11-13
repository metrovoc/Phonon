package com.metrovoc.phonon.config;

/** Writes the client settings to the loader's config file and applies them to {@link PhononClientConfig}. */
@FunctionalInterface
public interface ClientConfigStore {
    void save(String cacheDirectory, int maxCacheSizeMB);
}
