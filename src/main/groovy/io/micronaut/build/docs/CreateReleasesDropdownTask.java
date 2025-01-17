/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.docs;

import io.micronaut.docs.GitHubTagsParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@CacheableTask
public abstract class CreateReleasesDropdownTask extends DefaultTask {
    private static final int CACHE_IN_SECONDS = 3600;
    private static final Logger LOG = LoggerFactory.getLogger(CreateReleasesDropdownTask.class);
    private static final String MICRONAUT_GITHUB_ORGANIZATION = "micronaut-projects";
    private static final String MICRONAUT_CORE_REPOSITORY = "micronaut-core";
    public static final String PROJECTS_MICRONAUT_CORE = "micronaut-projects/micronaut-core";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSourceIndex();

    @Input
    public abstract Property<String> getSlug();

    @Input
    public abstract Property<String> getVersion();

    @OutputFile
    public abstract RegularFileProperty getOutputIndex();

    @Input
    protected Provider<Long> getTimestamp() {
        return getProviders().provider(() -> {
            long seconds = System.currentTimeMillis() / 1000;
            long base = seconds / CACHE_IN_SECONDS;
            return base * CACHE_IN_SECONDS;
        });
    }

    @Internal
    public abstract Property<String> getVersionsJson();

    @Inject
    protected abstract ProviderFactory getProviders();

    @TaskAction
    public void createDropdown() throws IOException {
        String slug = getSlug().get();
        String version = getVersion().get();

        String selectHtml = composeSelectHtml(getVersionsJson().get(), slug, version);


        String versionHtml = "<p><strong>Version:</strong> " + version + "</p>";
        String substitute = selectHtml.replaceAll(" style='margin-top: 10px' ", " style='max-width: 200px' ");
        String versionWithSelectHtml = "<p><strong>Version:</strong> " + substitute + " </p>";

        String original = getProviders().fileContents(getSourceIndex()).getAsText().get();
        File targetFile = getOutputIndex().get().getAsFile();
        if (targetFile.getParentFile().isDirectory() || targetFile.getParentFile().mkdirs()) {
            try (OutputStream out = new FileOutputStream(targetFile)) {
                out.write(original.replace(versionHtml, versionWithSelectHtml).getBytes("utf-8"));
            }
        } else {
            throw new GradleException("Cannot create " + targetFile);
        }
    }

    public static String composeSelectHtml(String json, String slug, String version) {
        String[] splitted = slug.split("/");
        String org = splitted[0];
        String repo = splitted[1];

        StringBuilder selectHtml = new StringBuilder("<select style='margin-top: 10px' onChange='window.document.location.href=this.options[this.selectedIndex].value;'>");
        String snapshotHref = snapshotRefUrl(org, repo);
        selectHtml.append(option(snapshotHref, "SNAPSHOT", version.endsWith("SNAPSHOT")));
        String latestHref = latestRefUrl(org, repo);
        selectHtml.append(option(latestHref, "LATEST", false));
        GitHubTagsParser.toVersions(json).forEach(softwareVersion -> {
            String versionName = softwareVersion.getVersionText();
            String href = "https://" + org + ".github.io/" + repo + "/" + versionName + "/guide/index.html";
            if (PROJECTS_MICRONAUT_CORE.equals(slug)) {
                href = "https://docs.micronaut.io/" + versionName + "/guide/index.html";
            }
            selectHtml.append(option(href, versionName, version.equals(versionName)));

        });
        selectHtml.append("</select>");
        return selectHtml.toString();
    }

    private static String option(String href, String text, boolean selected) {
        StringBuilder sb = new StringBuilder();
        if (selected) {
            sb.append("<option selected='selected' value='").append(href).append("'>").append(text).append("</option>");
        } else {
            sb.append("<option value='").append(href).append("'>").append(text).append("</option>");
        }
        return sb.toString();
    }


    static String snapshotRefUrl(String org, String repo) {
        if (MICRONAUT_GITHUB_ORGANIZATION.equals(org) && MICRONAUT_CORE_REPOSITORY.equals(repo)) {
            return "https://docs.micronaut.io/snapshot/guide/index.html";
        }
        return String.format("https://%s.github.io/%s/snapshot/guide/index.html", org, repo);
    }

    static String latestRefUrl(String org, String repo) {
        if (MICRONAUT_GITHUB_ORGANIZATION.equals(org) && MICRONAUT_CORE_REPOSITORY.equals(repo)) {
            return "https://docs.micronaut.io/latest/guide/index.html";
        }
        return String.format("https://%s.github.io/%s/latest/guide/index.html", org, repo);
    }

}
