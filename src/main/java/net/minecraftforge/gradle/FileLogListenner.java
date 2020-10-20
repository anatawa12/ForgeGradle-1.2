package net.minecraftforge.gradle;

import com.google.common.io.Files;
import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.StandardOutputListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

public class FileLogListenner extends BuildAdapter implements StandardOutputListener, BuildListener {
    private final File out;
    private BufferedWriter writer;

    public FileLogListenner(File file) {
        out = file;

        try {
            if (out.exists())
                out.delete();
            else
                out.getParentFile().mkdirs();

            out.createNewFile();

            writer = Files.newWriter(out, Charset.defaultCharset());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
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
