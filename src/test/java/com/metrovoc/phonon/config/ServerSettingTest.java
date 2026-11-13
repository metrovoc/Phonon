package com.metrovoc.phonon.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerSettingTest {
    @Test
    void hidesProxyPassword() {
        assertEquals("socks5://***@host:1080", PhononServerConfig.Setting.hidePassword("socks5://user:secret@host:1080"));
        assertEquals("http://***@host:8080/path@x", PhononServerConfig.Setting.hidePassword("http://user:secret@host:8080/path@x"));
        assertEquals("***@host:1080", PhononServerConfig.Setting.hidePassword("user:secret@host:1080"));
    }

    @Test
    void showsOtherValuesAsTheyAre() {
        assertEquals("socks5://host:1080", PhononServerConfig.Setting.hidePassword("socks5://host:1080"));
        assertEquals("cookies.txt", PhononServerConfig.Setting.hidePassword("cookies.txt"));
    }
}
