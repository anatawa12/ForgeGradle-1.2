package net.minecraftforge.gradle.tasks;

import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import static net.minecraftforge.gradle.user.UserConstants.WRAPPER_ARTIFACT_GROUP_ID;

public class GenWrapperArtifactTask extends DefaultTask {
    @OutputFile
    DelayedFile ivyXml;

    @OutputFile
    DelayedFile emptyJar;

    @Input
    DelayedString moduleName;

    @Input
    DelayedBase<Boolean> isDecomp;

    @Input
    DelayedString srcDepName;

    @Input
    DelayedString binDepName;

    @Input
    DelayedString version;

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
        return isDecomp.call();
    }

    public void setIsDecomp(DelayedBase<Boolean> isDecomp) {
        this.isDecomp = isDecomp;
    }

    
    public void setIsDecomp(final boolean isDecomp) {
        this.isDecomp = new DelayedBase<Boolean>(null, null) {
            @Override
            public Boolean resolveDelayed() {
                return isDecomp;
            }
        };
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

    @TaskAction
    public void execute() throws IOException {
        generateEmptyJar(getEmptyJar());
        generateIvyXml(getIvyXml());
    }

    private static void generateEmptyJar(File emptyJar) throws IOException {
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(emptyJar);
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
        } finally {
            if (stream != null) stream.close();
        }
    }

    private void generateIvyXml(File ivyXml) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ivy-module version=\"2.0\">\n" +
                "  <info organisation=\"" + WRAPPER_ARTIFACT_GROUP_ID + "\"\n" +
                "        module=\"" + getModuleName() + "\"\n" +
                "        revision=\"" + getVersion() + "\"/>\n" +
                "  <dependencies>\n" +
                "    <dependency name=\"" + (getIsDecomp() ? getSrcDepName() : getBinDepName()) + "\"\n" +
                "                rev=\"" + getVersion() + "\"/>\n" +
                "  </dependencies>\n" +
                "</ivy-module>\n";

        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(ivyXml);
            stream.write(xml.getBytes(Charset.forName("UTF-8")));
        } finally {
            if (stream != null) stream.close();
        }
    }
}
