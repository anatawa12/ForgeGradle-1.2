package net.minecraftforge.gradle;

import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.logging.StandardOutputListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileLogListenner extends BuildAdapter implements StandardOutputListener, BuildListener {
    private final Path out;
    private BufferedWriter writer;

    /**
     * @deprecated use {@link #FileLogListenner(Path)}
     */
    @Deprecated
    public FileLogListenner(File file) {
        this(file.toPath());
    }

    public FileLogListenner(Path path) {
        out = path;

        try {
            if (Files.exists(out))
                Files.delete(out);
            else
                Files.createDirectories(out.getParent());

            Files.createFile(out);

            writer = Files.newBufferedWriter(out, Charset.defaultCharset());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOutput(CharSequence arg0) {
        try {
            writer.write(arg0.toString());
        } catch (IOException e) {
            // to stop recursion....
        }
    }

    @Override
    public void buildFinished(BuildResult arg0) {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
