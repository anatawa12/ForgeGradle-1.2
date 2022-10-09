package com.anatawa12.forge.gradle.separated.mergeJars;

import com.anatawa12.forge.gradle.separated.ArgsParser;
import com.anatawa12.forge.gradle.separated.ListUtils;
import com.google.common.base.MoreObjects;
import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Main {
    public static void main(String[] args) throws IOException {
        Main main = new Main();
        ArgsParser parser = new ArgsParser(args);
        main.mergeCfg = parser.nextFile();
        main.client = parser.nextFile();
        main.server = parser.nextFile();
        main.mcVersion = parser.nextString();
        main.outJar = parser.nextFile();
        main.doTask();
    }

    private File mergeCfg;
    private File client;
    private File server;
    private String mcVersion;
    private File outJar;

    @SuppressWarnings("rawtypes")
    private Class sideClass, sideOnlyClass;

    private static final HashSet<String> copyToServer = new HashSet<>();
    private static final HashSet<String> copyToClient = new HashSet<>();
    private static final HashSet<String> dontAnnotate = new HashSet<>();
    private static final HashSet<String> dontProcess = new HashSet<>();
    private static final boolean DEBUG = false;

    public void doTask() throws IOException {
        // set classes.
        if (getMcVersion().startsWith("1.8")) {
            sideClass = net.minecraftforge.fml.relauncher.Side.class;
            sideOnlyClass = net.minecraftforge.fml.relauncher.SideOnly.class;
        } else {
            sideClass = cpw.mods.fml.relauncher.Side.class;
            sideOnlyClass = cpw.mods.fml.relauncher.SideOnly.class;
        }

        readConfig(getMergeCfg());
        processJar(getClient(), getServer(), getOutJar());
    }

    private boolean readConfig(File mapFile) {
        try {
            FileInputStream fstream = new FileInputStream(mapFile);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String line;
            while ((line = br.readLine()) != null) {
                line = line.split("#")[0];
                char cmd = line.charAt(0);
                line = line.substring(1).trim();

                switch (cmd) {
                    case '!':
                        dontAnnotate.add(line);
                        break;
                    case '<':
                        copyToClient.add(line);
                        break;
                    case '>':
                        copyToServer.add(line);
                        break;
                    case '^':
                        dontProcess.add(line);
                        break;
                }
            }

            in.close();
            return true;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    public void processJar(File clientInFile, File serverInFile, File outFile) throws IOException {
        ZipFile cInJar = null;
        ZipFile sInJar = null;
        ZipOutputStream outJar = null;

        try {
            try {
                cInJar = new ZipFile(clientInFile);
                sInJar = new ZipFile(serverInFile);
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }

            // different messages.

            try {
                outJar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }

            // read in the jars, and initalize some variables
            Set<String> resources = new HashSet<>();
            Map<String, ZipEntry> cClasses = getClassEntries(cInJar, outJar, resources);
            Map<String, ZipEntry> sClasses = getClassEntries(sInJar, outJar, resources);
            Set<String> cAdded = new HashSet<>();

            // start processing
            for (Map.Entry<String, ZipEntry> entry : cClasses.entrySet()) {
                String name = entry.getKey();
                ZipEntry cEntry = entry.getValue();
                ZipEntry sEntry = sClasses.get(name);

                if (sEntry == null) {
                    if (copyToServer.contains(name)) {
                        if (DEBUG) {
                            System.out.println("Copy class c->s : " + name);
                        }
                    }
                    copyClass(cInJar, cEntry, outJar, true);
                    cAdded.add(name);
                    continue;
                }

                sClasses.remove(name);
                byte[] cData = readEntry(cInJar, entry.getValue());
                byte[] sData = readEntry(sInJar, sEntry);
                byte[] data = processClass(cData, sData);

                ZipEntry newEntry = new ZipEntry(cEntry.getName());
                outJar.putNextEntry(newEntry);
                outJar.write(data);
                cAdded.add(name);
            }

            for (Map.Entry<String, ZipEntry> entry : sClasses.entrySet()) {
                if (DEBUG) {
                    System.out.println("Copy class s->c : " + entry.getKey());
                }
                copyClass(sInJar, entry.getValue(), outJar, false);
            }

            for (String name : new String[]{sideOnlyClass.getName(), sideClass.getName()}) {
                String eName = name.replace(".", "/");
                String classPath = eName + ".class";
                ZipEntry newEntry = new ZipEntry(classPath);
                if (!cAdded.contains(eName)) {
                    outJar.putNextEntry(newEntry);
                    outJar.write(getClassBytes(name));
                }
            }

        } finally {
            if (cInJar != null) {
                try {
                    cInJar.close();
                } catch (IOException e) {
                }
            }

            if (sInJar != null) {
                try {
                    sInJar.close();
                } catch (IOException e) {
                }
            }
            if (outJar != null) {
                try {
                    outJar.close();
                } catch (IOException e) {
                }
            }

        }
    }

    private void copyClass(ZipFile inJar, ZipEntry entry, ZipOutputStream outJar, boolean isClientOnly) throws IOException {
        ClassReader reader = new ClassReader(readEntry(inJar, entry));
        ClassNode classNode = new ClassNode();

        reader.accept(classNode, 0);

        if (!dontAnnotate.contains(classNode.name)) {
            if (classNode.visibleAnnotations == null) {
                classNode.visibleAnnotations = new ArrayList<>();
            }
            classNode.visibleAnnotations.add(getSideAnn(isClientOnly));
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        byte[] data = writer.toByteArray();

        ZipEntry newEntry = new ZipEntry(entry.getName());
        if (outJar != null) {
            outJar.putNextEntry(newEntry);
            outJar.write(data);
        }
    }

    private byte[] readEntry(ZipFile inFile, ZipEntry entry) throws IOException {
        return ByteStreams.toByteArray(inFile.getInputStream(entry));
    }

    private AnnotationNode getSideAnn(boolean isClientOnly) {
        AnnotationNode ann = new AnnotationNode(Type.getDescriptor(sideOnlyClass));
        ann.values = new ArrayList<>();
        ann.values.add("value");
        ann.values.add(new String[]{Type.getDescriptor(sideClass), isClientOnly ? "CLIENT" : "SERVER"});
        return ann;
    }

    /**
     * @param inFile    From which to read classes and resources
     * @param outFile   The place to write resources and ignored classes
     * @param resources The registry to add resources to, and to check against.
     * @return HashMap of all the desired Classes and their ZipEntrys
     */
    private Map<String, ZipEntry> getClassEntries(ZipFile inFile, ZipOutputStream outFile, Set<String> resources) throws IOException {
        Map<String, ZipEntry> ret = new HashMap<>();
        master:
        for (ZipEntry entry : Collections.list(inFile.entries())) {
            String entryName = entry.getName();
            // Always skip the manifest
            if ("META-INF/MANIFEST.MF".equals(entryName)) {
                continue;
            }
            if (entry.isDirectory()) {
                /*
                if (!resources.contains(entryName))
                {
                    outFile.putNextEntry(entry);
                }
                */
                continue;
            }

            for (String filter : dontProcess) {
                if (entryName.startsWith(filter)) {
                    continue master;
                }
            }

            if (!entryName.endsWith(".class") || entryName.startsWith(".")) {
                if (!resources.contains(entryName)) {
                    ZipEntry newEntry = new ZipEntry(entryName);
                    outFile.putNextEntry(newEntry);
                    outFile.write(readEntry(inFile, entry));
                    resources.add(entryName);
                }
            } else {
                ret.put(entryName.replace(".class", ""), entry);
            }
        }
        return ret;
    }

    // TODO: rewrite.
    public static byte[] getClassBytes(String name) throws IOException {
        try (InputStream classStream = Main.class.getResourceAsStream("/" + name.replace('.', '/').concat(".class"))) {
            return ByteStreams.toByteArray(classStream);
        }
    }

    public byte[] processClass(byte[] cIn, byte[] sIn) {
        ClassNode cClassNode = getClassNode(cIn);
        ClassNode sClassNode = getClassNode(sIn);

        processFields(cClassNode, sClassNode);
        processMethods(cClassNode, sClassNode);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cClassNode.accept(writer);
        return writer.toByteArray();
    }

    private ClassNode getClassNode(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    private void processFields(ClassNode cClass, ClassNode sClass) {
        List<FieldNode> cFields = cClass.fields;
        List<FieldNode> sFields = sClass.fields;

        int serverFieldIdx = 0;
        if (DEBUG)
            System.out.printf("B: Server List: %s\nB: Client List: %s\n", ListUtils.transform(sFields, FieldName.instance), ListUtils.transform(cFields, FieldName.instance));
        for (int clientFieldIdx = 0; clientFieldIdx < cFields.size(); clientFieldIdx++) {
            FieldNode clientField = cFields.get(clientFieldIdx);
            if (serverFieldIdx < sFields.size()) {
                FieldNode serverField = sFields.get(serverFieldIdx);
                if (!clientField.name.equals(serverField.name)) {
                    boolean foundServerField = false;
                    for (int serverFieldSearchIdx = serverFieldIdx + 1; serverFieldSearchIdx < sFields.size(); serverFieldSearchIdx++) {
                        if (clientField.name.equals(sFields.get(serverFieldSearchIdx).name)) {
                            foundServerField = true;
                            break;
                        }
                    }
                    // Found a server field match ahead in the list - walk to it and add the missing server fields to the client
                    if (foundServerField) {
                        boolean foundClientField = false;
                        for (int clientFieldSearchIdx = clientFieldIdx + 1; clientFieldSearchIdx < cFields.size(); clientFieldSearchIdx++) {
                            if (serverField.name.equals(cFields.get(clientFieldSearchIdx).name)) {
                                foundClientField = true;
                                break;
                            }
                        }
                        if (!foundClientField) {
                            if (serverField.visibleAnnotations == null) {
                                serverField.visibleAnnotations = new ArrayList<>();
                            }
                            serverField.visibleAnnotations.add(getSideAnn(false));
                            cFields.add(clientFieldIdx, serverField);
                            if (DEBUG)
                                System.out.printf("1. Server List: %s\n1. Client List: %s\nIdx: %d %d\n", ListUtils.transform(sFields, FieldName.instance), ListUtils.transform(cFields, FieldName.instance), serverFieldIdx, clientFieldIdx);
                        }
                    } else {
                        if (clientField.visibleAnnotations == null) {
                            clientField.visibleAnnotations = new ArrayList<>();
                        }
                        clientField.visibleAnnotations.add(getSideAnn(true));
                        sFields.add(serverFieldIdx, clientField);
                        if (DEBUG)
                            System.out.printf("2. Server List: %s\n2. Client List: %s\nIdx: %d %d\n", ListUtils.transform(sFields, FieldName.instance), ListUtils.transform(cFields, FieldName.instance), serverFieldIdx, clientFieldIdx);
                    }
                }
            } else {
                if (clientField.visibleAnnotations == null) {
                    clientField.visibleAnnotations = new ArrayList<>();
                }
                clientField.visibleAnnotations.add(getSideAnn(true));
                sFields.add(serverFieldIdx, clientField);
                if (DEBUG)
                    System.out.printf("3. Server List: %s\n3. Client List: %s\nIdx: %d %d\n", ListUtils.transform(sFields, FieldName.instance), ListUtils.transform(cFields, FieldName.instance), serverFieldIdx, clientFieldIdx);
            }
            serverFieldIdx++;
        }
        if (DEBUG)
            System.out.printf("A. Server List: %s\nA. Client List: %s\n", ListUtils.transform(sFields, FieldName.instance), ListUtils.transform(cFields, FieldName.instance));
        if (sFields.size() != cFields.size()) {
            for (int x = cFields.size(); x < sFields.size(); x++) {
                FieldNode sF = sFields.get(x);
                if (sF.visibleAnnotations == null) {
                    sF.visibleAnnotations = new ArrayList<>();
                }
                sF.visibleAnnotations.add(getSideAnn(true));
                cFields.add(x++, sF);
            }
        }
        if (DEBUG)
            System.out.printf("E. Server List: %s\nE. Client List: %s\n", ListUtils.transform(sFields, FieldName.instance), ListUtils.transform(cFields, FieldName.instance));
    }

    private static class FieldName implements Function<FieldNode, String> {
        public static FieldName instance = new FieldName();

        @Override
        public String apply(FieldNode in) {
            return in.name;
        }
    }

    private void processMethods(ClassNode cClass, ClassNode sClass) {
        List<MethodNode> cMethods = cClass.methods;
        List<MethodNode> sMethods = sClass.methods;
        LinkedHashSet<MethodWrapper> allMethods = new LinkedHashSet<>();

        int cPos = 0;
        int sPos = 0;
        int cLen = cMethods.size();
        int sLen = sMethods.size();
        String clientName = "";
        String lastName = clientName;
        String serverName = "";
        while (cPos < cLen || sPos < sLen) {
            do {
                if (sPos >= sLen) {
                    break;
                }
                MethodNode sM = sMethods.get(sPos);
                serverName = sM.name;
                if (!serverName.equals(lastName) && cPos != cLen) {
                    if (DEBUG) {
                        System.out.printf("Server -skip : %s %s %d (%s %d) %d [%s]\n", sClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                    }
                    break;
                }
                MethodWrapper mw = new MethodWrapper(sM);
                mw.server = true;
                allMethods.add(mw);
                if (DEBUG) {
                    System.out.printf("Server *add* : %s %s %d (%s %d) %d [%s]\n", sClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                }
                sPos++;
            } while (sPos < sLen);
            do {
                if (cPos >= cLen) {
                    break;
                }
                MethodNode cM = cMethods.get(cPos);
                lastName = clientName;
                clientName = cM.name;
                if (!clientName.equals(lastName) && sPos != sLen) {
                    if (DEBUG) {
                        System.out.printf("Client -skip : %s %s %d (%s %d) %d [%s]\n", cClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                    }
                    break;
                }
                MethodWrapper mw = new MethodWrapper(cM);
                mw.client = true;
                allMethods.add(mw);
                if (DEBUG) {
                    System.out.printf("Client *add* : %s %s %d (%s %d) %d [%s]\n", cClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                }
                cPos++;
            } while (cPos < cLen);
        }

        cMethods.clear();
        sMethods.clear();

        for (MethodWrapper mw : allMethods) {
            if (DEBUG) {
                System.out.println(mw);
            }
            cMethods.add(mw.node);
            sMethods.add(mw.node);
            if (mw.server && mw.client) {
                // no op
            } else {
                if (mw.node.visibleAnnotations == null) {
                    mw.node.visibleAnnotations = new ArrayList<>(6);
                }

                mw.node.visibleAnnotations.add(getSideAnn(mw.client));
            }
        }
    }

    private static final class MethodWrapper {
        private MethodNode node;
        public boolean client;
        public boolean server;

        public MethodWrapper(MethodNode node) {
            this.node = node;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MethodWrapper)) {
                return false;
            }
            MethodWrapper mw = (MethodWrapper) obj;
            boolean eq = Objects.equals(node.name, mw.node.name) && Objects.equals(node.desc, mw.node.desc);
            if (eq) {
                mw.client = client | mw.client;
                mw.server = server | mw.server;
                client = client | mw.client;
                server = server | mw.server;
                if (DEBUG) {
                    System.out.printf(" eq: %s %s\n", this, mw);
                }
            }
            return eq;
        }

        @Override
        public int hashCode() {
            return Objects.hash(node.name, node.desc);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("name", node.name).add("desc", node.desc).add("server", server).add("client", client).toString();
        }
    }

    public File getClient() {
        return client;
    }
    public File getMergeCfg() {
        return mergeCfg;
    }
    public File getOutJar() {
        return outJar;
    }
    public File getServer() {
        return this.server;
    }
    public String getMcVersion() {
        return mcVersion;
    }
}
