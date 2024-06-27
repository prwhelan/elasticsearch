/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.ditto.schema;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.yaml.YamlXContent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DittoSchemaLoader {

    DittoSchema load(String yml) {
        try (XContentParser parser = YamlXContent.yamlXContent.createParser(XContentParserConfiguration.EMPTY, loadResource(yml))) {
            return parse(parser.map());
        } catch (Exception e) {
            throw new ElasticsearchParseException("Failed to parse yml file [" + yml + "]", e);
        }
    }

    static String loadResource(String name) throws IOException {
        InputStream is = DittoSchemaLoader.class.getResourceAsStream(name);
        if (is == null) {
            throw new IOException("Resource [" + name + "] not found in classpath.");
        }
        return Streams.readFully(is).utf8ToString();
    }

    // TODO this is not efficient
    @SuppressWarnings({ "unchecked" })
    private DittoSchema parse(Map<String, Object> ymlMap) {
        var service = (String) ymlMap.get("service");
        var type = (String) ymlMap.get("type");
        var tokenLimit = (Integer) ymlMap.get("tokenLimit");
        var rateLimit = Long.valueOf((Integer) ymlMap.get("requestsPerMinute"));
        var rateLimitGroup = ymlMap.get("rateLimitGroup");

        var uri = URI.create((String) ymlMap.get("uri"));

        var auth = (Map<String, String>) ymlMap.get("auth");
        var authSettings = Map.ofEntries(parseAuthSettings(auth));

        var headers = (Map<String, String>) ymlMap.get("headers");
        var serviceSettingsHeaders = parseAnnoyingString("service_settings", headers);
        var taskSettingsHeaders = parseAnnoyingString("task_settings", headers);
        var inferenceRequestHeaders = parseAnnoyingString("inference_request", headers);
        var hardcodedInputHeaders = parseHardcoded(headers);

        var body = (Map<String, String>) ymlMap.get("body");
        var serviceSettingsBody = parseAnnoyingString("service_settings", body);
        var taskSettingsBody = parseAnnoyingString("task_settings", body);
        var inferenceRequestBody = parseAnnoyingString("inference_request", body);
        var hardcodedInputBody = parseHardcoded(body);

        var response = (Map<String, String>) ymlMap.get("response");
        var responseBody = parseAnnoyingString("", response);

        return new DittoSchema(
            service,
            type,
            uri,
            tokenLimit,
            rateLimit,
            rateLimitGroup,
            taskSettingsHeaders,
            taskSettingsBody,
            serviceSettingsHeaders,
            serviceSettingsBody,
            authSettings,
            inferenceRequestHeaders,
            inferenceRequestBody,
            hardcodedInputHeaders,
            hardcodedInputBody,
            responseBody
        );
    }

    private Map.Entry<List<String>, DittoSchemaMapping> parseAuthSettings(Map<String, String> auth) {
        return switch (auth.get("type")) {
            case "api-key" -> parse("api-key", auth.get("location"));
            default -> throw new IllegalArgumentException("Unknown auth type " + auth.get("type"));
        };
    }

    private Map<List<String>, DittoSchemaMapping> parseAnnoyingString(String keyword, Map<String, String> map) {
        return map.entrySet()
            .stream()
            .filter(entry -> entry.getKey().contains(keyword))
            .filter(entry -> entry.getValue().contains("{{"))
            .map(entry -> parse(entry.getKey(), entry.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<List<String>, DittoSchemaMapping> parse(String key, String annoyingString) {
        var lessAnnoyingString = annoyingString.replace("{", "").replace("}", "");

        // parse default value
        var splitAroundOr = lessAnnoyingString.split("\\|");
        var defaultValue = splitAroundOr.length == 2 ? splitAroundOr[1].trim() : null;

        // required if there is no "?" or there is no default value
        var required = (lessAnnoyingString.contains("?")) || (defaultValue != null);

        var locationKeys = Arrays.stream(splitAroundOr[0].trim().split("\\.")).toList();

        return Map.entry(locationKeys, new DittoSchemaMapping(key, required, defaultValue));
    }

    // anything that isn't wrapped in {{ }} is treated as a hardcoded value
    private Map<String, String> parseHardcoded(Map<String, String> map) {
        return map.entrySet()
            .stream()
            .filter(entry -> entry.getValue().contains("{{") == false)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
