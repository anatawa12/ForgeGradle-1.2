package com.anatawa12.forge.gradle.separated;

import net.minecraftforge.gradle.GradleVersionUtils;
import org.gradle.api.Project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SeparatedLauncher {
    public static String configurationName = "comAnatawa12GradleForgeGradle1_2Separated";

    public final String name;
    public final List<String> args;

    public SeparatedLauncher(String name) {
        this.name = name;
        this.args = new ArrayList<>();
    }

    public SeparatedLauncher arg(File file) {
        return arg(file.getPath());
    }

    public SeparatedLauncher arg(String path) {
        if (path == null)
            args.add("+");
        else
            args.add("-" + path);
        return this;
    }

    public void run(final Project project) {
        project.javaexec(javaExecSpec -> {
            javaExecSpec.classpath(project.getConfigurations().getByName(configurationName));
            String main = "com.anatawa12.forge.gradle.separated." + name + ".Main";
            GradleVersionUtils.choose("6.4",
                    () -> javaExecSpec.setMain(main),
                    () -> javaExecSpec.getMainClass().set(main));
            javaExecSpec.args(args);
            javaExecSpec.setIgnoreExitValue(false);
        });
    }
}
