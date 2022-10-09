package net.minecraftforge.gradle.tasks.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.gradle.FileUtils;
import net.minecraftforge.gradle.delayed.DelayedFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public class VersionJsonTask extends DefaultTask {
    private static final Gson GSON_FORMATTER = new GsonBuilder().setPrettyPrinting().create();

    @InputFile
    DelayedFile input;

    @OutputFile
    DelayedFile output;

    @SuppressWarnings("unchecked")
    @TaskAction
    public void doTask() throws IOException {
        String data = FileUtils.readString(getInput(), StandardCharsets.UTF_8);
        Map<String, Object> json = (Map<String, Object>) new Gson().fromJson(data, Map.class);
        json = (Map<String, Object>) json.get("versionInfo");
        data = GSON_FORMATTER.toJson(json);
        Files.write(getOutput().toPath(), data.getBytes());
    }

    public File getInput() {
        return input.call();
    }

    public void setInput(DelayedFile input) {
        this.input = input;
    }

    public File getOutput() {
        return output.call();
    }

    public void setOutput(DelayedFile output) {
        this.output = output;
    }

}
