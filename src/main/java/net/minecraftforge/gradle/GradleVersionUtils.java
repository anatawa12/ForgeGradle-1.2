package net.minecraftforge.gradle;

import org.gradle.util.GradleVersion;

public class GradleVersionUtils {
    /**
     * @param versionName includes this version
     * @param action the action runs if gradle version is equals to or after {@code versionName}.
     */
    public static void ifAfter(String versionName, Runnable action) {
        GradleVersion gradleVersion = GradleVersion.current();
        GradleVersion version = GradleVersion.version(versionName);

        if (gradleVersion.compareTo(version) >= 0) {
            action.run();
        }
    }

    /**
     * @param versionName excludes this version
     * @param action the action runs if gradle version is equals to or after {@code versionName}.
     */
    public static void ifBefore(String versionName, Runnable action) {
        GradleVersion gradleVersion = GradleVersion.current();
        GradleVersion version = GradleVersion.version(versionName);

        if (gradleVersion.compareTo(version) < 0) {
            action.run();
        }
    }
    /**
     * same version includes after
     * @param versionName includes this version
     */
    public static <T> T choose(String versionName, Callable<? extends T> before, Callable<? extends T> after) {
        if (isBefore(versionName)) {
            return before.call();
        } else {
            return after.call();
        }
    }

    /**
     * same version includes after
     * @param versionName includes this version
     */
    public static boolean isBefore(String versionName) {
        GradleVersion gradleVersion = GradleVersion.current();
        GradleVersion version = GradleVersion.version(versionName);

        if (gradleVersion.compareTo(version) < 0) {
            return true;
        } else {
            return false;
        }
    }

    public interface Callable<T> {
        T call();
    }
}
