/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.rust.cargo.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.netbeans.modules.rust.cargo.api.CargoTOML;
import org.netbeans.modules.rust.cargo.api.RustPackage;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 *
 */
public class CargoTOMLParser {

    private static final Logger LOG = Logger.getLogger(CargoTOMLParser.class.getName());

    /**
     * Parses a Cargo.toml file.
     *
     * @param cargoTomlFile The "Cargo.toml" file to parse.
     * @param cargotoml The CargoTOML resulting object.
     * @throws IOException in case of error.
     */
    public static void parseCargoToml(FileObject cargoTomlFile, CargoTOML cargotoml) throws Throwable {
        File file = FileUtil.toFile(cargoTomlFile);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            throw new IOException(String.format("Cannot read file '%s'", file.getAbsolutePath())); // NOI18N
        }
        long start = System.currentTimeMillis();
        // As per the specification, .toml files are always UTF-8
        try (final BufferedReader fileContent = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            TomlParseResult parseResult = Toml.parse(fileContent);
            List<TomlParseError> errors = parseResult.errors().stream().collect(Collectors.toList());
            if (!errors.isEmpty()) {
                final String fileName = file.getAbsolutePath();
                errors.forEach(e -> {
                    LOG.warning(String.format("Error parsing '%s': '%s'", fileName, e.getMessage())); // NOI18N
                });
                throw new IOException(String.format("Errors parsing '%s'. See log for details", fileName)); // NOI18N
            }
            String packageName = parseResult.getString("package.name"); // NOI18N
            String version = parseResult.getString("package.version"); // NOI18N
            String edition = parseResult.getString("package.edition"); // NOI18N
            edition = edition == null ? "2015" : edition;
            String rustVersion = parseResult.getString("package.rust-version"); // NOI18N
            String description = parseResult.getString("package.description"); // NOI18N
            String documentation = parseResult.getString("package.documentation"); // NOI18N
            String homepage = parseResult.getString("package.homepage");
            // TODO: Read more stuff...
            // TODO: Fire property change only if required.

            cargotoml.setPackageName(packageName);
            cargotoml.setVersion(version);
            cargotoml.setEdition(edition);
            cargotoml.setDocumentation(documentation);
            cargotoml.setHomePage(homepage);
            cargotoml.setDescription(description);
            cargotoml.setRustVersion(rustVersion);

            // dependencies
            {
                List<RustPackage> dependencies = getDependencies(cargotoml, parseResult, "dependencies");
                cargotoml.setDependencies(dependencies);
            }

            // dev-dependencies
            {
                List<RustPackage> devDependencies = getDependencies(cargotoml, parseResult, "dev-dependencies");
                cargotoml.setDevDependencies(devDependencies);
            }

            // build-dependencies
            {
                List<RustPackage> buildDependencies = getDependencies(cargotoml, parseResult, "build-dependencies");
                cargotoml.setBuildDependencies(buildDependencies);
            }

        }
        long end = System.currentTimeMillis();
        LOG.info(String.format("Parsed '%s' in %5.2g ms.", file.getAbsolutePath(), (end - start) / 1000.0)); //NOI18N
    }

    private static final List<RustPackage> getDependencies(CargoTOML cargotoml, TomlParseResult parseResult, String propertyKey) {
        TomlTable dependencies = parseResult.getTable(propertyKey);
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.<RustPackage>emptyList();
        }
        ArrayList<RustPackage> packages = new ArrayList<>(dependencies.size());
        for (Map.Entry<String, Object> declaredDependency : dependencies.entrySet()) {
            String key = declaredDependency.getKey();
            Object value = declaredDependency.getValue();
            if (value instanceof String) {
                String stringValue = (String) value;
                packages.add(new RustPackage(cargotoml, key, stringValue));
            } else {
                // TODO: Add support for github dependencies and registry dependencies https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html
                LOG.warning(String.format("Unrecognized cargo dev-dependency with key '%s', value '%s'", key, value));
            }
        }
        Collections.sort(packages, (RustPackage a, RustPackage b) -> {
            int diff = a.getName().compareTo(b.getName());
            if (diff == 0) {
                diff = a.getSemver().compareTo(b.getSemver());
            }
            return diff;
        });
        return packages;
    }

}
