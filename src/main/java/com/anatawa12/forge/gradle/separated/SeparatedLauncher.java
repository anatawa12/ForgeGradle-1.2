package com.anatawa12.forge.gradle.separated;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.process.JavaExecSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SeparatedLauncher {
    public static String configurationName = "comAnatawa12GradleForgeGradle1_2Separated";

    public final String name;
    public final List<String> args;

    public SeparatedLauncher(String name) {
        this.name = name;
        this.args = new ArrayList<String>();
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
        project.javaexec(new Action<JavaExecSpec>() {
            @Override
            public void execute(JavaExecSpec javaExecSpec) {
                javaExecSpec.classpath(project.getConfigurations().getByName(configurationName));
                javaExecSpec.setMain("com.anatawa12.forge.gradle.separated." + name + ".Main");
                javaExecSpec.args(args);
                javaExecSpec.setIgnoreExitValue(false);
            }
        });
    }
}
