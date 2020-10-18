package net.minecraftforge.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ArchiveTaskHelper {
    private static AbstractArchiveTaskHelperBack back = GradleVersionUtils.choose("5.1",
            new GradleVersionUtils.Callable<AbstractArchiveTaskHelperBack>() {
                @Override
                public AbstractArchiveTaskHelperBack call() {
                    return new AbstractArchiveTaskHelperBackImplOld();
                }
            },
            new GradleVersionUtils.Callable<AbstractArchiveTaskHelperBack>() {
                @Override
                public AbstractArchiveTaskHelperBack call() {
                    return new AbstractArchiveTaskHelperBackImplNew();
                }
            });

    private ArchiveTaskHelper() {
    }

    public static File getArchivePath(AbstractArchiveTask task) {
        return back.getArchivePath(task);
    }

    public static File getDestinationDir(AbstractArchiveTask task) {
        return back.getDestinationDir(task);
    }

    public static void setDestinationDir(AbstractArchiveTask task, File destinationDir) {
        back.setDestinationDir(task, destinationDir);
    }

    public static String getBaseName(AbstractArchiveTask task) {
        return back.getStringProperty(task, StringProperties.BaseName);
    }

    public static void setBaseName(AbstractArchiveTask task, String value) {
        back.setStringProperty(task, StringProperties.BaseName, value);
    }

    public static String getAppendix(AbstractArchiveTask task) {
        return back.getStringProperty(task, StringProperties.Appendix);
    }

    public static void setAppendix(AbstractArchiveTask task, String value) {
        back.setStringProperty(task, StringProperties.Appendix, value);
    }

    public static String getVersion(AbstractArchiveTask task) {
        return back.getStringProperty(task, StringProperties.Version);
    }

    public static void setVersion(AbstractArchiveTask task, String value) {
        back.setStringProperty(task, StringProperties.Version, value);
    }

    public static String getClassifier(AbstractArchiveTask task) {
        return back.getStringProperty(task, StringProperties.Classifier);
    }

    public static void setClassifier(AbstractArchiveTask task, String value) {
        back.setStringProperty(task, StringProperties.Classifier, value);
    }

    public static String getExtension(AbstractArchiveTask task) {
        return back.getStringProperty(task, StringProperties.Extension);
    }

    public static void setExtension(AbstractArchiveTask task, String value) {
        back.setStringProperty(task, StringProperties.Extension, value);
    }

    public static String getArchiveName(AbstractArchiveTask task) {
        return back.getStringProperty(task, StringProperties.ArchiveName);
    }

    public static void setArchiveName(AbstractArchiveTask task, String value) {
        back.setStringProperty(task, StringProperties.ArchiveName, value);
    }

    private enum StringProperties {
        BaseName,
        Appendix,
        Version,
        Classifier,
        Extension,
        ArchiveName("getArchiveFileName"),
        ;
        Method forOldGetMethod;
        Method forOldSetMethod;
        Method forNewMethod;

        StringProperties() {
            init("get" + name(), "set" + name(), "getArchive" + name());
        }

        StringProperties(String oldGetName, String oldSetName, String newName) {
            init(oldGetName, oldSetName, newName);
        }

        StringProperties(String newName) {
            init("get" + name(), "set" + name(), newName);
        }

        private void init(String oldGetName, String oldSetName, String newName) {
            try {
                forOldGetMethod = AbstractArchiveTask.class.getMethod(oldGetName);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            try {
                forOldSetMethod = AbstractArchiveTask.class.getMethod(oldSetName, String.class);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            try {
                forNewMethod = AbstractArchiveTask.class.getMethod(newName);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    private interface AbstractArchiveTaskHelperBack {
        File getArchivePath(AbstractArchiveTask task);

        File getDestinationDir(AbstractArchiveTask task);

        void setDestinationDir(AbstractArchiveTask task, File destinationDir);

        String getStringProperty(AbstractArchiveTask task, StringProperties prop);

        void setStringProperty(AbstractArchiveTask task, StringProperties prop, String value);
    }

    private static class AbstractArchiveTaskHelperBackImplOld implements AbstractArchiveTaskHelperBack {
        @Override
        public File getArchivePath(AbstractArchiveTask task) {
            return task.getArchivePath();
        }

        @Override
        public File getDestinationDir(AbstractArchiveTask task) {
            return task.getDestinationDir();
        }

        @Override
        public void setDestinationDir(AbstractArchiveTask task, File destinationDir) {
            task.setDestinationDir(destinationDir);
        }

        @Override
        public String getStringProperty(AbstractArchiveTask task, StringProperties prop) {
            return ArchiveTaskHelper.call(prop.forOldGetMethod, task);
        }

        @Override
        public void setStringProperty(AbstractArchiveTask task, StringProperties prop, String value) {
            ArchiveTaskHelper.call(prop.forOldSetMethod, task, value);
        }
    }

    // @since 5.1
    private static class AbstractArchiveTaskHelperBackImplNew implements AbstractArchiveTaskHelperBack {
        static Method getArchiveFile;
        static Method getDestinationDirectory;

        static {
            try {
                getArchiveFile = AbstractArchiveTask.class.getMethod("getArchiveFile");
                getDestinationDirectory = AbstractArchiveTask.class.getMethod("getDestinationDirectory");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public File getArchivePath(AbstractArchiveTask task) {
            return ArchiveTaskHelper.<Provider<RegularFile>>call(getArchiveFile, task).get().getAsFile();
        }

        @Override
        public File getDestinationDir(AbstractArchiveTask task) {
            return ArchiveTaskHelper.<DirectoryProperty>call(getDestinationDirectory, task).getAsFile().get();
        }

        @Override
        public void setDestinationDir(AbstractArchiveTask task, File destinationDir) {
            ArchiveTaskHelper.<DirectoryProperty>call(getDestinationDirectory, task)
                    .set(task.getProject().file(destinationDir));
        }

        @Override
        public String getStringProperty(AbstractArchiveTask task, StringProperties prop) {
            return ArchiveTaskHelper.<Property<String>>call(prop.forNewMethod, task).getOrNull();
        }

        @Override
        public void setStringProperty(AbstractArchiveTask task, StringProperties prop, String value) {
            ArchiveTaskHelper.<Property<String>>call(prop.forNewMethod, task).set(value);
        }
    }

    private static <T> T call(Method method, Object self, Object... args) {
        try {
            return (T) method.invoke(self, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
