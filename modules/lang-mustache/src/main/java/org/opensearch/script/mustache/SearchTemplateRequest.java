/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.script.mustache;

import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.CompositeIndicesRequest;
import org.opensearch.action.IndicesRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.core.ParseField;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.script.ScriptType;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * A request to execute a search based on a search template.
 */
public class SearchTemplateRequest extends ActionRequest implements IndicesRequest.Replaceable, CompositeIndicesRequest, ToXContentObject {

    private SearchRequest request;
    private boolean simulate = false;
    private boolean explain = false;
    private boolean profile = false;
    private ScriptType scriptType;
    private String script;
    private Map<String, Object> scriptParams;
    private String searchPipeline;

    public SearchTemplateRequest() {}

    public SearchTemplateRequest(StreamInput in) throws IOException {
        super(in);
        request = in.readOptionalWriteable(SearchRequest::new);
        simulate = in.readBoolean();
        explain = in.readBoolean();
        profile = in.readBoolean();
        scriptType = ScriptType.readFrom(in);
        script = in.readOptionalString();
        if (in.readBoolean()) {
            scriptParams = in.readMap();
        }
        if (in.getVersion().onOrAfter(Version.V_3_2_0)) {
            searchPipeline = in.readOptionalString();
        }

    }

    public SearchTemplateRequest(SearchRequest searchRequest) {
        this.request = searchRequest;
    }

    public void setRequest(SearchRequest request) {
        this.request = request;
    }

    public SearchRequest getRequest() {
        return request;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchTemplateRequest request1 = (SearchTemplateRequest) o;
        return simulate == request1.simulate
            && explain == request1.explain
            && profile == request1.profile
            && Objects.equals(request, request1.request)
            && scriptType == request1.scriptType
            && Objects.equals(script, request1.script)
            && Objects.equals(scriptParams, request1.scriptParams)
            && Objects.equals(searchPipeline, request1.searchPipeline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, simulate, explain, profile, scriptType, script, scriptParams, searchPipeline);
    }

    public boolean isSimulate() {
        return simulate;
    }

    public void setSimulate(boolean simulate) {
        this.simulate = simulate;
    }

    public boolean isExplain() {
        return explain;
    }

    public void setExplain(boolean explain) {
        this.explain = explain;
    }

    public boolean isProfile() {
        return profile;
    }

    public void setProfile(boolean profile) {
        this.profile = profile;
    }

    public ScriptType getScriptType() {
        return scriptType;
    }

    public void setScriptType(ScriptType scriptType) {
        this.scriptType = scriptType;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public Map<String, Object> getScriptParams() {
        return scriptParams;
    }

    public void setScriptParams(Map<String, Object> scriptParams) {
        this.scriptParams = scriptParams;
    }

    public String getSearchPipeline() {
        return searchPipeline;
    }

    public void setSearchPipeline(String searchPipeline) {
        this.searchPipeline = searchPipeline;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (script == null || script.isEmpty()) {
            validationException = addValidationError("template is missing", validationException);
        }
        if (scriptType == null) {
            validationException = addValidationError("template's script type is missing", validationException);
        }
        if (simulate == false) {
            if (request == null) {
                validationException = addValidationError("search request is missing", validationException);
            } else {
                ActionRequestValidationException ex = request.validate();
                if (ex != null) {
                    if (validationException == null) {
                        validationException = new ActionRequestValidationException();
                    }
                    validationException.addValidationErrors(ex.validationErrors());
                }
            }
        }
        return validationException;
    }

    private static ParseField ID_FIELD = new ParseField("id");
    private static ParseField SOURCE_FIELD = new ParseField("source", "inline", "template");

    private static ParseField PARAMS_FIELD = new ParseField("params");
    private static ParseField EXPLAIN_FIELD = new ParseField("explain");
    private static ParseField PROFILE_FIELD = new ParseField("profile");
    private static ParseField SEARCH_PIPELINE_FIELD = new ParseField("search_pipeline");

    private static final ObjectParser<SearchTemplateRequest, Void> PARSER;
    static {
        PARSER = new ObjectParser<>("search_template");
        PARSER.declareField((parser, request, s) -> request.setScriptParams(parser.map()), PARAMS_FIELD, ObjectParser.ValueType.OBJECT);
        PARSER.declareString((request, s) -> {
            request.setScriptType(ScriptType.STORED);
            request.setScript(s);
        }, ID_FIELD);
        PARSER.declareBoolean(SearchTemplateRequest::setExplain, EXPLAIN_FIELD);
        PARSER.declareBoolean(SearchTemplateRequest::setProfile, PROFILE_FIELD);
        PARSER.declareField((parser, request, value) -> {
            request.setScriptType(ScriptType.INLINE);
            if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
                // convert the template to json which is the only supported XContentType (see CustomMustacheFactory#createEncoder)
                try (XContentBuilder builder = MediaTypeRegistry.JSON.contentBuilder()) {
                    request.setScript(builder.copyCurrentStructure(parser).toString());
                } catch (IOException e) {
                    throw new ParsingException(parser.getTokenLocation(), "Could not parse inline template", e);
                }
            } else {
                request.setScript(parser.text());
            }
        }, SOURCE_FIELD, ObjectParser.ValueType.OBJECT_OR_STRING);
        PARSER.declareField((parser, request, context) -> {
            if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                request.setSearchPipeline(null);
            } else {
                request.setSearchPipeline(parser.text());
            }
        }, SEARCH_PIPELINE_FIELD, ObjectParser.ValueType.STRING_OR_NULL);

    }

    public static SearchTemplateRequest fromXContent(XContentParser parser) throws IOException {
        return PARSER.parse(parser, new SearchTemplateRequest(), null);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        if (scriptType == ScriptType.STORED) {
            builder.field(ID_FIELD.getPreferredName(), script);
        } else if (scriptType == ScriptType.INLINE) {
            builder.field(SOURCE_FIELD.getPreferredName(), script);
        } else {
            throw new UnsupportedOperationException("Unrecognized script type [" + scriptType + "].");
        }

        return builder.field(PARAMS_FIELD.getPreferredName(), scriptParams)
            .field(EXPLAIN_FIELD.getPreferredName(), explain)
            .field(PROFILE_FIELD.getPreferredName(), profile)
            .field(SEARCH_PIPELINE_FIELD.getPreferredName(), searchPipeline)
            .endObject();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalWriteable(request);
        out.writeBoolean(simulate);
        out.writeBoolean(explain);
        out.writeBoolean(profile);
        scriptType.writeTo(out);
        out.writeOptionalString(script);
        boolean hasParams = scriptParams != null;
        out.writeBoolean(hasParams);
        if (hasParams) {
            out.writeMap(scriptParams);
        }
        if (out.getVersion().onOrAfter(Version.V_3_2_0)) {
            out.writeOptionalString(searchPipeline);
        }
    }

    @Override
    public String[] indices() {
        if (request == null) {
            return new String[0];
        }
        return request.indices();
    }

    @Override
    public IndicesOptions indicesOptions() {
        if (request == null) {
            return SearchRequest.DEFAULT_INDICES_OPTIONS;
        }
        return request.indicesOptions();
    }

    @Override
    public IndicesRequest indices(String... indices) {
        if (request == null) {
            return new SearchRequest(new String[0]).indices(indices);
        }
        return request.indices(indices);
    }
}
