package net.minecraftforge.gradle;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Throwables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

public class ThrowableUtils {
    @CanIgnoreReturnValue
    @GwtIncompatible
    public static RuntimeException propagate(Throwable throwable) {
        Throwables.throwIfUnchecked(throwable);
        throw new RuntimeException(throwable);
    }
}
