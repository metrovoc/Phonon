package com.metrovoc.phonon.config;

/** Writes one server setting to the loader's config file and applies it to {@link PhononServerConfig}. */
@FunctionalInterface
public interface ServerConfigStore {
    void set(String section, String key, Object value);
}
