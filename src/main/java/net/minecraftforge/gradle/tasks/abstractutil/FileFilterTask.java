package net.minecraftforge.gradle.tasks.abstractutil;

import groovy.lang.Closure;
import groovy.util.MapEntry;
import net.minecraftforge.gradle.FileUtils;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

public class FileFilterTask extends DefaultTask {
    @InputFile
    DelayedFile inputFile;

    @OutputFile
    DelayedFile outputFile;

    ArrayList<MapEntry> replacements = new ArrayList<>();

    public FileFilterTask() {
        this.getOutputs().upToDateWhen(Constants.SPEC_FALSE);
    }

    @TaskAction
    public void doTask() throws IOException {
        String input = FileUtils.readString(getInputFile());

        for (MapEntry e : replacements) {
            input = input.replaceAll(toString(e.getKey()), toString(e.getValue()));
        }

        Files.write(getOutputFile().toPath(), input.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private String toString(Object obj) {
        if (obj instanceof Closure) {
            return ((Closure<String>) obj).call();
        } else {
            return (String) obj;
        }
    }

    public void addReplacement(Object search, Object replace) {
        replacements.add(new MapEntry(search, replace));
    }

    public void setInputFile(DelayedFile file) {
        this.inputFile = file;
    }

    public void setOutputFile(DelayedFile file) {
        this.outputFile = file;
    }

    public File getInputFile() {
        return inputFile.call();
    }

    public File getOutputFile() {
        return outputFile == null ? getInputFile() : outputFile.call();
    }
}
