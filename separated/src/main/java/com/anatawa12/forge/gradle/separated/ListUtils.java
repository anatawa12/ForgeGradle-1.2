package com.anatawa12.forge.gradle.separated;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListUtils {
    public static <F, T> List<T> transform(
            List<F> fromList, Function<? super F, ? extends T> function) {
        return fromList.stream().map(function).collect(Collectors.toList());
    }
}
