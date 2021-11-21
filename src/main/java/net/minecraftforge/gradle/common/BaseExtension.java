package net.minecraftforge.gradle.common;

import com.google.common.base.Strings;
import net.minecraftforge.gradle.GradleConfigurationException;
import org.gradle.api.Project;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

public class BaseExtension {
    protected transient Project project;
    protected String version = "null";
    protected String mcpVersion = "unknown";
    protected String runDir = "run";
    private LinkedList<String> srgExtra = new LinkedList<String>();

    protected boolean mappingsSet = false;
    protected String mappingsChannel = null;
    protected int mappingsVersion = -1;
    protected String customVersion = null;

    public BaseExtension(BasePlugin<? extends BaseExtension> plugin) {
        this.project = plugin.project;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;

        // maybe they set the mappings first
        checkMappings();
    }

    public String getMcpVersion() {
        return mcpVersion;
    }

    public void setMcpVersion(String mcpVersion) {
        this.mcpVersion = mcpVersion;
    }

    public void setRunDir(String value) {
        this.runDir = value;
    }

    public String getRunDir() {
        return this.runDir;
    }

    @Deprecated
    public void setAssetDir(String value) {
        setRunDir(value + "/..");
        project.getLogger().warn("The assetDir is deprecated!  I actually just did all this generalizing stuff just now.. Use runDir instead! runDir set to " + runDir);
        project.getLogger().warn("The runDir should be the location where you want MC to be run, usually he parent of the asset dir");
    }

    public LinkedList<String> getSrgExtra() {
        return srgExtra;
    }

    public void srgExtra(String in) {
        srgExtra.add(in);
    }

    public void copyFrom(BaseExtension ext) {
        if ("null".equals(version)) {
            setVersion(ext.getVersion());
        }

        if ("unknown".equals(mcpVersion)) {
            setMcpVersion(ext.getMcpVersion());
        }
    }

    public String getMappings() {
        return mappingsChannel + "_" + (customVersion == null ? mappingsVersion : customVersion);
    }

    public String getMappingsChannel() {
        return mappingsChannel;
    }

    public String getMappingsChannelNoSubtype() {
        int underscore = mappingsChannel.indexOf('_');
        if (underscore <= 0) // already has docs.
            return mappingsChannel;
        else
            return mappingsChannel.substring(0, underscore);
    }

    public String getMappingsVersion() {
        return customVersion == null ? "" + mappingsVersion : customVersion;
    }

    public boolean mappingsSet() {
        return mappingsSet;
    }

    public void setMappings(String mappings) {
        if (Strings.isNullOrEmpty(mappings)) {
            mappingsChannel = null;
            mappingsVersion = -1;
            return;
        }

        mappings = mappings.toLowerCase();

        if (!mappings.contains("_")) {
            throw new IllegalArgumentException("Mappings must be in format 'channel_version'. eg: snapshot_20140910");
        }

        int index = mappings.lastIndexOf('_');
        mappingsChannel = mappings.substring(0, index);
        customVersion = mappings.substring(index + 1);

        if (!customVersion.equals("custom")) {
            try {
                mappingsVersion = Integer.parseInt(customVersion);
                customVersion = null;
            } catch (NumberFormatException e) {
                throw new GradleConfigurationException("The mappings version must be a number! eg: channel_### or channel_custom (for custom mappings).");
            }
        }

        mappingsSet = true;

        // check
        checkMappings();
    }

    /**
     * Checks that the set mappings are valid based on the channel, version, and MC version.
     * If the mappings are invalid, this method will throw a runtime exception.
     */
    protected void checkMappings() {
        // mappings or mc version are null
        if (!mappingsSet || "null".equals(version) || Strings.isNullOrEmpty(version) || customVersion != null)
            return;

        String channel = getMappingsChannelNoSubtype();
        if (!checkMappingsVersion(channel, version, mappingsVersion, "HEAD")
                && !checkMappingsVersion(channel, version, mappingsVersion, "GET")) {
            throw new GradleConfigurationException(
                    "There is no such MCP version " + mappingsVersion + " in channel " + channel + " for " + version + "."
            );
        }
    }

    private boolean checkMappingsVersion(String channel, String version, int mappingsVersion, String method) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(
                    "https://maven.minecraftforge.net/de/oceanlabs/mcp" +
                            "/mcp_" + channel +
                            "/" + mappingsVersion + "-" + version +
                            "/mcp_" + channel + "-" + mappingsVersion + "-" + version + ".zip"
            );

            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", Constants.USER_AGENT);

            con.connect();

            return con.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static boolean searchArray(int[] array, int key) {
        Arrays.sort(array);
        int foundIndex = Arrays.binarySearch(array, key);
        return foundIndex >= 0 && array[foundIndex] == key;
    }
}
