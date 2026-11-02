package com.metrovoc.phonon.lang;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LangFilesTest {
    private static final Pattern ARGUMENT = Pattern.compile("%(?!%)");

    @SuppressWarnings("unchecked")
    private static Map<String, String> load(String language) throws Exception {
        String path = "/assets/phonon/lang/" + language + ".json";
        try (var reader = new InputStreamReader(LangFilesTest.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Map.class);
        }
    }

    private static int arguments(String text) {
        Matcher matcher = ARGUMENT.matcher(text.replace("%%", ""));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void chineseMatchesEnglish() throws Exception {
        Map<String, String> english = load("en_us");
        Map<String, String> chinese = load("zh_cn");
        assertEquals(english.keySet(), chinese.keySet());
        english.forEach((key, text) -> assertEquals(arguments(text), arguments(chinese.get(key)), key));
    }
}
