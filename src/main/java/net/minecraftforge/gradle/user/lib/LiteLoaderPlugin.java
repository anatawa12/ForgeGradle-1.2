package net.minecraftforge.gradle.user.lib;

import net.minecraftforge.gradle.ArchiveTaskHelper;
import net.minecraftforge.gradle.GradleConfigurationException;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.LiteLoaderJson;
import net.minecraftforge.gradle.json.LiteLoaderJson.Artifact;
import net.minecraftforge.gradle.json.LiteLoaderJson.VersionObject;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.abstractutil.EtagDownloadTask;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.user.UserConstants;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class LiteLoaderPlugin extends UserLibBasePlugin {
    private Artifact llArtifact;

    private static final String EXTENSION = "litemod";

    @Override
    public void applyPlugin() {
        super.applyPlugin();
        commonApply();

        // change main output to litemod
        ArchiveTaskHelper.setExtension((Jar) project.getTasks().getByName("jar"), EXTENSION);
    }

    @Override
    public void applyOverlayPlugin() {
        // add in extension
        project.getExtensions().create(actualApiName(), getExtensionClass(), this);

        // ensure that this lib goes everywhere MC goes. its a required lib after all.
        Configuration config = project.getConfigurations().create(actualApiName());
        project.getConfigurations().getByName(UserConstants.CONFIG_MC).extendsFrom(config);

        // override run configs
        CreateStartTask starter = (CreateStartTask) project.getTasks().getByName("makeStart");
        starter.addReplacement("@@BOUNCERCLIENT@@", delayedString(getClientRunClass()));
        starter.addReplacement("@@BOUNCERSERVER@@", delayedString(getServerRunClass()));

        // packaging
        configurePackaging();

        // ensure we get basic things from the other extension
        project.afterEvaluate(arg0 -> getOverlayExtension().copyFrom(otherPlugin.getExtension()));

        commonApply();
    }

    @SuppressWarnings("rawtypes")
    protected void configurePackaging() {
        String cappedApiName = Character.toUpperCase(actualApiName().charAt(0)) + actualApiName().substring(1);
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

        // create apiJar task
        Jar jarTask = makeTask("jar" + cappedApiName, Jar.class);
        jarTask.from(javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput());
        ArchiveTaskHelper.setClassifier(jarTask, actualApiName());
        ArchiveTaskHelper.setExtension(jarTask, EXTENSION);

        // configure otherPlugin task to have a classifier
        ArchiveTaskHelper.setClassifier((Jar) project.getTasks().getByName("jar"), ((UserBasePlugin) otherPlugin).getApiName());

        //  configure reobf for litemod
        ((ReobfTask) project.getTasks().getByName("reobf")).reobf(jarTask, spec -> {
            spec.setSrgMcp();

            JavaPluginConvention javaConv1 = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
            spec.setClasspath(javaConv1.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getCompileClasspath());
        });

        project.getArtifacts().add("archives", jarTask);
    }

    private void commonApply() {
        // add repo
        project.allprojects(proj -> addMavenRepo(proj, "liteloaderRepo", "https://dl.liteloader.com/versions/"));

        final DelayedFile json = delayedFile("{CACHE_DIR}/minecraft/liteloader.json");

        {

            EtagDownloadTask task = makeTask("getLiteLoaderJson", EtagDownloadTask.class);
            task.setUrl("https://dl.liteloader.com/versions/versions.json");
            task.setFile(json);
            task.setDieWithError(false);

            // make sure it happens sometime during the build.
            project.getTasks().getByName("setupCIWorkspace").dependsOn(task);
            project.getTasks().getByName("setupDevWorkspace").dependsOn(task);
            project.getTasks().getByName("setupDecompWorkspace").dependsOn(task);

            task.doLast(arg0 -> {
                EtagDownloadTask task1 = (EtagDownloadTask) arg0;
                try {
                    readJsonDep(task1.getFile());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        project.afterEvaluate(arg0 -> {
            if (json.call().exists()) {
                try {
                    readJsonDep(json.call());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void readJsonDep(File json) throws IOException {
        if (llArtifact != null) {
            // its already set.. why parse again?
            return;
        }

        String mcVersion = delayedString("{MC_VERSION}").call();

        LiteLoaderJson loaded = JsonFactory.loadLiteLoaderJson(json);
        VersionObject obj = loaded.versions.get(mcVersion);
        if (obj == null)//|| !obj.latest.hasMcp())
            throw new GradleConfigurationException("LiteLoader does not have an ForgeGradle compatible edition for Minecraft " + mcVersion);

        llArtifact = obj.latest;

        // add the dependency.
        project.getLogger().debug("LiteLoader dep: " + llArtifact.getMcpDepString());
        project.getDependencies().add(actualApiName(), llArtifact.getMcpDepString());
    }

    @Override
    protected String getClientRunClass() {
        return "com.mumfrey.liteloader.debug.Start";
    }

    @Override
    protected Iterable<String> getClientRunArgs() {
        return new ArrayList<>(0);
    }

    @Override
    protected String getServerRunClass() {
        return "net.minecraft.server.MinecraftServer";
    }

    @Override
    protected Iterable<String> getServerRunArgs() {
        return new ArrayList<>(0);
    }

    @Override
    String actualApiName() {
        return "liteloader";
    }

    @Override
    public final boolean canOverlayPlugin() {
        return true;
    }

    @Override
    protected String getClientTweaker() {
        return "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
    }

    @Override
    protected String getServerTweaker() {
        return ""; // umm...
    }
}
