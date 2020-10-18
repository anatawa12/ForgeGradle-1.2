package net.minecraftforge.gradle.delayed;

import groovy.lang.Closure;
import net.minecraftforge.gradle.ArchiveTaskHelper;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

@SuppressWarnings("serial")
public class DelayedThingy extends Closure<Object> {
    private Object thing;

    public DelayedThingy(Object thing) {
        super(null);
        this.thing = thing;
    }

    public Object call(Object... objects) {
        if (thing instanceof AbstractArchiveTask)
            return ArchiveTaskHelper.getArchivePath((AbstractArchiveTask) thing);
        else if ((thing instanceof PublishArtifact))
            return ((PublishArtifact) thing).getFile();

        return thing;
    }

    public String toString() {
        return call().toString();
    }
}
