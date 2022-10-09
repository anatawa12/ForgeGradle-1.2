package net.minecraftforge.gradle.tasks;

import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import net.minecraftforge.gradle.delayed.DelayedFile;
import org.apache.shiro.util.AntPathMatcher;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CacheableTask
public class ExtractConfigTask extends DefaultTask {
    private final AntPathMatcher antMatcher = new AntPathMatcher();
    private final ConfigurationContainer configurations = getProject().getConfigurations();

    @Input
    private String config;

    @Input
    private final List<String> excludes = new LinkedList<>();

    @Input
    private final List<Closure<Boolean>> excludeCalls = new LinkedList<>();

    @Input
    private final List<String> includes = new LinkedList<>();

    @OutputDirectory
    private DelayedFile out;

    @TaskAction
    public void doTask() throws IOException {
        File outDir = getOut();
        outDir.mkdirs();

        for (File source : getConfigFiles()) {
            getLogger().debug("Extracting: " + source);

            try (ZipFile input = new ZipFile(source)) {
                Enumeration<? extends ZipEntry> itr = input.entries();

                while (itr.hasMoreElements()) {
                    ZipEntry entry = itr.nextElement();
                    if (shouldExtract(entry.getName())) {
                        File outFile = new File(outDir, entry.getName());
                        getLogger().debug("  " + outFile);
                        if (!entry.isDirectory()) {
                            File outParent = outFile.getParentFile();
                            if (!outParent.exists()) {
                                outParent.mkdirs();
                            }

                            FileOutputStream fos = new FileOutputStream(outFile);
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

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getConfigFiles() {
        return configurations.getByName(config);
    }

    public File getOut() {
        return out.call();
    }

    public void setOut(DelayedFile out) {
        this.out = out;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public ExtractConfigTask include(String... paterns) {
        Collections.addAll(includes, paterns);
        return this;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public ExtractConfigTask exclude(String... paterns) {
        Collections.addAll(excludes, paterns);
        return this;
    }

    public List<Closure<Boolean>> getExcludeCalls() {
        return excludeCalls;
    }

    public void exclude(Closure<Boolean> c) {
        excludeCalls.add(c);
    }
}
