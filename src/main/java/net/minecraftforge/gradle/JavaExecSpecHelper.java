package net.minecraftforge.gradle;

import org.gradle.process.JavaExecSpec;

public class JavaExecSpecHelper {
    private static final MainSetter MAIN_SETTERS = GradleVersionUtils.choose("6.4",
            OldMainSetter::new, NewMainSetter::new);

    interface MainSetter {
        void setMain(JavaExecSpec spec, String main);
    }

    private static class OldMainSetter implements MainSetter {
        @Override
        @Deprecated
        public void setMain(JavaExecSpec spec, String main) {
            spec.setMain(main);
        }
    }

    private static class NewMainSetter implements MainSetter {
        @Override
        public void setMain(JavaExecSpec spec, String main) {
            spec.getMainClass().set(main);
        }
    }

    public static void setMainClass(JavaExecSpec spec, String main) {
        MAIN_SETTERS.setMain(spec, main);
    }
}
