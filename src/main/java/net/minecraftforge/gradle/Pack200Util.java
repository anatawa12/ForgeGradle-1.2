package net.minecraftforge.gradle;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.SortedMap;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

public class Pack200Util {
    public static final boolean isJava14;
    private static boolean useApacheVariant;

    /**
     * @return true for apache variant, false for JDK's
     */
    public static boolean isApacheVariant(Project project) {
        return useApacheVariant = isJava14 || ProjectUtils.getBooleanProperty(project, "com.anatawa12.forge.gradle.useApachePack200");
    }

    public static Object newPacker() {
        if (useApacheVariant) {
            return org.apache.commons.compress.java.util.jar.Pack200.newPacker();
        }
        return Pack200.newPacker();
    }

    public static Object newUnpacker() {
        if (useApacheVariant) {
            return org.apache.commons.compress.java.util.jar.Pack200.newUnpacker();
        }
        return Pack200.newUnpacker();
    }

    public static void pack(Object packer, JarInputStream in, OutputStream out) throws IOException {
        if (useApacheVariant) {
            ((org.apache.commons.compress.java.util.jar.Pack200.Packer) packer).pack(in, out);
        } else {
            ((Pack200.Packer) packer).pack(in, out);
        }
    }

    public static void unpack(Object unpacker, InputStream in, JarOutputStream out) throws IOException {
        if (useApacheVariant) {
            ((org.apache.commons.compress.java.util.jar.Pack200.Unpacker) unpacker).unpack(in, out);
        } else {
            ((Pack200.Unpacker) unpacker).unpack(in, out);
        }
    }

    public static SortedMap<String,String> properties(Object packer) {
        if (useApacheVariant) {
            return ((org.apache.commons.compress.java.util.jar.Pack200.Packer) packer).properties();
        } else {
            return ((Pack200.Packer) packer).properties();
        }
    }

    static {
        JavaVersion version_14 = null;
        try {
            version_14 = (JavaVersion) JavaVersion.class.getField("VERSION_14").get(null);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        isJava14 = version_14 != null && JavaVersion.current().compareTo(version_14) >= 0;
        useApacheVariant = isJava14;
    }
}
