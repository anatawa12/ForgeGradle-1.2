package net.minecraftforge.gradle.tasks;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import net.minecraftforge.gradle.GradleVersionUtils;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@CacheableTask
public class GenSrgTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    private DelayedFile inSrg;
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    private DelayedFile inExc;

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    private final LinkedList<File> extraExcs = new LinkedList<>();
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    private final LinkedList<File> extraSrgs = new LinkedList<>();

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    private DelayedFile methodsCsv;
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    private DelayedFile fieldsCsv;

    @OutputFile
    private DelayedFile notchToSrg;

    @OutputFile
    private DelayedFile notchToMcp;

    @OutputFile
    private DelayedFile mcpToNotch;

    @OutputFile
    private DelayedFile srgToMcp;

    @OutputFile
    private DelayedFile mcpToSrg;

    @OutputFile
    private DelayedFile srgExc;

    @OutputFile
    private DelayedFile mcpExc;

    public GenSrgTask() {
        getOutputs().doNotCacheIf("Old gradle version", e -> GradleVersionUtils.isBefore("5.3"));
    }

    @TaskAction
    public void doTask() throws IOException, CsvException {
        // csv data.  SRG -> MCP
        HashMap<String, String> methods = new HashMap<>();
        HashMap<String, String> fields = new HashMap<>();
        readCSVs(getMethodsCsv(), getFieldsCsv(), methods, fields);

        // Do SRG stuff
        SrgContainer inSrg = new SrgContainer().readSrg(getInSrg());
        Map<String, String> excRemap = readExtraSrgs(getExtraSrgs(), inSrg);
        writeOutSrgs(inSrg, methods, fields);

        // do EXC stuff
        writeOutExcs(excRemap, methods);

    }

    private static void readCSVs(File methodCsv, File fieldCsv, Map<String, String> methodMap, Map<String, String> fieldMap) throws IOException, CsvException {

        // read methods
        CSVReader csvReader = RemapSourcesTask.getReader(methodCsv);
        for (String[] s : csvReader.readAll()) {
            methodMap.put(s[0], s[1]);
        }

        // read fields
        csvReader = RemapSourcesTask.getReader(fieldCsv);
        for (String[] s : csvReader.readAll()) {
            fieldMap.put(s[0], s[1]);
        }
    }

    private Map<String, String> readExtraSrgs(FileCollection extras, SrgContainer inSrg) {
        return new HashMap<>(); //Nop this out.
        /*
        SrgContainer extraSrg = new SrgContainer().readSrgs(extras);
        // Need to convert these to Notch-SRG names. and add them to the other one.
        // These Extra SRGs are in MCP->SRG names as they are denoting dev time values.
        // So we need to swap the values we get.

        HashMap<String, String> excRemap = new HashMap<String, String>(extraSrg.methodMap.size());

        // SRG -> notch map
        Map<String, String> classMap = inSrg.classMap.inverse();
        Map<MethodData, MethodData> methodMap = inSrg.methodMap.inverse();

        // rename methods
        for (Entry<MethodData, MethodData> e : extraSrg.methodMap.inverse().entrySet())
        {
            String notchSig = remapSig(e.getValue().sig, classMap);
            String notchName = remapMethodName(e.getKey().name, notchSig, classMap, methodMap);
            //getLogger().lifecycle(e.getKey().name + " " + e.getKey().sig + " " + e.getValue().name + " " + e.getValue().sig);
            //getLogger().lifecycle(notchName       + " " + notchSig       + " " + e.getValue().name + " " + e.getValue().sig);
            inSrg.methodMap.put(new MethodData(notchName, notchSig), e.getValue());
            excRemap.put(e.getKey().name, e.getValue().name);
        }

        return excRemap;
        */
    }

    private void writeOutSrgs(SrgContainer inSrg, Map<String, String> methods, Map<String, String> fields) throws IOException {
        // ensure folders exist
        getNotchToSrg().getParentFile().mkdirs();
        getNotchToMcp().getParentFile().mkdirs();
        getSrgToMcp().getParentFile().mkdirs();
        getMcpToSrg().getParentFile().mkdirs();
        getMcpToNotch().getParentFile().mkdirs();

        // create streams
        BufferedWriter notchToSrg = Files.newBufferedWriter(getNotchToSrg().toPath());
        BufferedWriter notchToMcp = Files.newBufferedWriter(getNotchToMcp().toPath());
        BufferedWriter srgToMcp = Files.newBufferedWriter(getSrgToMcp().toPath());
        BufferedWriter mcpToSrg = Files.newBufferedWriter(getMcpToSrg().toPath());
        BufferedWriter mcpToNotch = Files.newBufferedWriter(getMcpToNotch().toPath());

        String line, temp, mcpName;
        // packages
        for (Entry<String, String> e : inSrg.packageMap.entrySet()) {
            line = "PK: " + e.getKey() + " " + e.getValue();

            // nobody cares about the packages.
            notchToSrg.write(line);
            notchToSrg.newLine();

            notchToMcp.write(line);
            notchToMcp.newLine();

            // No package changes from MCP to SRG names
            //srgToMcp.write(line);
            //srgToMcp.newLine();

            // No package changes from MCP to SRG names
            //mcpToSrg.write(line);
            //mcpToSrg.newLine();

            // reverse!
            mcpToNotch.write("PK: " + e.getValue() + " " + e.getKey());
            mcpToNotch.newLine();
        }

        // classes
        for (Entry<String, String> e : inSrg.classMap.entrySet()) {
            line = "CL: " + e.getKey() + " " + e.getValue();

            // same...
            notchToSrg.write(line);
            notchToSrg.newLine();

            // SRG and MCP have the same class names
            notchToMcp.write(line);
            notchToMcp.newLine();

            line = "CL: " + e.getValue() + " " + e.getValue();

            // deobf: same classes on both sides.
            srgToMcp.write("CL: " + e.getValue() + " " + e.getValue());
            srgToMcp.newLine();

            // reobf: same classes on both sides.
            mcpToSrg.write("CL: " + e.getValue() + " " + e.getValue());
            mcpToSrg.newLine();

            // output is notch
            mcpToNotch.write("CL: " + e.getValue() + " " + e.getKey());
            mcpToNotch.newLine();
        }

        // fields
        for (Entry<String, String> e : inSrg.fieldMap.entrySet()) {
            line = "FD: " + e.getKey() + " " + e.getValue();

            // same...
            notchToSrg.write("FD: " + e.getKey() + " " + e.getValue());
            notchToSrg.newLine();

            temp = e.getValue().substring(e.getValue().lastIndexOf('/') + 1);
            mcpName = e.getValue();
            if (fields.containsKey(temp))
                mcpName = mcpName.replace(temp, fields.get(temp));

            // SRG and MCP have the same class names
            notchToMcp.write("FD: " + e.getKey() + " " + mcpName);
            notchToMcp.newLine();

            // srg name -> mcp name
            srgToMcp.write("FD: " + e.getValue() + " " + mcpName);
            srgToMcp.newLine();

            // mcp name -> srg name
            mcpToSrg.write("FD: " + mcpName + " " + e.getValue());
            mcpToSrg.newLine();

            // output is notch
            mcpToNotch.write("FD: " + mcpName + " " + e.getKey());
            mcpToNotch.newLine();
        }

        // methods
        for (Entry<MethodData, MethodData> e : inSrg.methodMap.entrySet()) {
            line = "MD: " + e.getKey() + " " + e.getValue();

            // same...
            notchToSrg.write("MD: " + e.getKey() + " " + e.getValue());
            notchToSrg.newLine();

            temp = e.getValue().name.substring(e.getValue().name.lastIndexOf('/') + 1);
            mcpName = e.getValue().toString();
            if (methods.containsKey(temp))
                mcpName = mcpName.replace(temp, methods.get(temp));

            // SRG and MCP have the same class names
            notchToMcp.write("MD: " + e.getKey() + " " + mcpName);
            notchToMcp.newLine();

            // srg name -> mcp name
            srgToMcp.write("MD: " + e.getValue() + " " + mcpName);
            srgToMcp.newLine();

            // mcp name -> srg name
            mcpToSrg.write("MD: " + mcpName + " " + e.getValue());
            mcpToSrg.newLine();

            // output is notch
            mcpToNotch.write("MD: " + mcpName + " " + e.getKey());
            mcpToNotch.newLine();
        }

        notchToSrg.flush();
        notchToSrg.close();

        notchToMcp.flush();
        notchToMcp.close();

        srgToMcp.flush();
        srgToMcp.close();

        mcpToSrg.flush();
        mcpToSrg.close();

        mcpToNotch.flush();
        mcpToNotch.close();
    }

    private void writeOutExcs(Map<String, String> excRemap, Map<String, String> methods) throws IOException {
        // ensure folders exist
        getSrgExc().getParentFile().mkdirs();
        getMcpExc().getParentFile().mkdirs();

        // create streams
        BufferedWriter srgOut = Files.newBufferedWriter(getSrgExc().toPath());
        BufferedWriter mcpOut = Files.newBufferedWriter(getMcpExc().toPath());

        // read and write existing lines
        List<String> excLines = Files.readAllLines(getInExc().toPath());
        String[] split;
        for (String line : excLines) {
            // its already in SRG names.
            srgOut.write(line);
            srgOut.newLine();

            // remap MCP.

            // split line up
            split = line.split("=");
            int sigIndex = split[0].indexOf('(');
            int dotIndex = split[0].indexOf('.');

            // not a method? wut?
            if (sigIndex == -1 || dotIndex == -1) {
                mcpOut.write(line);
                mcpOut.newLine();
                continue;
            }

            // get new name
            String name = split[0].substring(dotIndex + 1, sigIndex);
            if (methods.containsKey(name))
                name = methods.get(name);

            // write remapped line
            mcpOut.write(split[0].substring(0, dotIndex) + "." + name + split[0].substring(sigIndex) + "=" + split[1]);
            mcpOut.newLine();

        }

        for (File f : getExtraExcs()) {
            List<String> lines = Files.readAllLines(f.toPath());

            for (String line : lines) {
                // these are in MCP names
                mcpOut.write(line);
                mcpOut.newLine();

                // remap SRG

                // split line up
                split = line.split("=");
                int sigIndex = split[0].indexOf('(');
                int dotIndex = split[0].indexOf('.');

                // not a method? wut?
                if (sigIndex == -1 || dotIndex == -1) {
                    srgOut.write(line);
                    srgOut.newLine();
                    continue;
                }

                // get new name
                String name = split[0].substring(dotIndex + 1, sigIndex);
                if (excRemap.containsKey(name))
                    name = excRemap.get(name);

                // write remapped line
                srgOut.write(split[0].substring(0, dotIndex) + name + split[0].substring(sigIndex) + "=" + split[1]);
                srgOut.newLine();
            }
        }

        srgOut.flush();
        srgOut.close();

        mcpOut.flush();
        mcpOut.close();
    }

    /*
        private String remapMethodName(String qualified, String notchSig, Map<String, String> classMap, Map<MethodData, MethodData> methodMap)
        {

            for (MethodData data : methodMap.keySet())
            {
                if (data.name.equals(qualified))
                    return methodMap.get(data).name;
            }

            String cls = qualified.substring(0, qualified.lastIndexOf('/'));
            String name = qualified.substring(cls.length() + 1);
            getLogger().lifecycle(qualified);
            getLogger().lifecycle(cls + " " + name);

            String ret = classMap.get(cls);
            if (ret != null)
                cls = ret;

            return cls + '/' + name;
        }

        private String remapSig(String sig, Map<String, String> classMap)
        {
            StringBuilder newSig = new StringBuilder(sig.length());

            int last = 0;
            int start = sig.indexOf('L');
            while(start != -1)
            {
                newSig.append(sig.substring(last, start));
                int next = sig.indexOf(';', start);
                newSig.append('L').append(remap(sig.substring(start + 1, next), classMap)).append(';');
                last = next + 1;
                start = sig.indexOf('L', next);
            }
            newSig.append(sig.substring(last));

            return newSig.toString();
        }

        private static String remap(String thing, Map<String, String> map)
        {
            if (map.containsKey(thing))
                return map.get(thing);
            else
                return thing;
        }
    */

    @Inject
    protected ObjectFactory getInjectedObjectFactory() {
        throw new IllegalStateException("must be injected");
    }

    public File getInSrg() {
        return inSrg.call();
    }

    public void setInSrg(DelayedFile inSrg) {
        this.inSrg = inSrg;
    }

    public File getInExc() {
        return inExc.call();
    }

    public void setInExc(DelayedFile inSrg) {
        this.inExc = inSrg;
    }

    public File getMethodsCsv() {
        return methodsCsv.call();
    }

    public void setMethodsCsv(DelayedFile methodsCsv) {
        this.methodsCsv = methodsCsv;
    }

    public File getFieldsCsv() {
        return fieldsCsv.call();
    }

    public void setFieldsCsv(DelayedFile fieldsCsv) {
        this.fieldsCsv = fieldsCsv;
    }

    public File getNotchToSrg() {
        return notchToSrg.call();
    }

    public void setNotchToSrg(DelayedFile deobfSrg) {
        this.notchToSrg = deobfSrg;
    }

    public File getNotchToMcp() {
        return notchToMcp.call();
    }

    public void setNotchToMcp(DelayedFile deobfSrg) {
        this.notchToMcp = deobfSrg;
    }

    public File getSrgToMcp() {
        return srgToMcp.call();
    }

    public void setSrgToMcp(DelayedFile deobfSrg) {
        this.srgToMcp = deobfSrg;
    }

    public File getMcpToSrg() {
        return mcpToSrg.call();
    }

    public void setMcpToSrg(DelayedFile reobfSrg) {
        this.mcpToSrg = reobfSrg;
    }

    public File getMcpToNotch() {
        return mcpToNotch.call();
    }

    public void setMcpToNotch(DelayedFile reobfSrg) {
        this.mcpToNotch = reobfSrg;
    }

    public File getSrgExc() {
        return srgExc.call();
    }

    public void setSrgExc(DelayedFile inSrg) {
        this.srgExc = inSrg;
    }

    public File getMcpExc() {
        return mcpExc.call();
    }

    public void setMcpExc(DelayedFile inSrg) {
        this.mcpExc = inSrg;
    }

    public FileCollection getExtraExcs() {
        return GradleVersionUtils.choose("5.3", () -> getProject().files(extraExcs), () -> getInjectedObjectFactory().fileCollection().from(extraExcs));
    }

    public void addExtraExc(File file) {
        extraExcs.add(file);
    }

    public FileCollection getExtraSrgs() {
        return GradleVersionUtils.choose("5.3", () -> getProject().files(extraSrgs), () -> getInjectedObjectFactory().fileCollection().from(extraSrgs));
    }

    public void addExtraSrg(File file) {
        extraSrgs.add(file);
    }
}
