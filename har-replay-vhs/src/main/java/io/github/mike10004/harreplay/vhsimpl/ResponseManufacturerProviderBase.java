package io.github.mike10004.harreplay.vhsimpl;

import io.github.mike10004.harreplay.ReplayServerConfig;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.bmp.BmpResponseManufacturer;
import io.github.mike10004.vhs.bmp.HarReplayManufacturer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public abstract class ResponseManufacturerProviderBase<H> implements ResponseManufacturerProvider {

    private final ResponseManufacturerConfig responseManufacturerConfig;

    protected ResponseManufacturerProviderBase(ResponseManufacturerConfig responseManufacturerConfig) {
        this.responseManufacturerConfig = requireNonNull(responseManufacturerConfig);
    }

    protected abstract List<H> readHarEntries(File harFile) throws IOException;

    protected abstract EntryParser<H> getHarEntryParser();

    protected ResponseManufacturerConfig getConfig() {
        return responseManufacturerConfig;
    }

    @Override
    public BmpResponseManufacturer create(File harFile, ReplayServerConfig replayServerConfig) throws IOException {
        ResponseManufacturerConfig responseManufacturerConfig = getConfig();
        List<ResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.addAll(buildInterceptorsForReplacements(responseManufacturerConfig, replayServerConfig.replacements));
        responseInterceptors.addAll(buildInterceptorsForTransforms(responseManufacturerConfig, replayServerConfig.responseHeaderTransforms));
        List<H> entries = readHarEntries(harFile);
        EntryParser<H> parser = getHarEntryParser();
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        EntryMatcher harEntryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        EntryMatcher compositeEntryMatcher = buildEntryMatcher(responseManufacturerConfig, harEntryMatcher, replayServerConfig);
        return new HarReplayManufacturer(compositeEntryMatcher, responseInterceptors);
    }

    protected List<ResponseInterceptor> buildInterceptorsForReplacements(ResponseManufacturerConfig config, Collection<ReplayServerConfig.Replacement> replacements) {
        return replacements.stream().map(replacement ->  new ReplacingInterceptor(config, replacement)).collect(Collectors.toList());
    }

    protected List<ResponseInterceptor> buildInterceptorsForTransforms(ResponseManufacturerConfig config, Collection<ReplayServerConfig.ResponseHeaderTransform> headerTransforms) {
        return headerTransforms.stream().map(headerTransform -> new HeaderTransformInterceptor(config, headerTransform)).collect(Collectors.toList());
    }

    protected EntryMatcher buildEntryMatcher(ResponseManufacturerConfig config, EntryMatcher harEntryMatcher, ReplayServerConfig serverConfig) {
        MappingEntryMatcher mappingEntryMatcher = new MappingEntryMatcher(serverConfig.mappings, config.mappedFileResolutionRoot);
        return new CompositeEntryMatcher(Arrays.asList(mappingEntryMatcher, harEntryMatcher));
    }

}
