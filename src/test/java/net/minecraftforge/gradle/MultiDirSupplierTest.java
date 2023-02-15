package net.minecraftforge.gradle;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;

public class MultiDirSupplierTest {
    private final List<File> dirs = new LinkedList<>();
    private final Multimap<File, String> expectedFiles = HashMultimap.create();
    private final Random rand = new Random();
    private static final String END = ".tmp";

    @BeforeEach // before each test
    public void setup() throws IOException {
        int dirNum = rand.nextInt(4) + 1; // 0-5

        for (int i = 0; i < dirNum; i++) {
            // create and add dir
            File dir = Files.createTempDirectory(System.currentTimeMillis() + "-").toFile();
            dirs.add(dir);

            int fileNum = rand.nextInt(9) + 1; // 0-10
            for (int j = 0; j < fileNum; j++) {
                File f = Files.createTempFile(dir.toPath(), j + "tmp-", END).toFile();
                expectedFiles.put(dir, getRelative(dir, f));
            }
        }
    }

    @AfterEach // after each test
    public void cleanup() {
        // delete the files.
        for (File f : dirs)
            delete(f);

        // empty the variables.
        dirs.clear();
        expectedFiles.clear();
    }

    /**
     * Deletes the specified file or directory.
     */
    private void delete(File f) {
        if (f.isFile())
            f.delete();
        else // mst be a dir.
        {
            for (File file : f.listFiles())
                delete(file);
        }
    }

    /**
     * Returns the path of the child relative to the provided root. Assumes that the child is actually a child of the provided root.
     */
    private String getRelative(File root, File child) throws IOException {
        return child.getCanonicalPath().substring(root.getCanonicalPath().length() + 1); // + 1 for the slash
    }

    @Test
    public void testGatherAll() throws IOException {
        InputSupplier supp = new MultiDirSupplier(dirs);

        // gather all the relative paths
        for (String rel : supp.gatherAll(END)) {
            Assertions.assertTrue(expectedFiles.containsValue(rel));
        }

        supp.close(); // to please the compiler..
    }

    @Test
    public void testGetRoot() throws IOException {
        InputSupplier supp = new MultiDirSupplier(dirs);

        for (File dir : expectedFiles.keySet()) {
            for (String rel : expectedFiles.get(dir)) {
                Assertions.assertEquals(dir.getCanonicalFile(), new File(supp.getRoot(rel)).getCanonicalFile());
            }
        }

        supp.close(); // to please the compiler..
    }

    @Test
    public void testIOStreams() throws IOException {
        // to keep track of changes to check later.
        Map<String, byte[]> dataMap = new HashMap<>(expectedFiles.size());

        // its both an input and output supplier.
        MultiDirSupplier supp = new MultiDirSupplier(dirs);

        // write a bunch of random bytes to each file.
        for (String resource : supp.gatherAll(END)) {
            // generate bytes.
            byte[] bytes = new byte[rand.nextInt(90) + 10]; // 10-100 bytes
            rand.nextBytes(bytes); // fill with random stuff
            dataMap.put(resource, bytes); // put into the map.


            OutputStream stream = supp.getOutput(resource);
            stream.write(bytes);
            stream.close();
        }

        // this IO supplier shouldnt need closing.. so we dont care here...
        // otherwise we would close the one supplier, and open another.

        // read the files, and ensure they are correct.
        for (String resource : supp.gatherAll(END)) {
            byte[] expected = dataMap.get(resource);
            byte[] actual = new byte[expected.length];

            InputStream stream = supp.getInput(resource);
            stream.read(actual);
            stream.close();

            Assertions.assertArrayEquals(expected, actual);
        }

        supp.close(); // to please the compiler..
    }
}
