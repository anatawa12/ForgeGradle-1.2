package net.minecraftforge.gradle.tasks;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import net.minecraftforge.gradle.FileUtils;
import net.minecraftforge.gradle.common.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CrowdinDownloadTask extends DefaultTask {
    @Input
    private Object projectId;
    @Input
    private Object apiKey;
    @Input
    private boolean extract = true;
    private Object output;

    // format these with the projectId and apiKey
    private static final String EXPORT_URL = "https://api.crowdin.net/api/project/%s/export?key=%s";
    private static final String DOWNLOAD_URL = "https://api.crowdin.net/api/project/%s/download/all.zip?key=%s";

    public CrowdinDownloadTask() {
        super();

        this.onlyIf(arg0 -> {
            CrowdinDownloadTask task = (CrowdinDownloadTask) arg0;

            // no API key? skip
            if (Strings.isNullOrEmpty(task.getApiKey())) {
                getLogger().lifecycle("Crowdin api key is null, skipping task.");
                return false;
            }

            // offline? skip.
            if (getProject().getGradle().getStartParameter().isOffline()) {
                getLogger().lifecycle("Gradle is in offline mode, skipping task.");
                return false;
            }

            return true;
        });
    }


    @TaskAction
    public void doTask() throws IOException {
        String project = getProjectId();
        String key = getApiKey();

        exportLocalizations(project, key);
        getLocalizations(project, key, getOutput());
    }

    private void exportLocalizations(String projectId, String key) throws IOException {
        getLogger().debug("Exporting crowdin localizations.");
        URL url = new URL(String.format(EXPORT_URL, projectId, key));

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("User-Agent", Constants.USER_AGENT);
        con.setInstanceFollowRedirects(true);

        try {
            con.connect();
        } catch (IOException e) {
            // just in case people dont have internet at the moment.
            throw new RuntimeException(e);
        }

        int reponse = con.getResponseCode();
        con.disconnect();

        if (reponse == 401)
            throw new RuntimeException("Invalid Crowdin API-Key");
    }

    private void getLocalizations(String projectId, String key, File output) throws IOException {
        getLogger().info("Downlaoding crowdin localizations.");
        URL url = new URL(String.format(DOWNLOAD_URL, projectId, key));

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("User-Agent", Constants.USER_AGENT);
        con.setInstanceFollowRedirects(true);

        InputStream stream = con.getInputStream();

        if (extract) {
            ZipInputStream zStream = new ZipInputStream(con.getInputStream());

            ZipEntry entry;
            while ((entry = zStream.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getSize() == 0) {
                    continue;
                }

                getLogger().debug("Extracting file: " + entry.getName());
                File out = new File(output, entry.getName());
                output.mkdirs();
                FileUtils.updateDate(out);
                Files.write(out.toPath(), ByteStreams.toByteArray(zStream));
                zStream.closeEntry();
            }

            zStream.close();
        } else {
            output.mkdirs();
            FileUtils.updateDate(output);
            Files.write(output.toPath(), ByteStreams.toByteArray(stream));
            stream.close();
        }

        con.disconnect();
    }


    @SuppressWarnings("rawtypes")
    public String getProjectId() {
        Objects.requireNonNull(projectId, "ProjectID must be set for crowdin!");

        while (projectId instanceof Closure)
            projectId = ((Closure) projectId).call();

        return projectId.toString();
    }

    public void setProjectId(Object projectId) {
        this.projectId = projectId;
    }

    @SuppressWarnings("rawtypes")
    public String getApiKey() {
        while (apiKey instanceof Closure)
            apiKey = ((Closure) apiKey).call();

        if (apiKey == null)
            return null;

        return apiKey.toString();
    }

    public void setApiKey(Object apiKey) {
        this.apiKey = apiKey;
    }

    @OutputFiles
    public FileCollection getOutputFiles() {
        if (isExtract())
            return getProject().fileTree(getOutput());
        else
            return getProject().files(getOutput());
    }

    @Internal
    public File getOutput() {
        return getProject().file(output);
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public boolean isExtract() {
        return extract;
    }

    public void setExtract(boolean extract) {
        this.extract = extract;
    }
}
