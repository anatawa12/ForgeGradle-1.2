package net.minecraftforge.gradle;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;

public class JavaExtensionHelper {
    private static final Extensions GETTERS_AND_SETTERS = GradleVersionUtils.choose("7.1",
            ConventionGetter::new,
            ExtensionGetter::new
    );

    private static final Extensions VERSION_GETTERS = GradleVersionUtils.choose("4.10",
            ConventionGetter::new,
            ExtensionGetter::new
    );
    interface Extensions {
        SourceSetContainer getSourceSets(Project project);

        void setSourceCompatibility(Project project, Object value);

        void setTargetCompatibility(Project project, Object value);

        JavaVersion getTargetCompatibility(Project project);
    }

    private static class ConventionGetter implements Extensions {
        @Override
        @Deprecated
        public SourceSetContainer getSourceSets(Project project) {
            return getType(project).getSourceSets();
        }

        @Override
        @Deprecated
        public void setSourceCompatibility(Project project, Object value) {
            getType(project).setSourceCompatibility(value);
        }

        @Override
        @Deprecated
        public void setTargetCompatibility(Project project, Object value) {
            getType(project).setTargetCompatibility(value);
        }

        @Override
        @Deprecated
        public JavaVersion getTargetCompatibility(Project project) {
            return getType(project).getTargetCompatibility();
        }

        @Deprecated
        public JavaPluginConvention getType(Project project) {
            return (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        }
    }

    private static class ExtensionGetter implements Extensions {
        @Override
        public SourceSetContainer getSourceSets(Project project) {
            return getType(project).getSourceSets();
        }

        @Override
        public void setSourceCompatibility(Project project, Object value) {
            getType(project).setSourceCompatibility(value);
        }

        @Override
        public void setTargetCompatibility(Project project, Object value) {
            getType(project).setTargetCompatibility(value);
        }

        @Override
        public JavaVersion getTargetCompatibility(Project project) {
            return getType(project).getTargetCompatibility();
        }

        public JavaPluginExtension getType(Project project) {
            return project.getExtensions().getByType(JavaPluginExtension.class);
        }
    }

    public static SourceSetContainer getSourceSet(Project project) {
        return GETTERS_AND_SETTERS.getSourceSets(project);
    }

    public static void setSourceCompatibility(Project project, Object value) {
        GETTERS_AND_SETTERS.setSourceCompatibility(project, value);
    }

    public static void setTargetCompatibility(Project project, Object value) {
        GETTERS_AND_SETTERS.setTargetCompatibility(project, value);
    }

    public static JavaVersion getTargetCompatibility(Project project) {
        return VERSION_GETTERS.getTargetCompatibility(project);
    }
}
