package com.autodesk.compute.configuration;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import lombok.SneakyThrows;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Holds the base methods to read configuration from configuration files or environment variables/
 * Access is package private.
 */
public class ConfigurationReader {

    private final Config cfg;
    private final Map<String, String> env;

    @SneakyThrows(ComputeException.class)
    public ConfigurationReader(final String conf) {
        cfg = ConfigFactory.load(conf);
        try {
            this.env = System.getenv();
        } catch (final SecurityException e) {
            throw ComputeException.builder()
                    .code(ComputeErrorCodes.CONFIGURATION)
                    .message("Could not read system environment")
                    .cause(e)
                    .build();
        }
    }

    /**
     * - helper function for reading environment variables
     *
     * @param key The environment variable name
     * @param <T> The type of the value returned
     * @return An object of the specified type
     */
    @SneakyThrows(ComputeException.class)
    private <T> Optional<T> getFromEnv(final String key, final Function<String, T> fn) {
        try {
            String var = this.env.get(key.replace('.', '_').toUpperCase(Locale.ROOT));
            if (Strings.isNullOrEmpty(var)) {
                var = this.env.get(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, key));
            }
            return Optional.ofNullable(Strings.isNullOrEmpty(var) ? null : fn.apply(var));
        } catch (final ClassCastException e) {
            throw ComputeException.builder()
                    .code(ComputeErrorCodes.CONFIGURATION)
                    .message("configuration for [" + key + "] in [environment] is the wrong type")
                    .build();
        }
    }

    /**
     * - helper function for reading string configurations from the env or a
     * conf file, with a default and a required value (strict) flag
     *
     * @param key          The name of the parameter to read
     * @param defaultValue A fallback value if not present
     * @param strict       If true, then the value *must* be present or we throw
     * @param <T>          The type of the object being read
     * @return
     */
    @SneakyThrows(ComputeException.class)
    private <T> T read(final String key, final Function<String, T> cfgFun, final Function<String, T> envFun, final T defaultValue,
                       final boolean strict) {
        final Optional<T> loaded = this.getFromEnv(key, envFun);
        if (!this.cfg.hasPath(key) && !loaded.isPresent() && strict) {
            throw ComputeException.builder()
                    .code(ComputeErrorCodes.CONFIGURATION)
                    .message("missing configuration [" + key + "]")
                    .build();
        } else if (!this.cfg.hasPath(key) && !loaded.isPresent()) {
            return defaultValue;
        } else if (loaded.isPresent()) {
            return loaded.get();
        }

        try {
            return cfgFun.apply(key);
        } catch (final ClassCastException | ConfigException.WrongType e) {
            throw ComputeException.builder().
                    code(ComputeErrorCodes.CONFIGURATION)
                    .message("configuration for [" + key + "] in [configuration file] is the wrong type")
                    .cause(e)
                    .build();
        }
    }

    /**
     * - reads a configuration string with a default value
     *
     * @param key
     * @param defaultValue
     * @return
     */
    public String readString(final String key, final String defaultValue) {
        return this.read(key, s -> this.cfg.getString(s), Function.identity(), defaultValue, false);
    }

    /**
     * - reads a configuration long with a default value
     *
     * @param key
     * @param defaultValue
     * @return
     */
    public long readLong(final String key, final long defaultValue) {
        return this.read(key, s -> this.cfg.getLong(s), Long::parseLong, defaultValue, false);
    }

    /**
     * - reads a configuration int with a default value
     *
     * @param key
     * @param defaultValue
     * @return
     */
    public int readInt(final String key, final int defaultValue) {
        return this.read(key, s -> this.cfg.getInt(s), Integer::parseInt, defaultValue, false);
    }

    /**
     * - reads a configuration boolean with a default value
     *
     * @param key
     * @param defaultValue
     * @return
     */
    public boolean readBoolean(final String key, final boolean defaultValue) {
        return this.read(key, s -> this.cfg.getBoolean(s), Boolean::parseBoolean, defaultValue, false);
    }

    /**
     * - reads a configuration string; will throw an exception if the value is
     * not present
     *
     * @param key
     * @return
     */
    public String readString(final String key) {
        return this.read(key, s -> this.cfg.getString(s), Function.identity(), "", true);
    }

    /**
     * - reads a configuration long; will throw an exception if the value is not
     * present
     *
     * @param key
     * @return
     */
    public long readLong(final String key) {
        return this.read(key, s -> this.cfg.getLong(s), Long::parseLong, -1, true).longValue();
    }

    /**
     * - reads a configuration int; will throw an exception if the value is not
     * present
     *
     * @param key
     * @return
     */
    public int readInt(final String key) {
        return this.read(key, s -> this.cfg.getInt(s), Integer::parseInt, -1, true);
    }

}
