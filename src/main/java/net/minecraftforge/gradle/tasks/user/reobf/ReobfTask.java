package net.minecraftforge.gradle.tasks.user.reobf;

import com.google.common.base.Throwables;
import groovy.lang.Closure;
import net.minecraftforge.gradle.FileUtils;
import net.minecraftforge.gradle.GradleVersionUtils;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.delayed.DelayedThingy;
import net.minecraftforge.gradle.extrastuff.ReobfExceptor;
import net.minecraftforge.gradle.user.UserConstants;
import net.minecraftforge.gradle.user.UserExtension;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class ReobfTask extends DefaultTask {
    final private DomainObjectSet<ObfArtifact> obfOutput = GradleVersionUtils.choose("5.5",
            ReobfTask::getInternalDeprecatedDefaultDomain,
            () -> getInjectedObjectFactory().domainObjectSet(ObfArtifact.class));
    private final ExtensionContainer extensions = getProject().getExtensions();

    @SuppressWarnings("unchecked")
    private static DomainObjectSet<ObfArtifact> getInternalDeprecatedDefaultDomain() {
        try {
            Class<?> aClass = Class.forName("org.gradle.api.internal.DefaultDomainObjectSet");
            return (DomainObjectSet<ObfArtifact>) aClass.getConstructor(Class.class).newInstance(ObfArtifact.class);
        } catch (Throwable t) {
            Throwables.throwIfUnchecked(t);
            throw new IllegalStateException(t);
        }
    }

    @Input
    private boolean useRetroGuard = false;

    @Input
    private DelayedString mcVersion;

    @InputFile
    private DelayedFile srg;

    @Optional
    @InputFile
    private DelayedFile fieldCsv;

    @Optional
    @InputFile
    private DelayedFile methodCsv;

    @Optional
    @InputFile
    private DelayedFile exceptorCfg;

    @Optional
    @InputFile
    private DelayedFile deobfFile;

    @Optional
    @InputFile
    private DelayedFile recompFile;

    @Input
    private List<String> extraSrg = new ArrayList<>();

    @Input
    private boolean copyEmptyDirectories = false;

    private List<Object> extraSrgFiles = new ArrayList<>();

    public ReobfTask() {
        super();

        getInputs().files((Callable<FileCollection>) this::getFilesToObfuscate);
        getOutputs().files((Callable<FileCollection>) this::getObfuscatedFiles);
    }

    @Inject
    protected ObjectFactory getInjectedObjectFactory() {
        throw new IllegalStateException("must be injected");
    }

    public void reobf(Task task, Action<ArtifactSpec> artifactSpec) {
        if (!(task instanceof AbstractArchiveTask)) {
            throw new InvalidUserDataException("You cannot reobfuscate tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)");
        }

        ArtifactSpec spec = new ArtifactSpec((AbstractArchiveTask) task);
        artifactSpec.execute(spec);

        dependsOn(task);
        addArtifact(new ObfArtifact(new DelayedThingy(task), spec, this));
    }

    public void reobf(Task task, Closure<Object> artifactSpec) {
        if (!(task instanceof AbstractArchiveTask)) {
            throw new InvalidUserDataException("You cannot reobfuscate tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)");
        }

        ArtifactSpec spec = new ArtifactSpec((AbstractArchiveTask) task);
        artifactSpec.call(spec);

        dependsOn(task);
        addArtifact(new ObfArtifact(new DelayedThingy(task), spec, this));
    }

    /**
     * Configures the task to sign the archive produced for each of the given tasks (which must be archive tasks).
     *
     * @param tasks tasks
     */
    public void reobf(Task... tasks) {
        for (Task task : tasks) {
            if (!(task instanceof AbstractArchiveTask)) {
                throw new InvalidUserDataException("You cannot reobfuscate tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)");
            }

            dependsOn(task);
            addArtifact(new ObfArtifact(new DelayedThingy(task), new ArtifactSpec((AbstractArchiveTask) task), this));
        }
    }

    public void reobf(PublishArtifact publishArtifact, Action<ArtifactSpec> artifactSpec) {
        ArtifactSpec spec = new ArtifactSpec(publishArtifact, getProject());
        artifactSpec.execute(spec);

        dependsOn(publishArtifact);
        addArtifact(new ObfArtifact(publishArtifact, spec, this));
    }

    /**
     * Configures the task to sign each of the given artifacts
     *
     * @param publishArtifact artifact
     * @param artifactSpec    configuration closure
     */
    public void reobf(PublishArtifact publishArtifact, Closure<Object> artifactSpec) {
        ArtifactSpec spec = new ArtifactSpec(publishArtifact, getProject());
        artifactSpec.call(spec);

        dependsOn(publishArtifact);
        addArtifact(new ObfArtifact(publishArtifact, spec, this));
    }

    /**
     * Configures the task to sign each of the given artifacts
     *
     * @param publishArtifacts artifacts
     */
    public void reobf(PublishArtifact... publishArtifacts) {
        for (PublishArtifact publishArtifact : publishArtifacts) {
            dependsOn(publishArtifact);
            addArtifact(new ObfArtifact(publishArtifact, new ArtifactSpec(publishArtifact, getProject()), this));
        }
    }

    public void reobf(File file, Action<ArtifactSpec> artifactSpec) {
        ArtifactSpec spec = new ArtifactSpec(file, getProject());
        artifactSpec.execute(spec);

        addArtifact(new ObfArtifact(file, spec, this));
    }

    /**
     * Configures the task to reobf each of the given files
     *
     * @param file         file
     * @param artifactSpec configuration closure
     */
    public void reobf(File file, Closure<Object> artifactSpec) {
        ArtifactSpec spec = new ArtifactSpec(file, getProject());
        artifactSpec.call(spec);

        addArtifact(new ObfArtifact(file, spec, this));
    }

    /**
     * Configures the task to reobf each of the given files
     *
     * @param files files
     */
    public void reobf(File... files) {
        for (File file : files) {
            addArtifact(new ObfArtifact(file, new ArtifactSpec(file, getProject()), this));
        }
    }

    /**
     * Configures the task to obfuscate every artifact of the given configurations
     *
     * @param configuration config
     * @param artifactSpec  configuration closure
     */
    public void reobf(Configuration configuration, final Closure<Object> artifactSpec) {
        configuration.getAllArtifacts().all(artifact -> {
            if (!(artifact instanceof ObfArtifact)) {
                reobf(artifact, artifactSpec);
            }
        });

        configuration.getAllArtifacts().whenObjectRemoved(artifact -> {
            ObfArtifact removed = null;
            for (ObfArtifact it : obfOutput) {
                if (it.toObfArtifact == artifact) {
                    removed = it;
                    break;
                }
            }

            if (removed != null)
                obfOutput.remove(removed);
        });
    }

    /**
     * Configures the task to obfuscate every artifact of the given configurations
     *
     * @param configurations configs
     */
    public void reobf(Configuration... configurations) {
        for (Configuration configuration : configurations) {
            configuration.getAllArtifacts().all(artifact -> {
                if (!(artifact instanceof ObfArtifact)) {
                    reobf(artifact);
                }
            });

            configuration.getAllArtifacts().whenObjectRemoved(artifact -> {
                ObfArtifact removed = null;
                for (ObfArtifact it : obfOutput) {
                    if (it.toObfArtifact == artifact) {
                        removed = it;
                        break;
                    }
                }

                if (removed != null)
                    obfOutput.remove(removed);
            });
        }
    }

    /**
     * Generates the signature files.
     *
     * @throws Exception Becuase of FileIO and because retroguard throws an Exception
     */
    @TaskAction
    public void doTask() throws Exception {
        // do stuff.
        ReobfExceptor exc = null;
        File srg = Files.createTempFile(getTemporaryDir().toPath(), "reobf-default", ".srg").toFile();
        File extraSrg = Files.createTempFile(getTemporaryDir().toPath(), "reobf-extra", ".srg").toFile();

        UserExtension ext = (UserExtension) extensions.getByName(Constants.EXT_NAME_MC);

        if (ext.isDecomp()) {
            exc = getExceptor();
            exc.buildSrg(getSrg(), srg);
        } else
            Files.copy(getSrg().toPath(), srg.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // generate extraSrg
        {
            if (!extraSrg.exists()) {
                extraSrg.getParentFile().mkdirs();
                extraSrg.createNewFile();
            }

            BufferedWriter writer = Files.newBufferedWriter(extraSrg.toPath());
            for (String line : getExtraSrg()) {
                writer.write(line);
                writer.newLine();
            }

            writer.flush();
            writer.close();
        }

        for (ObfArtifact obf : getObfuscated())
            obf.generate(exc, srg, extraSrg, getExtraSrgFiles(), getCopyEmptyDirectories());

        // cleanup
        srg.delete();
        extraSrg.delete();
    }

    private ReobfExceptor getExceptor() throws IOException {
        ReobfExceptor exc = new ReobfExceptor();
        exc.deobfJar = getDeobfFile();
        exc.toReobfJar = getRecompFile();
        exc.excConfig = getExceptorCfg();
        exc.fieldCSV = getFieldCsv();
        exc.methodCSV = getMethodCsv();

        exc.doFirstThings();

        return exc;
    }

    private void addArtifact(ObfArtifact artifact) {
        obfOutput.add(artifact);
    }

    /**
     * The signatures generated by this task.
     */
    @Internal
    DomainObjectSet<ObfArtifact> getObfuscated() {
        return obfOutput;
    }

    /**
     * All of the files that will be signed by this task.
     */
    @InputFiles
    FileCollection getFilesToObfuscate() {
        List<File> collect = new ArrayList<>();

        for (ObfArtifact obf : getObfuscated()) {
            if (obf != null && obf.getToObf() != null)
                collect.add(obf.getToObf());
        }

        return getProject().files(collect);
    }

    /**
     * All of the signature files that will be generated by this operation.
     */
    @OutputFiles
    FileCollection getObfuscatedFiles() {
        List<File> collect = new ArrayList<>();

        for (ObfArtifact obf : getObfuscated()) {
            if (obf != null && obf.getFile() != null)
                collect.add(obf.getFile());
        }

        return getProject().files(collect);
    }

    public boolean getUseRetroGuard() {
        return useRetroGuard;
    }

    public void setUseRetroGuard(boolean useRG) {
        this.useRetroGuard = useRG;
    }

    public String getMcVersion() {
        return mcVersion == null ? null : mcVersion.call();
    }

    public void setMcVersion(DelayedString mcVersion) {
        this.mcVersion = mcVersion;
    }

    public File getDeobfFile() {
        return deobfFile == null ? null : deobfFile.call();
    }

    public void setDeobfFile(DelayedFile deobfFile) {
        this.deobfFile = deobfFile;
    }

    public File getRecompFile() {
        return recompFile == null ? null : recompFile.call();
    }

    public void setRecompFile(DelayedFile recompFile) {
        this.recompFile = recompFile;
    }

    public File getExceptorCfg() {
        return exceptorCfg == null ? null : exceptorCfg.call();
    }

    public void setExceptorCfg(DelayedFile file) {
        this.exceptorCfg = file;
    }

    public List<String> getExtraSrg() {
        return extraSrg;
    }

    public void setExtraSrg(List<String> extraSrg) {
        this.extraSrg = extraSrg;
    }

    public void addExtraSrgFile(Object thing) {
        extraSrgFiles.add(thing);
    }

    @InputFiles
    public FileCollection getExtraSrgFiles() {
        List<File> files = new ArrayList<>(extraSrgFiles.size());

        for (Object thing : getProject().files(extraSrgFiles)) {
            File f = getProject().file(thing);
            if (f.isDirectory()) {
                for (File nested : getProject().fileTree(f)) {
                    if ("srg".equalsIgnoreCase(FileUtils.getFileExtension(nested.getName()))) {
                        files.add(nested.getAbsoluteFile());
                    }
                }
            } else if ("srg".equalsIgnoreCase(FileUtils.getFileExtension(f.getName()))) {
                files.add(f.getAbsoluteFile());
            }
        }

        return getProject().files(files);
    }

    public File getSrg() {
        return srg.call();
    }

    public void setSrg(DelayedFile srg) {
        this.srg = srg;
    }

    public void setSrg(String srg) {
        this.srg = new DelayedFile(getProject(), srg);
    }

    public void setSrgSrg() {
        this.srg = new DelayedFile(getProject(), UserConstants.REOBF_SRG, ((UserExtension) extensions.getByName(Constants.EXT_NAME_MC)).plugin);
    }

    public void setSrgMcp() {
        this.srg = new DelayedFile(getProject(), UserConstants.REOBF_NOTCH_SRG, ((UserExtension) extensions.getByName(Constants.EXT_NAME_MC)).plugin);
    }

    public File getFieldCsv() {
        return fieldCsv == null ? null : fieldCsv.call();
    }

    public void setFieldCsv(DelayedFile fieldCsv) {
        this.fieldCsv = fieldCsv;
    }

    public File getMethodCsv() {
        return methodCsv == null ? null : methodCsv.call();
    }

    public void setMethodCsv(DelayedFile methodCsv) {
        this.methodCsv = methodCsv;
    }

    public boolean getCopyEmptyDirectories() {
        return copyEmptyDirectories;
    }

    public void setCopyEmptyDirectories(boolean copyEmptyDirectories) {
        this.copyEmptyDirectories = copyEmptyDirectories;
    }

    @Internal
    public DomainObjectSet<ObfArtifact> getObfOutput() {
        return obfOutput;
    }

}
