package net.minecraftforge.gradle.curseforge;

import groovy.lang.Closure;
import net.minecraftforge.gradle.ArchiveTaskHelper;
import net.minecraftforge.gradle.GradleVersionUtils;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.user.UserExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;

public class CursePlugin implements Plugin<Project> {

    @SuppressWarnings("serial")
    @Override
    public void apply(final Project project) {
        GradleVersionUtils.checkSupportedVersion();
        // create task
        final CurseUploadTask upload = project.getTasks().create("curse", CurseUploadTask.class);
        upload.setGroup("ForgeGradle");
        upload.setDescription("Uploads an artifact to CurseForge. Configureable in the curse{} block.");

        // set artifact
        upload.setArtifact(new Closure<File>(null, null) {
            @Override
            public File call() {
                if (project.getPlugins().hasPlugin("java"))
                    return ArchiveTaskHelper.getArchivePath((Jar) project.getTasks().getByName("jar"));
                return null;
            }
        });

        // configure task extra.
        project.afterEvaluate(arg0 -> {
            // dont continue if its already failed!
            if (project.getState().getFailure() != null)
                return;

            UserBasePlugin<UserExtension> plugin = userPluginApplied(project);
            upload.addGameVersion(plugin.getExtension().getVersion());

            upload.dependsOn("reobf");
        });
    }

    @SuppressWarnings("unchecked")
    private UserBasePlugin<UserExtension> userPluginApplied(Project project) {
        // search for overlays..
        for (Plugin<Project> p : project.getPlugins()) {
            if (p instanceof UserBasePlugin) {
                return (UserBasePlugin<UserExtension>) p;
            }
        }

        return null;
    }

}
