package net.minecraftforge.gradle.tasks;

import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

import static net.minecraftforge.gradle.user.UserConstants.WRAPPER_ARTIFACT_GROUP_ID;

public class GenWrapperArtifactTask extends DefaultTask {
    @OutputFile
    DelayedFile ivyXml;

    @OutputFile
    DelayedFile emptyJar;

    @Input
    DelayedString moduleName;

    @Input
    BooleanSupplier isDecomp;

    @Input
    DelayedString srcDepName;

    @Input
    DelayedString binDepName;

    @Input
    DelayedString version;

    @InputFiles
    @Optional
    Configuration configuration;

    public File getIvyXml() {
        return ivyXml.call();
    }

    public void setIvyXml(DelayedFile ivyXml) {
        this.ivyXml = ivyXml;
    }

    
    public void setIvyXml(File ivyXml) {
        this.ivyXml = new DelayedFile(ivyXml);
    }

    public File getEmptyJar() {
        return emptyJar.call();
    }

    public void setEmptyJar(DelayedFile emptyJar) {
        this.emptyJar = emptyJar;
    }

    
    public void setEmptyJar(File emptyJar) {
        this.emptyJar = new DelayedFile(emptyJar);
    }

    public String getModuleName() {
        return moduleName.call();
    }

    public void setModuleName(DelayedString moduleName) {
        this.moduleName = moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = DelayedString.resolved(moduleName);
    }

    public boolean getIsDecomp() {
        return isDecomp.getAsBoolean();
    }

    /**
     * @deprecated use {@link #setIsDecomp(BooleanSupplier)} variant
     */
    @Deprecated
    public void setIsDecomp(DelayedBase<Boolean> isDecomp) {
        this.isDecomp = isDecomp::call;
    }

    public void setIsDecomp(BooleanSupplier isDecomp) {
        this.isDecomp = isDecomp;
    }

    public void setIsDecomp(final boolean isDecomp) {
        this.isDecomp = () -> isDecomp;
    }

    public String getSrcDepName() {
        return srcDepName.call();
    }

    public void setSrcDepName(DelayedString srcDepName) {
        this.srcDepName = srcDepName;
    }

    
    public void setSrcDepName(String srcDepName) {
        this.srcDepName = DelayedString.resolved(srcDepName);
    }

    public String getBinDepName() {
        return binDepName.call();
    }

    public void setBinDepName(DelayedString binDepName) {
        this.binDepName = binDepName;
    }

    
    public void setBinDepName(String binDepName) {
        this.binDepName = DelayedString.resolved(binDepName);
    }

    public String getVersion() {
        return version.call();
    }

    public void setVersion(DelayedString version) {
        this.version = version;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @TaskAction
    public void execute() throws IOException {
        generateEmptyJar(getEmptyJar());
        generateIvyXml(getIvyXml());
    }

    private static void generateEmptyJar(File emptyJar) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(emptyJar)) {
            // this is empty zip file
            stream.write(new byte[]{
                    0x50, 0x4B, 0x05, 0x06,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00,
            });
            stream.flush();
        }
    }

    private void generateIvyXml(File ivyXml) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<ivy-module version=\"2.0\">\n");
        xml.append("  <info organisation=\"" + WRAPPER_ARTIFACT_GROUP_ID + "\"\n");
        xml.append("        module=\"").append(getModuleName()).append("\"\n");
        xml.append("        revision=\"").append(getVersion()).append("\"/>\n");
        xml.append("  <dependencies>\n");
        xml.append("    <dependency org=\"\"\n");
        xml.append("                name=\"").append(getIsDecomp() ? getSrcDepName() : getBinDepName()).append("\"\n");
        xml.append("                rev=\"").append(getVersion()).append("\"/>\n");

        Configuration config = getConfiguration();
        if (config != null) {
            for (Dependency dependency : config.getAllDependencies()) {
                xml.append("    <dependency ");
                if (dependency.getGroup() != null)
                    xml.append("org=\"").append(dependency.getGroup()).append("\"\n                ");
                else
                    xml.append("org=\"\"\n                ");
                xml.append("name=\"").append(dependency.getName()).append("\"");
                if (dependency.getVersion() != null)
                    xml.append("\n                ").append("rev=\"").append(dependency.getVersion()).append("\"");
                xml.append("/>\n");
            }
        }

        xml.append("  </dependencies>\n");
        xml.append("</ivy-module>\n");

        try (FileOutputStream stream = new FileOutputStream(ivyXml)) {
            stream.write(xml.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
