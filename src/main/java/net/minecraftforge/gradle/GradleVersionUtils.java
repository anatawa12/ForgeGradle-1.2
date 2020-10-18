package net.minecraftforge.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.util.VersionNumber;

public class GradleVersionUtils {
    /**
     * @param project detect by version of gradle of this project
     * @param versionName includes this version
     * @param action the action runs if gradle version is equals to or after {@code versionName}.
     */
    public static void ifAfter(Project project, String versionName, Runnable action) {
        VersionNumber gradleVersion = VersionNumber.parse(project.getGradle().getGradleVersion());
        VersionNumber version = VersionNumber.parse(versionName);

        if (gradleVersion.compareTo(version) > 0) {
            action.run();
        }
    }
}
