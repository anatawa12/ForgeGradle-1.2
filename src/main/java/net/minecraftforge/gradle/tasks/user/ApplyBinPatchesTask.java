package net.minecraftforge.gradle.tasks.user;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.nothome.delta.GDiffPatcher;
import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.*;

import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.*;

@CacheableTask
public class ApplyBinPatchesTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    DelayedFile inJar;

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    DelayedFile classesJar;

    @OutputFile
    DelayedFile outJar;

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    DelayedFile patches;  // this will be a patches.lzma

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    DelayedFileTree resources;

    private HashMap<String, ClassPatch> patchlist = new HashMap<>();
    private GDiffPatcher patcher = new GDiffPatcher();

    @TaskAction
    public void doTask() throws IOException {
        setup();

        if (getOutJar().exists()) {
            getOutJar().delete();
        }

        final HashSet<String> entries = new HashSet<>();

        try (ZipFile in = new ZipFile(getInJar()); ZipInputStream classesIn = new ZipInputStream(Files.newInputStream(getClassesJar().toPath())); ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(getOutJar().toPath())))) {
            // DO PATCHES
            log("Patching Class:");
            for (ZipEntry e : Collections.list(in.entries())) {
                if (e.getName().contains("META-INF"))
                    continue;

                if (e.isDirectory()) {
                    out.putNextEntry(e);
                } else {
                    ZipEntry n = new ZipEntry(e.getName());
                    n.setTime(e.getTime());
                    out.putNextEntry(n);

                    byte[] data = ByteStreams.toByteArray(in.getInputStream(e));
                    ClassPatch patch = patchlist.get(e.getName().replace('\\', '/'));

                    if (patch != null) {
                        log("\t%s (%s) (input size %d)", patch.targetClassName, patch.sourceClassName, data.length);
                        int inputChecksum = adlerHash(data);
                        if (patch.inputChecksum != inputChecksum) {
                            throw new RuntimeException(String.format("There is a binary discrepency between the expected input class %s (%s) and the actual class. Checksum on disk is %x, in patch %x. Things are probably about to go very wrong. Did you put something into the jar file?", patch.targetClassName, patch.sourceClassName, inputChecksum, patch.inputChecksum));
                        }
                        synchronized (patcher) {
                            data = patcher.patch(data, patch.patch);
                        }
                    }

                    out.write(data);
                }

                // add the names to the hashset
                entries.add(e.getName());
            }

            // COPY DATA
            ZipEntry entry;
            while ((entry = classesIn.getNextEntry()) != null) {
                if (entries.contains(entry.getName()))
                    continue;

                out.putNextEntry(entry);
                out.write(ByteStreams.toByteArray(classesIn));
                entries.add(entry.getName());
            }

            getResources().visit(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails dirDetails) {}

                @Override
                public void visitFile(FileVisitDetails file) {
                    try {
                        String name = file.getRelativePath().toString().replace('\\', '/');
                        if (!entries.contains(name)) {
                            ZipEntry n = new ZipEntry(name);
                            n.setTime(file.getLastModified());
                            out.putNextEntry(n);
                            ByteStreams.copy(file.open(), out);
                            entries.add(name);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    private int adlerHash(byte[] input) {
        Adler32 hasher = new Adler32();
        hasher.update(input);
        return (int) hasher.getValue();
    }

    public void setup() {
        Pattern matcher = Pattern.compile("binpatch/merged/.*.binpatch");

        JarInputStream jis;
        try {
            LzmaInputStream binpatchesDecompressed = new LzmaInputStream(Files.newInputStream(getPatches().toPath()), new Decoder());
            ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
            JarOutputStream jos = new JarOutputStream(jarBytes);
            Pack200.newUnpacker().unpack(binpatchesDecompressed, jos);
            jis = new JarInputStream(new ByteArrayInputStream(jarBytes.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log("Reading Patches:");
        do {
            try {
                JarEntry entry = jis.getNextJarEntry();
                if (entry == null) {
                    break;
                }

                if (matcher.matcher(entry.getName()).matches()) {
                    ClassPatch cp = readPatch(entry, jis);
                    patchlist.put(cp.sourceClassName.replace('.', '/') + ".class", cp);
                } else {
                    jis.closeEntry();
                }
            } catch (IOException e) {}
        } while (true);
        log("Read %d binary patches", patchlist.size());
        List<String> parts = patchlist.entrySet().stream().map(Object::toString).collect(Collectors.toList());
        log("Patch list :\n\t%s", String.join("\n\t", parts));
    }

    private ClassPatch readPatch(JarEntry patchEntry, JarInputStream jis) throws IOException {
        log("\t%s", patchEntry.getName());
        ByteArrayDataInput input = ByteStreams.newDataInput(ByteStreams.toByteArray(jis));

        String name = input.readUTF();
        String sourceClassName = input.readUTF();
        String targetClassName = input.readUTF();
        boolean exists = input.readBoolean();
        int inputChecksum = 0;
        if (exists) {
            inputChecksum = input.readInt();
        }
        int patchLength = input.readInt();
        byte[] patchBytes = new byte[patchLength];
        input.readFully(patchBytes);

        return new ClassPatch(name, sourceClassName, targetClassName, exists, inputChecksum, patchBytes);
    }

    private void log(String format, Object... args) {
        getLogger().debug(String.format(format, args));
    }


    public File getInJar() {
        return inJar.call();
    }

    public void setInJar(DelayedFile inJar) {
        this.inJar = inJar;
    }

    public File getOutJar() {
        return outJar.call();
    }

    public void setOutJar(DelayedFile outJar) {
        this.outJar = outJar;
    }

    public File getPatches() {
        return patches.call();
    }

    public void setPatches(DelayedFile patchesJar) {
        this.patches = patchesJar;
    }

    public File getClassesJar() {
        return classesJar.call();
    }

    public void setClassesJar(DelayedFile extraJar) {
        this.classesJar = extraJar;
    }

    public static class ClassPatch {
        public final String name;
        public final String sourceClassName;
        public final String targetClassName;
        public final boolean existsAtTarget;
        public final byte[] patch;
        public final int inputChecksum;

        public ClassPatch(String name, String sourceClassName, String targetClassName, boolean existsAtTarget, int inputChecksum, byte[] patch) {
            this.name = name;
            this.sourceClassName = sourceClassName;
            this.targetClassName = targetClassName;
            this.existsAtTarget = existsAtTarget;
            this.inputChecksum = inputChecksum;
            this.patch = patch;
        }

        @Override
        public String toString() {
            return String.format("%s : %s => %s (%b) size %d", name, sourceClassName, targetClassName, existsAtTarget, patch.length);
        }
    }

    public FileTree getResources() {
        return resources.call();
    }

    public void setResources(DelayedFileTree resources) {
        this.resources = resources;
    }
}
