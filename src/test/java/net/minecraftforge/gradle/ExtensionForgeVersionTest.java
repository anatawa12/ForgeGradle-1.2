package net.minecraftforge.gradle;

import com.google.common.collect.ImmutableMap;
import net.minecraftforge.gradle.user.patch.ForgeUserPlugin;
import net.minecraftforge.gradle.user.patch.UserPatchExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ExtensionForgeVersionTest {
    private Project testProject;
    private UserPatchExtension ext;

    @Before
    public void setupProject() {
        this.testProject = ProjectBuilder.builder().build();
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
