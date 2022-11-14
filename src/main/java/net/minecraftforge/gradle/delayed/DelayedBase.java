package net.minecraftforge.gradle.delayed;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BaseExtension;
import net.minecraftforge.gradle.common.JenkinsExtension;
import org.gradle.api.Project;

import java.util.function.Supplier;

import static net.minecraftforge.gradle.common.Constants.EXT_NAME_JENKINS;
import static net.minecraftforge.gradle.common.Constants.EXT_NAME_MC;

@SuppressWarnings("serial")
public abstract class DelayedBase<V> extends Closure<V> implements Supplier<V> {
    protected Project project;
    private V resolved;
    protected String pattern;
    public boolean resolveOnce = true;
    @SuppressWarnings("rawtypes")
    protected IDelayedResolver[] resolvers;
    public static final IDelayedResolver<BaseExtension> RESOLVER = (pattern, project, extension) -> pattern;

    public DelayedBase(Project owner, String pattern) {
        this(owner, pattern, RESOLVER);
    }

    @SafeVarargs
    public DelayedBase(Project owner, String pattern, IDelayedResolver<? extends BaseExtension>... resolvers) {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = resolvers;
    }

    public abstract V resolveDelayed();

    @Override
    public final V call() {
        if (resolved == null || !resolveOnce) {
            resolved = resolveDelayed();
        }

        return resolved;
    }

    @Override
    public final V get() {
        return call();
    }

    @Override
    public String toString() {
        return call().toString();
    }

    // interface
    public interface IDelayedResolver<K extends BaseExtension> {
        String resolve(String pattern, Project project, K extension);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String resolve(String pattern, Project project, IDelayedResolver... resolvers) {
        project.getLogger().debug("Resolving: " + pattern);
        BaseExtension extension = (BaseExtension) project.getExtensions().getByName(EXT_NAME_MC);
        JenkinsExtension jenkins = (JenkinsExtension) project.getExtensions().getByName(EXT_NAME_JENKINS);

        String build = "0";
        if (System.getenv().containsKey("BUILD_NUMBER")) {
            build = System.getenv("BUILD_NUMBER");
        }

        // resolvers first
        for (IDelayedResolver r : resolvers) {
            pattern = r.resolve(pattern, project, extension);
        }

        pattern = pattern.replace("{MC_VERSION}", extension.getVersion());
        pattern = pattern.replace("{MC_VERSION_SAFE}", extension.getVersion().replace('-', '_'));
        pattern = pattern.replace("{MCP_VERSION}", extension.getMcpVersion());
        pattern = pattern.replace("{CACHE_DIR}", project.getGradle().getGradleUserHomeDir().getAbsolutePath().replace('\\', '/') + "/caches");
        pattern = pattern.replace("{BUILD_DIR}", project.getBuildDir().getAbsolutePath().replace('\\', '/'));
        pattern = pattern.replace("{BUILD_NUM}", build);
        pattern = pattern.replace("{PROJECT}", project.getName());
        pattern = pattern.replace("{RUN_DIR}", extension.getRunDir().replace('\\', '/'));

        if (extension.mappingsSet()) {
            pattern = pattern.replace("{MAPPING_CHANNEL}", extension.getMappingsChannel());
            pattern = pattern.replace("{MAPPING_CHANNEL_DOC}", extension.getMappingsChannelNoSubtype());
            pattern = pattern.replace("{MAPPING_VERSION}", extension.getMappingsVersion());
        }

        pattern = pattern.replace("{JENKINS_SERVER}", jenkins.getServer());
        pattern = pattern.replace("{JENKINS_JOB}", jenkins.getJob());
        pattern = pattern.replace("{JENKINS_AUTH_NAME}", jenkins.getAuthName());
        pattern = pattern.replace("{JENKINS_AUTH_PASSWORD}", jenkins.getAuthPassword());

        project.getLogger().debug("Resolved:  " + pattern);
        return pattern;
    }
}
