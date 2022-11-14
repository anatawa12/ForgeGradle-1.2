package net.minecraftforge.gradle.tasks.abstractutil;

import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import net.minecraftforge.gradle.GradleVersionUtils;
import net.minecraftforge.gradle.delayed.DelayedFile;
import org.apache.shiro.util.AntPathMatcher;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CacheableTask
public class ExtractTask extends DefaultTask {
    private final AntPathMatcher antMatcher = new AntPathMatcher();

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    private final LinkedHashSet<DelayedFile> sourcePaths = new LinkedHashSet<>();

    @Input
    private final List<String> excludes = new LinkedList<>();

    @Input
    private final List<Closure<Boolean>> excludeCalls = new LinkedList<>();

    @Input
    private final List<String> includes = new LinkedList<>();

    @Input
    private boolean includeEmptyDirs = true;

    private boolean clean = false;

    @OutputDirectory
    private DelayedFile destinationDir = null;

    public ExtractTask() {
        getOutputs().doNotCacheIf("Old gradle version", e -> GradleVersionUtils.isBefore("5.3"));
    }

    @TaskAction
    public void doTask() throws IOException {
        File dest = destinationDir.call();

        if (shouldClean()) {
            delete(dest);
        }

        dest.mkdirs();

        for (DelayedFile source : sourcePaths) {
            getLogger().debug("Extracting: " + source);

            try (ZipFile input = new ZipFile(source.call())) {
                Enumeration<? extends ZipEntry> itr = input.entries();

                while (itr.hasMoreElements()) {
                    ZipEntry entry = itr.nextElement();
                    if (shouldExtract(entry.getName())) {
                        File out = new File(dest, entry.getName());
                        getLogger().debug("  " + out);
                        if (entry.isDirectory()) {
                            if (includeEmptyDirs && !out.exists()) {
                                out.mkdirs();
                            }
                        } else {
                            File outParent = out.getParentFile();
                            if (!outParent.exists()) {
                                outParent.mkdirs();
                            }

                            FileOutputStream fos = new FileOutputStream(out);
                            InputStream ins = input.getInputStream(entry);

                            ByteStreams.copy(ins, fos);

                            fos.close();
                            ins.close();
                        }
                    }
                }
            }
        }
    }

    private void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        f.delete();
    }


    private boolean shouldExtract(String path) {
        for (String exclude : excludes) {
            if (antMatcher.matches(exclude, path)) {
                return false;
            }
        }

        for (Closure<Boolean> exclude : excludeCalls) {
            if (exclude.call(path)) {
                return false;
            }
        }

        for (String include : includes) {
            if (antMatcher.matches(include, path)) {
                return true;
            }
        }

        return includes.size() == 0; //If it gets to here, then it matches nothing. default to true, if no includes were specified
    }

    public ExtractTask from(DelayedFile... paths) {
        sourcePaths.addAll(Arrays.asList(paths));
        return this;
    }

    public ExtractTask into(DelayedFile target) {
        destinationDir = target;
        return this;
    }

    public ExtractTask setDestinationDir(DelayedFile target) {
        destinationDir = target;
        return this;
    }

    public File getDestinationDir() {
        return destinationDir.call();
    }

    public List<String> getIncludes() {
        return includes;
    }

    public ExtractTask include(String... paterns) {
        Collections.addAll(includes, paterns);
        return this;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public ExtractTask exclude(String... paterns) {
        Collections.addAll(excludes, paterns);
        return this;
    }

    public List<Closure<Boolean>> getExcludeCalls() {
        return excludeCalls;
    }

    public void exclude(Closure<Boolean> c) {
        excludeCalls.add(c);
    }

    public FileCollection getSourcePaths() {
        FileCollection collection = createFileCollection();

        for (DelayedFile file : sourcePaths)
            collection = collection.plus(createFileCollection(file));

        return collection;
    }

    private FileCollection createFileCollection(Object... paths) {
        return GradleVersionUtils.choose("5.3", () -> getProject().files(paths), () -> getInjectedObjectFactory().fileCollection().from(paths));
    }

    @Inject
    protected ObjectFactory getInjectedObjectFactory() {
        throw new IllegalStateException("must be injected");
    }

    public boolean isIncludeEmptyDirs() {
        return includeEmptyDirs;
    }

    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        this.includeEmptyDirs = includeEmptyDirs;
    }

    public boolean shouldClean() {
        return clean;
    }

    @Input
    public boolean isClean() {
        return clean;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }
}
