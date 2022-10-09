package net.minecraftforge.gradle.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import groovy.lang.Closure;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map.Entry;

@CacheableTask
public class CreateStartTask extends DefaultTask {
    private final ConfigurationContainer configurations = getProject().getConfigurations();
    @Input
    HashMap<String, String> resources = new HashMap<>();

    HashMap<String, Object> replacements = new HashMap<>();

    @OutputDirectory
    private DelayedFile startOut;

    private String classpath;
    private boolean compile;

    @TaskAction
    public void doStuff() throws IOException {
        // resolve the replacements
        for (Entry<String, Object> entry : replacements.entrySet()) {
            replacements.put(entry.getKey(), resolveString(entry.getValue()));
        }

        // set the output of the files
        File resourceDir = compile ? new File(getTemporaryDir(), "extracted") : getStartOut();

        // replace and extract
        for (Entry<String, String> resEntry : resources.entrySet()) {
            String out = resEntry.getValue();
            for (Entry<String, Object> replacement : replacements.entrySet()) {
                out = out.replace(replacement.getKey(), (String) replacement.getValue());
            }

            // write file
            File outFile = new File(resourceDir, resEntry.getKey());
            outFile.getParentFile().mkdirs();
            Files.write(outFile.toPath(), out.getBytes(StandardCharsets.UTF_8));
        }

        // now compile, if im compiling.
        if (compile) {
            File compiled = getStartOut();
            compiled.mkdirs();

            this.getAnt().invokeMethod("javac", ImmutableMap.builder()
                    .put("srcDir", resourceDir.getCanonicalPath())
                    .put("destDir", compiled.getCanonicalPath())
                    .put("failonerror", true)
                    .put("includeantruntime", false)
                    .put("classpath", configurations.getByName(classpath).getAsPath())
                    .put("encoding", StandardCharsets.UTF_8)
                    .put("source", "1.6")
                    .put("target", "1.6")
                    .build());
        }

    }

    private String resolveString(Object obj) throws IOException {
        if (obj instanceof Closure)
            return resolveString(((Closure<?>) obj).call());
        else if (obj instanceof File)
            return ((File) obj).getCanonicalPath().replace('\\', '/');
        else
            return obj.toString();
    }

    private String getResource(URL resource) {
        try {
            return Resources.toString(resource, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Use Resources.getResource() for this
     *
     * @param resource resource URL
     * @param outName  name of the resource after its been extracted
     */
    public void addResource(URL resource, String outName) {
        resources.put(outName, getResource(resource));
    }

    public void removeResource(String key) {
        resources.remove(key);
    }

    public void addResource(String resource, String outName) {
        this.addResource(Constants.getResource(resource), outName);
    }

    public void addResource(String thing) {
        this.addResource(thing, thing);
    }

    public void addReplacement(String token, Object replacement) {
        replacements.put(token, replacement);
    }

    public void compileResources(String classpathConfig) {
        compile = true;
        classpath = classpathConfig;
    }

    public File getStartOut() {
        File dir = startOut.call();
        if (!dir.exists())
            dir.mkdirs();
        return startOut.call();
    }

    public void setStartOut(DelayedFile outputFile) {
        this.startOut = outputFile;
    }

    public HashMap<String, String> getResources() {
        return resources;
    }

    @Input
    public HashMap<String, String> getReplacements() throws IOException {
        HashMap<String, String> result = new HashMap<>();
        for (Entry<String, Object> stringObjectEntry : replacements.entrySet()) {
            result.put(stringObjectEntry.getKey(), resolveString(stringObjectEntry.getValue()));
        }
        return result;
    }
}
