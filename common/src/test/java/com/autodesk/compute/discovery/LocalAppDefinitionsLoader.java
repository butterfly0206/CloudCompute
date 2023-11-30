package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.ServiceWithVersion;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.google.common.base.MoreObjects;
import com.google.common.collect.SortedSetMultimap;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.fail;

@Slf4j
public class LocalAppDefinitionsLoader {
    public Map<String, AppDefinition> testAppDefinitions;
    public SortedSetMultimap<String, AppDefinition>     testAppDefinitionsMultimap;

    public List<String> findFiles(final Pattern pattern, final File dir) {
        final List<String> files = new ArrayList<>();

        final File[] list = MoreObjects.firstNonNull(dir.listFiles(), new File[0]);

        for (final File f : list) {
            if (f.isDirectory()) {
                files.addAll(findFiles(pattern, f));
            } else {
                final Matcher m = pattern.matcher(f.getName());
                if (m.find()) {
                    files.add( f.toString() );
                }
            }
        }
        return files;
    }

    public List<String> findFiles(final String pattern, final File dir)
    {
        return findFiles(Pattern.compile(pattern), dir);
    }

    public void readTestAppDefinitions(final BiConsumer< String, AppDefinition> procEach ) {
        final File resourcesDirectory = new File("src/test/resources");
        final List<String> testFiles = findFiles(".*\\.json", resourcesDirectory);
        log.info(ComputeStringOps.makeString("readTestAppDefinitions found ", Long.toString(testFiles.size()), " test files"));
        for (final String filename : testFiles) {
            try {
                final byte[] encoded = Files.readAllBytes(Paths.get(filename));
                final String content = new String(encoded, StandardCharsets.UTF_8);
                final AppDefinition appDefinition = Json.mapper.readValue(content, AppDefinition.class);
                procEach.accept( filename, appDefinition );
            } catch (final Throwable e) {
                fail("Unexpected exception trying to read file " + filename + ": " + e);
            }
        }
    }

    public void loadTestAppDefinitions() {
        if (testAppDefinitions == null) {
            testAppDefinitions = new HashMap<>();
            testAppDefinitionsMultimap = ServiceDiscovery.createServiceSortedMultimap();
            readTestAppDefinitions( (filename, appDefinition) -> {
                final String normalizedFilename = filename.replaceAll("\\\\", "/");
                testAppDefinitions.put(normalizedFilename, appDefinition);
                testAppDefinitionsMultimap.put(ServiceWithVersion.makeKey(appDefinition.getAppName()), appDefinition);
                testAppDefinitionsMultimap.put(ServiceWithVersion.makeKey(appDefinition.getAppName(), appDefinition.getPortfolioVersion()), appDefinition);
                log.info(ComputeStringOps.makeString("Added appDefinition ", appDefinition.getAppName(), " with version ", appDefinition.getPortfolioVersion()));
            });
        }
        log.info(ComputeStringOps.makeString("testAppDefinitions has ", Long.toString(testAppDefinitions.size()), " entries"));
    }

}
