package org.olo.definition.runtime;

/**
 * Declared execution environment for a {@link RuntimeBindingDefinition}.
 * When {@code implementationClass} is set and {@code runtime} is omitted, the default is {@link #JAVA}.
 */
public final class RuntimeKind {

    public static final String JAVA = "java";
    public static final String PYTHON = "python";
    public static final String HTTP = "http";

    private RuntimeKind() {
    }

    public static boolean isKnown(String runtime) {
        return JAVA.equals(runtime) || PYTHON.equals(runtime) || HTTP.equals(runtime);
    }
}
