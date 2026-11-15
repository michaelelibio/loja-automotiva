package com.garage.garageapi.shared.json;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadConstraints;

/** Bounds work performed before Bean Validation can inspect an HTTP JSON request. */
@Configuration(proxyBeanMethods = false)
public class JsonInputSafetyConfiguration {
    static final int MAX_DEPTH = 64;
    static final int MAX_NUMBER_LENGTH = 128;
    static final int MAX_STRING_LENGTH = 1_000_000;
    static final long MAX_DOCUMENT_LENGTH = 2_000_000;

    @Bean
    JsonFactoryBuilderCustomizer boundedJsonInput() {
        return builder -> builder.streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(MAX_DEPTH)
                .maxNumberLength(MAX_NUMBER_LENGTH)
                .maxStringLength(MAX_STRING_LENGTH)
                .maxNameLength(256)
                .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                .maxTokenCount(100_000)
                .build());
    }
}
