package net.minecraftforge.gradle;

import org.gradle.api.Project;

public class ProjectUtils {
    private ProjectUtils() {
    }

    public static boolean getBooleanProperty(Project project, String name) {
        Object prop = project.findProperty(name);
        if (prop == null) return false;
        return Boolean.parseBoolean(prop.toString());
    }
}
