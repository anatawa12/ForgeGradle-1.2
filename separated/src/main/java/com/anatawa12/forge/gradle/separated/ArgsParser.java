package com.anatawa12.forge.gradle.separated;

import java.io.File;

public class ArgsParser {
    private final String[] args;
    private int index = 0;

    public ArgsParser(String[] args) {
        this.args = args;
    }

    public File nextFile() {
        String s = nextString();
        if (s == null) return null;
        return new File(s);
    }

    public String nextString() {
        String s = args[index++];
        if (s.startsWith("+")) return null;
        return s.substring(1);
    }
}
