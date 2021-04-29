package net.minecraftforge.gradle.tasks;

import com.anatawa12.forge.gradle.separated.SeparatedLauncher;
import groovy.lang.Closure;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.*;

public class MergeJarsTask extends CachedTask {
    @InputFile
    private Closure<File> mergeCfg;

    @InputFile
    private Closure<File> client;

    @InputFile
    private Closure<File> server;

    @Input
    private DelayedString mcVersion;

    @OutputFile
    @Cached
    private Closure<File> outJar;

    @TaskAction
    public void doTask() throws IOException {
        new SeparatedLauncher("mergeJars")
                .arg(getMergeCfg())
                .arg(getClient())
                .arg(getServer())
                .arg(getMcVersion())
                .arg(getOutJar())
                .run(getProject());
    }

    public static byte[] getClassBytes(String name) throws IOException {
        throw new UnsupportedOperationException("this method should not be called by external classes.");
    }

    public File getClient() {
        return client.call();
    }

    public void setClient(Closure<File> client) {
        this.client = client;
    }

    public File getMergeCfg() {
        return mergeCfg.call();
    }

    public void setMergeCfg(Closure<File> mergeCfg) {
        this.mergeCfg = mergeCfg;
    }

    public File getOutJar() {
        return outJar.call();
    }

    public void setOutJar(Closure<File> outJar) {
        this.outJar = outJar;
    }

    public File getServer() {
        return this.server.call();
    }

    public void setServer(Closure<File> server) {
        this.server = server;
    }

    public String getMcVersion() {
        return mcVersion.call();
    }

    public void setMcVersion(DelayedString mcVersion) {
        this.mcVersion = mcVersion;
    }
}
