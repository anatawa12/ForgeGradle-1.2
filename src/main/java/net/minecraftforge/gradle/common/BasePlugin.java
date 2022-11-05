package net.minecraftforge.gradle.common;

import com.anatawa12.forge.gradle.separated.SeparatedLauncher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import groovy.lang.Closure;
import net.minecraftforge.gradle.*;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.MCVersionManifest;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.Version;
import net.minecraftforge.gradle.tasks.DownloadAssetsTask;
import net.minecraftforge.gradle.tasks.ExtractConfigTask;
import net.minecraftforge.gradle.tasks.ObtainFernFlowerTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;
import net.minecraftforge.gradle.tasks.abstractutil.EtagDownloadTask;
import org.gradle.api.*;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Delete;
import org.gradle.testfixtures.ProjectBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>, IDelayedResolver<K> {
    public Project project;
    @SuppressWarnings("rawtypes")
    public BasePlugin otherPlugin;
    public Version version;
    public AssetIndex assetIndex;

    @SuppressWarnings("rawtypes")
    @Override
    public final void apply(Project arg) {
        GradleVersionUtils.checkSupportedVersion();
        project = arg;

        // search for overlays..
        for (Plugin<?> p : project.getPlugins()) {
            if (p instanceof BasePlugin && p != this) {
                if (canOverlayPlugin()) {
                    project.getLogger().info("Applying Overlay");

                    // found another BasePlugin thats already applied.
                    // do only overlay stuff and return;
                    otherPlugin = (BasePlugin) p;
                    applyOverlayPlugin();
                    return;
                } else {
                    throw new GradleConfigurationException("Seems you are trying to apply 2 ForgeGradle plugins that are not designed to overlay... Fix your buildscripts.");
                }
            }
        }

        // logging
        {
            File projectCacheDir = project.getGradle().getStartParameter().getProjectCacheDir();
            if (projectCacheDir == null)
                projectCacheDir = new File(project.getProjectDir(), ".gradle");

            FileLogListenner listener = new FileLogListenner(projectCacheDir.toPath().resolve("gradle.log"));
            project.getLogging().addStandardOutputListener(listener);
            project.getLogging().addStandardErrorListener(listener);
            project.getGradle().addBuildListener(listener);
        }

        if (project.getBuildDir().getAbsolutePath().contains("!")) {
            project.getLogger().error("Build path has !, This will screw over a lot of java things as ! is used to denote archive paths, REMOVE IT if you want to continue");
            throw new RuntimeException("Build path contains !");
        }

        // extension objects
        project.getExtensions().create(Constants.EXT_NAME_MC, getExtensionClass(), this);
        project.getExtensions().create(Constants.EXT_NAME_JENKINS, JenkinsExtension.class, project);

        // repos
        project.allprojects(proj -> {
            // the forge's repository doesn't have pom file.
            addMavenRepo(proj, "forge", Constants.FORGE_MAVEN, false);
            proj.getRepositories().mavenCentral();
            addMavenRepo(proj, "minecraft", Constants.LIBRARY_URL);
        });

        // do Mcp Snapshots Stuff
        setVersionInfoJson();
        project.getConfigurations().create(Constants.CONFIG_MCP_DATA);

        // Separated module
        {
            project.getConfigurations().create(SeparatedLauncher.configurationName);
            String version = getVersionString();
            if (version.indexOf('-') >= 0) {
                // remove git sha
                version = version.substring(0, version.lastIndexOf('-'));
                project.getDependencies().add(SeparatedLauncher.configurationName,
                        "com.anatawa12.forge:separated:" + version);
            }
        }

        // after eval
        project.afterEvaluate(project -> {
            // dont continue if its already failed!
            if (project.getState().getFailure() != null)
                return;

            afterEvaluate();

            try {
                if (version != null) {
                    File index = delayedFile(Constants.ASSETS + "/indexes/" + version.getAssets() + ".json").call();
                    if (index.exists())
                        parseAssetIndex();
                }
            } catch (JsonSyntaxException | JsonIOException | IOException e) {
                throw new RuntimeException(e);
            }

            finalCall();
        });

        // some default tasks
        makeObtainTasks();

        // at last, apply the child plugins
        applyPlugin();
    }

    public abstract void applyPlugin();

    public abstract void applyOverlayPlugin();

    /**
     * return true if this plugin can be applied over another BasePlugin.
     *
     * @return TRUE if this can be applied upon another base plugin.
     */
    public abstract boolean canOverlayPlugin();

    protected abstract DelayedFile getDevJson();

    private static boolean displayBanner = true;

    private void setVersionInfoJson() {
        File jsonCache = Constants.cacheFile(project, "caches", "minecraft", "McpMappings.json");
        File etagFile = new File(jsonCache.getAbsolutePath() + ".etag");

        getExtension().mcpJson = JsonFactory.GSON.fromJson(
                getWithEtag(Constants.MCP_JSON_URL, jsonCache, etagFile),
                new TypeToken<Map<String, Map<String, int[]>>>() {}.getType());
    }

    public void afterEvaluate() {
        if (getExtension().mappingsSet()) {
            project.getDependencies().add(Constants.CONFIG_MCP_DATA, ImmutableMap.of(
                    "group", "de.oceanlabs.mcp",
                    "name", delayedString("mcp_{MAPPING_CHANNEL}").call(),
                    "version", delayedString("{MAPPING_VERSION}-{MC_VERSION}").call(),
                    "ext", "zip"
            ));
        }

        if (!displayBanner)
            return;
        Logger logger = this.project.getLogger();
        logger.lifecycle("#################################################");
        logger.lifecycle("         ForgeGradle {}        ", this.getVersionString());
        logger.lifecycle("   https://github.com/anatawa12/ForgeGradle-1.2  ");
        logger.lifecycle("#################################################");
        logger.lifecycle("               Powered by MCP {}               ", this.delayedString("{MCP_VERSION}"));
        //noinspection HttpUrlsUsage
        logger.lifecycle("             http://modcoderpack.com             ");
        logger.lifecycle("         by: Searge, ProfMobius, Fesh0r,         ");
        logger.lifecycle("         R4wk, ZeuX, IngisKahn, bspkrs           ");
        logger.lifecycle("#################################################");
        if (!hasMavenCentralBeforeJCenterInBuildScriptRepositories()) {
            logger.lifecycle("");
            logger.warn("The jcenter maven repository is going to be closed.");
            logger.warn("The fork of ForgeGradle by anatawa12 will use the maven central repository.");
            logger.warn("In the near future, this ForgeGradle will not be published onto the jcenter.");
            logger.warn("Please add the maven central repository to the repositories for");
            logger.warn("buildscript before or as a replacement of jcenter.");
        }
        if (!hasMavenMinecraftForgeBeforeFilesMinecraftForge(project.getBuildscript().getRepositories())
                || hasMavenMinecraftForgeBeforeFilesMinecraftForge(project.getRepositories())) {
            logger.lifecycle("");
            logger.warn("The minecraft forge's official maven repository has been moved to");
            logger.warn("https://maven.minecraftforge.net/. Currently redirection from previous location");
            logger.warn("previous location to new location is alive but we don't know");
            logger.warn("when it will stop so I especially recommend to change repository url.");
        }
        displayBanner = false;
    }

    private boolean hasMavenCentralBeforeJCenterInBuildScriptRepositories() {
        if (ProjectUtils.getBooleanProperty(project, "com.anatawa12.forge.gradle.no-maven-central-warn"))
            return true;
        URI mavenCentralUrl;
        try {
            mavenCentralUrl = project.uri(ArtifactRepositoryContainer.class
                    .getField("MAVEN_CENTRAL_URL").get(null));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        for (ArtifactRepository repository : project.getBuildscript().getRepositories()) {
            if (repository instanceof MavenArtifactRepository) {
                MavenArtifactRepository mvnRepo = (MavenArtifactRepository) repository;
                // requires before the jcenter
                if (mvnRepo.getUrl().toString().equals("https://jcenter.bintray.com/"))
                    return false;
                if (mvnRepo.getUrl().equals(mavenCentralUrl))
                    return true;
            }
        }
        return false;
    }

    private boolean hasMavenMinecraftForgeBeforeFilesMinecraftForge(RepositoryHandler repositories) {
        if (ProjectUtils.getBooleanProperty(project, "com.anatawa12.forge.gradle.no-forge-maven-warn"))
            return true;
        for (ArtifactRepository repository : repositories) {
            if (repository instanceof MavenArtifactRepository) {
                MavenArtifactRepository mvnRepo = (MavenArtifactRepository) repository;
                // requires before the jcenter
                if (mvnRepo.getUrl().toString().contains("//files.minecraftforge.net/maven"))
                    return false;
                if (mvnRepo.getUrl().toString().equals("https://maven.minecraftforge.net/"))
                    return true;
            }
        }
        return false;
    }

    private String getVersionString() {
        String version = this.getClass().getPackage().getImplementationVersion();
        if (Strings.isNullOrEmpty(version)) {
            version = "unknown version";
        }

        return version;
    }

    public void finalCall() {
    }

    @SuppressWarnings("serial")
    private void makeObtainTasks() {
        // download tasks
        DownloadTask task;

        EtagDownloadTask etagDlTask;
        etagDlTask = makeTask("getVersionJsonIndex", EtagDownloadTask.class);
        {
            etagDlTask.setUrl(delayedString(Constants.MC_JSON_INDEX_URL));
            etagDlTask.setFile(delayedFile(Constants.VERSION_JSON_INDEX));
            etagDlTask.setDieWithError(false);
        }

        etagDlTask = makeTask("getVersionJson", EtagDownloadTask.class);
        {
            class GetVersionJsonUrl extends DelayedString {
                public GetVersionJsonUrl() {
                    super(BasePlugin.this.project, "");
                }

                @Override
                public String resolveDelayed() {
                    try {
                        MCVersionManifest manifest = JsonFactory.loadMCVersionManifest(delayedFile(Constants.VERSION_JSON_INDEX).call());
                        MCVersionManifest.Version version = manifest.findVersion(delayedString("{MC_VERSION}").call());
                        return version.url;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            etagDlTask.dependsOn("getVersionJsonIndex");
            etagDlTask.getInputs().file(delayedFile(Constants.VERSION_JSON_INDEX));
            etagDlTask.setUrl(new GetVersionJsonUrl());
            etagDlTask.setFile(delayedFile(Constants.VERSION_JSON));
            etagDlTask.setDieWithError(false);
            //TODO: this is not necessary?
            etagDlTask.doLast(new Closure<Boolean>(project) // normalizes to linux endings
            {
                @Override
                public Boolean call() {
                    try {
                        File json = delayedFile(Constants.VERSION_JSON).call();
                        if (!json.exists())
                            return true;

                        List<String> lines = Files.readAllLines(json.toPath());
                        StringBuilder buf = new StringBuilder();
                        for (String line : lines) {
                            buf = buf.append(line).append('\n');
                        }
                        Files.write(json.toPath(), buf.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                }
            });
        }
        class GetDataFromJson extends DelayedString {
            private final Function<Version, String> function;

            public GetDataFromJson(Function<Version, String> function) {
                super(BasePlugin.this.project, "");
                this.function = function;
            }

            @Override
            public String resolveDelayed() {
                try {
                    Version manifest = JsonFactory.loadVersion(delayedFile(Constants.VERSION_JSON).call(), null);
                    return function.apply(manifest);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        task = makeTask("downloadClient", DownloadTask.class);
        {
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");

            task.setOutput(delayedFile(Constants.JAR_CLIENT_FRESH));
            task.setUrl(new GetDataFromJson(json -> json.downloads.client.url));
        }

        task = makeTask("downloadServer", DownloadTask.class);
        {
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");

            task.setOutput(delayedFile(Constants.JAR_SERVER_FRESH));
            task.setUrl(new GetDataFromJson(json -> json.downloads.server.url));
        }

        ObtainFernFlowerTask mcpTask = makeTask("downloadMcpTools", ObtainFernFlowerTask.class);
        {
            mcpTask.setMcpUrl(delayedString(Constants.MCP_URL));
            mcpTask.setFfJar(delayedFile(Constants.FERNFLOWER));
        }

        etagDlTask = makeTask("getAssetsIndex", EtagDownloadTask.class);
        {
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");

            etagDlTask.setUrl(new GetDataFromJson(json -> json.assetIndex.url));
            etagDlTask.setFile(delayedFile(Constants.ASSETS + "/indexes/{ASSET_INDEX}.json"));
            etagDlTask.setDieWithError(false);

            etagDlTask.doLast(new Action<Task>() {
                @Override
                public void execute(Task task1) {
                    try {
                        parseAssetIndex();
                    } catch (JsonSyntaxException | JsonIOException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        DownloadAssetsTask assets = makeTask("getAssets", DownloadAssetsTask.class);
        {
            assets.setAssetsDir(delayedFile(Constants.ASSETS));
            assets.setIndex(getAssetIndexClosure());
            assets.setIndexName(delayedString("{ASSET_INDEX}"));
            assets.dependsOn("getAssetsIndex");
        }

        // getVersionJson has been moved to top

        Delete clearCache = makeTask("cleanCache", Delete.class);
        {
            clearCache.delete(delayedFile("{CACHE_DIR}/minecraft"));
            clearCache.setGroup("ForgeGradle");
            clearCache.setDescription("Cleares the ForgeGradle cache. DONT RUN THIS unless you want a fresh start, or the dev tells you to.");
        }

        // special userDev stuff
        ExtractConfigTask extractMcpData = makeTask("extractMcpData", ExtractConfigTask.class);
        {
            extractMcpData.setOut(delayedFile(Constants.MCP_DATA_DIR));
            extractMcpData.setConfig(Constants.CONFIG_MCP_DATA);
        }
    }

    public void parseAssetIndex() throws JsonSyntaxException, JsonIOException, IOException {
        assetIndex = JsonFactory.loadAssetsIndex(delayedFile(Constants.ASSETS + "/indexes/{ASSET_INDEX}.json").call());
    }

    @SuppressWarnings("serial")
    public Closure<AssetIndex> getAssetIndexClosure() {
        return new Closure<AssetIndex>(this, null) {
            public AssetIndex call(Object... obj) {
                return getAssetIndex();
            }
        };
    }

    public AssetIndex getAssetIndex() {
        return assetIndex;
    }

    /**
     * This extension object will have the name "minecraft"
     *
     * @return extension object class
     */
    @SuppressWarnings("unchecked")
    protected Class<K> getExtensionClass() {
        return (Class<K>) BaseExtension.class;
    }

    /**
     * @return the extension object with name
     * @see Constants#EXT_NAME_MC
     */
    @SuppressWarnings("unchecked")
    public final K getExtension() {
        if (otherPlugin != null && canOverlayPlugin())
            return getOverlayExtension();
        else
            return (K) project.getExtensions().getByName(Constants.EXT_NAME_MC);
    }

    /**
     * @return the extension object with name EXT_NAME_MC
     * @see Constants#EXT_NAME_MC
     */
    protected abstract K getOverlayExtension();

    public DefaultTask makeTask(String name) {
        return makeTask(name, DefaultTask.class);
    }

    public <T extends Task> T makeTask(String name, Class<T> type) {
        return makeTask(project, name, type);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Task> T makeTask(Project proj, String name, Class<T> type) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("type", type);
        return (T) proj.task(map, name);
    }

    public static Project getProject(File buildFile, Project parent) {
        ProjectBuilder builder = ProjectBuilder.builder();
        if (buildFile != null) {
            builder = builder.withProjectDir(buildFile.getParentFile())
                    .withName(buildFile.getParentFile().getName());
        } else {
            builder = builder.withProjectDir(new File("."));
        }

        if (parent != null) {
            builder = builder.withParent(parent);
        }

        Project project = builder.build();

        if (buildFile != null) {
            HashMap<String, String> map = new HashMap<>();
            map.put("from", buildFile.getAbsolutePath());

            project.apply(map);
        }

        return project;
    }

    public void applyExternalPlugin(String plugin) {
        Map<String, Object> map = new HashMap<>();
        map.put("plugin", plugin);
        project.apply(map);
    }

    public MavenArtifactRepository addMavenRepo(Project proj, final String name, final String url) {
        return addMavenRepo(proj, name, url, true);
    }

    public MavenArtifactRepository addMavenRepo(final Project proj, final String name, final String url, final boolean usePom) {
        return proj.getRepositories().maven(repo -> {
            repo.setName(name);
            repo.setUrl(url);
            if (!usePom) {
                GradleVersionUtils.ifAfter("4.5", () -> repo.metadataSources(MavenArtifactRepository.MetadataSources::artifact));
            }
        });
    }

    public FlatDirectoryArtifactRepository addFlatRepo(Project proj, final String name, final Object... dirs) {
        return proj.getRepositories().flatDir(repo -> {
            repo.setName(name);
            repo.dirs(dirs);
        });
    }

    protected String getWithEtag(String strUrl, File cache, File etagFile) {
        try {
            if (project.getGradle().getStartParameter().isOffline()) // dont even try the internet
                return new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);

            // dude, its been less than 5 minutes since the last time..
            if (cache.exists() && cache.lastModified() + 300000 >= System.currentTimeMillis())
                return new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);

            String etag;
            if (etagFile.exists()) {
                etag = new String(Files.readAllBytes(etagFile.toPath()), StandardCharsets.UTF_8);
            } else {
                etagFile.getParentFile().mkdirs();
                etag = "";
            }

            URL url = new URL(strUrl);

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", Constants.USER_AGENT);

            if (!Strings.isNullOrEmpty(etag)) {
                con.setRequestProperty("If-None-Match", etag);
            }

            con.connect();

            String out = null;
            if (con.getResponseCode() == 304) {
                // the existing file is good
                FileUtils.updateDate(cache); // touch it to update last-modified time
                out = new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);
            } else if (con.getResponseCode() == 200) {
                InputStream stream = con.getInputStream();
                byte[] data = ByteStreams.toByteArray(stream);
                Files.write(cache.toPath(), data);
                stream.close();

                // write etag
                etag = con.getHeaderField("ETag");
                if (Strings.isNullOrEmpty(etag)) {
                    FileUtils.updateDate(etagFile);
                } else {
                    Files.write(etagFile.toPath(), etag.getBytes(StandardCharsets.UTF_8));
                }

                out = new String(data);
            } else {
                project.getLogger().error("Etag download for " + strUrl + " failed with code " + con.getResponseCode());
            }

            con.disconnect();

            return out;
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (cache.exists()) {
            try {
                return new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("Unable to obtain url (" + strUrl + ") with etag!");
    }

    @Override
    public String resolve(String pattern, Project project, K exten) {
        if (version != null)
            pattern = pattern.replace("{ASSET_INDEX}", version.getAssets());

        if (exten.mappingsSet())
            pattern = pattern.replace("{MCP_DATA_DIR}", Constants.MCP_DATA_DIR);

        return pattern;
    }

    protected DelayedString delayedString(String path) {
        return new DelayedString(project, path, this);
    }

    protected DelayedFile delayedFile(String path) {
        return new DelayedFile(project, path, this);
    }

    protected DelayedFileTree delayedFileTree(String path) {
        return new DelayedFileTree(project, path, this);
    }

    protected DelayedFileTree delayedZipTree(String path) {
        return new DelayedFileTree(project, path, true, this);
    }

}
