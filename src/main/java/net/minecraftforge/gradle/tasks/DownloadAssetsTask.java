package net.minecraftforge.gradle.tasks;

import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.AssetIndex.AssetEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class DownloadAssetsTask extends DefaultTask {
    DelayedFile assetsDir;

    Supplier<AssetIndex> index;

    @Input
    DelayedString indexName;

    private boolean errored = false;
    private File virtualRoot = null;
    private final ConcurrentLinkedQueue<Asset> filesLeft = new ConcurrentLinkedQueue<>();
    private final ArrayList<AssetsThread> threads = new ArrayList<>();
    private final File minecraftDir = new File(Constants.getMinecraftDirectory(), "assets/objects");

    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int MAX_TRIES = 5;

    @TaskAction
    public void doTask() throws ParserConfigurationException, SAXException, IOException, InterruptedException {
        File out = new File(getAssetsDir(), "objects");
        out.mkdirs();

        AssetIndex index = getIndex();

        // check virtual
        if (index.virtual) {
            virtualRoot = new File(getAssetsDir(), "virtual/" + getIndexName());
            virtualRoot.mkdirs();
        }

        for (Entry<String, AssetEntry> e : index.objects.entrySet()) {
            Asset asset = new Asset(e.getKey(), e.getValue().hash, e.getValue().size);
            File file_hashed = new File(out, asset.path);
            File file_virtual = new File(virtualRoot, asset.name);

            // exists but not the right size?? delete
            if (file_hashed.exists() && file_hashed.length() != asset.size)
                file_hashed.delete();

            // File or virtual doesnt exist? add to the list.
            if (!file_hashed.exists()) {
                filesLeft.offer(asset);
                continue;
            }

            if (index.virtual) {
                if (file_virtual.exists() && (file_virtual.length() != asset.size || !asset.hash.equalsIgnoreCase(Constants.hash(file_virtual, "SHA")))) {
                    file_virtual.delete();
                }

                if (!file_virtual.exists()) {
                    filesLeft.offer(asset);
                }
            }
        }

        getLogger().debug("Finished parsing JSON");
        int max = filesLeft.size();
        getLogger().debug("Files Missing: " + max + "/" + index.objects.size());

        // get number of threads
        int threadNum = max / 100;
        if (threadNum == 0 && max > 0)
            threadNum++; // atleats 1 thread

        // spawn threads
        for (int i = 0; i < threadNum; i++)
            spawnThread();

        getLogger().debug("Threads initially spawned: " + threadNum);

        while (stillRunning()) {
            int done = max - filesLeft.size();
            getLogger().lifecycle("Current status: " + done + "/" + max + "   " + (int) ((double) done / max * 100) + "%");
            spawnThread();
            Thread.sleep(1000);
        }

        if (errored) {
            // CRASH!
            getLogger().error("Something went wrong with the assets downloading!");
            this.setDidWork(false);
        }
    }

    private void spawnThread() {
        if (threads.size() < MAX_THREADS) {
            getLogger().debug("Spawning thread #" + (threads.size() + 1));
            AssetsThread thread = new AssetsThread();
            thread.start();
            threads.add(thread);
        }
    }

    private boolean stillRunning() {
        for (Thread t : threads) {
            if (t.isAlive()) {
                return true;
            }
        }
        getLogger().debug("All " + threads.size() + " threads Complete");
        return false;
    }

    @OutputDirectory
    public File getAssetsDir() {
        return assetsDir.call();
    }

    public void setAssetsDir(DelayedFile assetsDir) {
        this.assetsDir = assetsDir;
    }

    @Input
    public AssetIndex getIndex() {
        return index.get();
    }

    /**
     * @deprecated use {@link #setIndex(Supplier)} variant
     */
    @Deprecated
    public void setIndex(@Deprecated Closure<AssetIndex> index) {
        this.index = index::call;
    }

    public void setIndex(Supplier<AssetIndex> index) {
        this.index = index;
    }

    public String getIndexName() {
        return indexName.call();
    }

    public void setIndexName(DelayedString indexName) {
        this.indexName = indexName;
    }

    private static class Asset {
        public final String name;
        public final String path;
        public final String hash;
        public final long size;

        Asset(String name, String hash, long size) {
            this.name = name;
            this.path = hash.substring(0, 2) + "/" + hash;
            this.hash = hash.toLowerCase();
            this.size = size;
        }
    }

    private class AssetsThread extends Thread {
        public AssetsThread() {
            this.setDaemon(true);
        }

        @Override
        public void run() {
            Asset asset;
            while ((asset = filesLeft.poll()) != null) {
                for (int i = 1; i < MAX_TRIES + 1; i++) {
                    try {
                        File file = new File(getAssetsDir(), "objects/" + asset.path);

                        if (file.exists() && file.length() != asset.size) {
                            file.delete();
                        }

                        if (!file.exists()) {
                            file.getParentFile().mkdirs();

                            File localMc = new File(minecraftDir, asset.path);
                            BufferedInputStream stream;

                            // check for local copy
                            if (localMc.exists() && Constants.hash(localMc, "SHA").equals(asset.hash))
                                stream = new BufferedInputStream(Files.newInputStream(localMc.toPath())); // if so, copy
                            else
                                stream = new BufferedInputStream(new URL(Constants.ASSETS_URL + "/" + asset.path).openStream()); // otherwise download

                            Files.write(file.toPath(), ByteStreams.toByteArray(stream));
                            stream.close();
                        }

                        String hash = Constants.hash(file, "SHA");
                        if (asset.hash.equals(hash)) {
                            break;
                        } else {
                            file.delete();
                            getLogger().error("download attempt " + i + " failed! : " + asset.hash + " != " + hash);
                        }

                        // copy to virtual
                        if (virtualRoot != null) {
                            File virtual = new File(virtualRoot, asset.name);
                            virtual.getParentFile().mkdirs();
                            if (virtual.exists() && !Constants.hash(virtual, "SHA").equalsIgnoreCase(asset.hash)) {
                                virtual.delete();
                            }

                            if (!virtual.exists()) {
                                Files.copy(file.toPath(), virtual.toPath());
                            }
                        }
                    } catch (Exception e) {
                        getLogger().error("Error downloading asset: " + asset.path);
                        e.printStackTrace();
                        if (!errored) {
                            errored = true;
                        }
                    }
                }
            }
        }
    }
}
