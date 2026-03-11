package ru.hgd.sdlc.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for the compiler module.
 * Controls compiler behavior, validation strictness, and serialization settings.
 */
@ConfigurationProperties(prefix = "sdlc.compiler")
public class CompilerProperties {

    /**
     * Whether to fail compilation on warnings.
     * When true, any validation warning is treated as an error.
     */
    private boolean strictMode = false;

    /**
     * Default serialization format for compiled IR.
     * Supported values: json, yaml
     */
    private SerializationFormat defaultSerializationFormat = SerializationFormat.JSON;

    /**
     * Whether to enable caching of parsed and compiled documents.
     */
    private boolean cacheEnabled = true;

    /**
     * Maximum allowed file size for markdown documents.
     */
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    /**
     * Whether pretty printing is enabled for JSON output.
     */
    private boolean prettyPrint = false;

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public SerializationFormat getDefaultSerializationFormat() {
        return defaultSerializationFormat;
    }

    public void setDefaultSerializationFormat(SerializationFormat defaultSerializationFormat) {
        this.defaultSerializationFormat = defaultSerializationFormat;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Supported serialization formats for compiled IR.
     */
    public enum SerializationFormat {
        JSON,
        YAML
    }
}
