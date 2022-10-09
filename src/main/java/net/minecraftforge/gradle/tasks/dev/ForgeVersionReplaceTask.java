package net.minecraftforge.gradle.tasks.dev;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

public class ForgeVersionReplaceTask extends DefaultTask {
    DelayedFile outputFile;
    DelayedString replacement;

    @TaskAction
    public void doTask() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String line : Files.readAllLines(getOutputFile().toPath(), Charset.defaultCharset())) {
            if (line.contains("public static final int") && line.contains("buildVersion")) {
                builder.append(line.split("=")[0]).append("= ").append(getReplacement()).append(";\n");
            } else {
                builder.append(line).append('\n');
            }
        }
        Files.write(getOutputFile().toPath(), builder.toString().getBytes(Charset.defaultCharset()));
    }

    public void setOutputFile(DelayedFile output) {
        this.outputFile = output;
    }

    @InputFile
    public File getOutputFile() {
        return outputFile.call();
    }

    public void setReplacement(DelayedString value) {
        this.replacement = value;
    }

    @Input
    public String getReplacement() {
        return replacement.call();
    }
}
