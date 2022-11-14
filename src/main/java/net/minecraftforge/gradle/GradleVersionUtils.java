package net.minecraftforge.gradle;

import org.gradle.util.GradleVersion;

import java.util.function.Supplier;

public class GradleVersionUtils {
    public static void checkSupportedVersion() {
        GradleVersionUtils.ifBefore("4.0", () -> {
            throw new IllegalStateException("Gradle 3.x or older is not supported. Please upgrade to 4.x or later, including 8.x version.");
        });
    }

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
    public static <T> T choose(String versionName, Supplier<? extends T> before, Supplier<? extends T> after) {
        if (isBefore(versionName)) {
            return before.get();
        } else {
            return after.get();
        }
    }

    /**
     * same version includes after
     * @param versionName includes this version
     */
    public static boolean isBefore(String versionName) {
        GradleVersion gradleVersion = GradleVersion.current();
        GradleVersion version = GradleVersion.version(versionName);

        return gradleVersion.compareTo(version) < 0;
    }
}
