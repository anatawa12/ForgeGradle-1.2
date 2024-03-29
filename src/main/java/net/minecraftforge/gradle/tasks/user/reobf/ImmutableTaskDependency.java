package net.minecraftforge.gradle.tasks.user.reobf;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.util.*;

public class ImmutableTaskDependency implements TaskDependency {
    private final ImmutableSet<? extends Task> immutableValues;

    public ImmutableTaskDependency() {
        this(ImmutableSet.of());
    }

    public ImmutableTaskDependency(ImmutableSet<? extends Task> immutableValues) {
        Objects.requireNonNull(immutableValues, "immutableValues");
        this.immutableValues = immutableValues;
    }

    @Override
    public Set<? extends Task> getDependencies(@Nullable Task task) {
        return immutableValues;
    }
}
