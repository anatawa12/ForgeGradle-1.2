package net.minecraftforge.gradle;

import com.google.common.collect.ImmutableMap;
import net.minecraftforge.gradle.user.patch.ForgeUserPlugin;
import net.minecraftforge.gradle.user.patch.UserPatchExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExtensionForgeVersionTest {
    private Project testProject;
    private UserPatchExtension ext;

    @BeforeEach
    public void setupProject() throws IOException {
        this.testProject = ProjectBuilder.builder()
                .withName("testProject")
                .withProjectDir(Files.createTempDirectory(String.valueOf(System.currentTimeMillis())).toFile())
                .build();
        assertNotNull(this.testProject);
        this.testProject.apply(ImmutableMap.of("plugin", ForgeUserPlugin.class));

        this.ext = this.testProject.getExtensions().findByType(UserPatchExtension.class);   // unlike getByType(), does not throw exception
        assertNotNull(this.ext);
    }

    // Invalid version notation! The following are valid notations. Buildnumber, version, version-branch, mcversion-version-branch, and pomotion (sic)

    @Test
    public void testValidMcVersionWithVersion() {
        // mcversion-version
        this.ext.setVersion("1.7.10-10.13.2.1256");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getApiVersion(), "1.7.10-10.13.2.1256");
    }

    @Test
    public void testValidMcVersionWithVersionAndBranch() {
        // mcversion-version-branch
        this.ext.setVersion("1.8-11.14.0.1257-1.8");
        assertEquals(this.ext.getVersion(), "1.8");
        assertEquals(this.ext.getApiVersion(), "1.8-11.14.0.1257-1.8");
    }

    @Test
    public void testZeroBuildnumber() {
        // 0 as the buildnumber
        this.ext.setVersion("1.8-11.14.1.0");
    }
}
