package net.minecraftforge.gradle.tasks.abstractutil;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

@CacheableTask
public class DownloadTask extends DefaultTask {
    @Input
    private String url;
    @OutputFile
    private File output;

    @TaskAction
    public void doTask() throws IOException {
        output.getParentFile().mkdirs();
        output.createNewFile();

        getLogger().debug("Downloading " + getUrl() + " to " + output);

        // TODO: check etags... maybe?

        HttpURLConnection connect = (HttpURLConnection) new URL(getUrl()).openConnection();
        connect.setRequestProperty("User-Agent", Constants.USER_AGENT);
        connect.setInstanceFollowRedirects(true);

        InputStream inStream = connect.getInputStream();
        OutputStream outStream = Files.newOutputStream(output.toPath());

        int data = inStream.read();
        while (data != -1) {
            outStream.write(data);

            // read next
            data = inStream.read();
        }

        inStream.close();
        outStream.flush();
        outStream.close();

        getLogger().info("Download complete");
    }

    public File getOutput() {
        return output;
    }

    @Deprecated
    public void setOutput(DelayedFile output) {
        this.output = output.call();
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public String getUrl() {
        return url;
    }

    @Deprecated
    public void setUrl(DelayedString url) {
        this.url = url.call();
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
