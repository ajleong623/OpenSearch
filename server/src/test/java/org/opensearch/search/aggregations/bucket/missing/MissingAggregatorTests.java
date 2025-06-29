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

package org.opensearch.search.aggregations.bucket.missing;

import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.util.BytesRef;
import org.opensearch.common.CheckedConsumer;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.mapper.NumberFieldMapper;
import org.opensearch.index.mapper.NumberFieldMapper.NumberType;
import org.opensearch.index.mapper.RangeFieldMapper;
import org.opensearch.index.mapper.RangeType;
import org.opensearch.script.MockScriptEngine;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptEngine;
import org.opensearch.script.ScriptModule;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregatorTestCase;
import org.opensearch.search.aggregations.support.AggregationInspectionHelper;
import org.opensearch.search.aggregations.support.CoreValuesSourceType;
import org.opensearch.search.aggregations.support.ValuesSourceType;
import org.opensearch.search.lookup.LeafDocLookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.opensearch.common.lucene.search.Queries.newMatchAllQuery;

public class MissingAggregatorTests extends AggregatorTestCase {

    private static final String VALUE_SCRIPT_PARAMS = "value_script_params";
    private static final String VALUE_SCRIPT = "value_script";
    private static final String FIELD_SCRIPT_PARAMS = "field_script_params";
    private static final String FIELD_SCRIPT = "field_script";
    private static final long DEFAULT_INC_PARAM = 1;
    private static final long DEFAULT_THRESHOLD_PARAM = 50;

    public void testMatchNoDocs() throws IOException {
        final int numDocs = randomIntBetween(10, 200);

        final MappedFieldType fieldType = new NumberFieldMapper.NumberFieldType("field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field(fieldType.name());
        final boolean isIndexed = randomBoolean();

        CheckedConsumer<RandomIndexWriter, IOException> writeIndex = (writer -> {
            for (int i = 0; i < numDocs; i++) {
                if (isIndexed) {
                    final long randomLong = randomLong();
                    writer.addDocument(
                        Set.of(
                            new SortedNumericDocValuesField(fieldType.name(), randomLong),
                            new StringField(fieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                } else {
                    writer.addDocument(singleton(new SortedNumericDocValuesField(fieldType.name(), randomLong())));
                }
            }
        });

        if (isIndexed) {
            // The precompute optimization kicked in, so no docs were traversed.
            testCase(newMatchAllQuery(), builder, writeIndex, internalMissing -> {
                assertEquals(0, internalMissing.getDocCount());
                assertFalse(AggregationInspectionHelper.hasValue(internalMissing));
            }, singleton(fieldType), 0);
        } else {
            testCase(newMatchAllQuery(), builder, writeIndex, internalMissing -> {
                assertEquals(0, internalMissing.getDocCount());
                assertFalse(AggregationInspectionHelper.hasValue(internalMissing));
            }, singleton(fieldType), numDocs);
        }
    }

    public void testMatchAllDocs() throws IOException {
        int numDocs = randomIntBetween(10, 200);

        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);
        final MappedFieldType anotherFieldType = new NumberFieldMapper.NumberFieldType("another_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field(aggFieldType.name());

        final boolean isIndexed = false;

        CheckedConsumer<RandomIndexWriter, IOException> writeIndex = (writer -> {
            for (int i = 0; i < numDocs; i++) {
                if (isIndexed) {
                    final long randomLong = randomLong();
                    writer.addDocument(
                        Set.of(
                            new SortedNumericDocValuesField(anotherFieldType.name(), randomLong),
                            new StringField(anotherFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                } else {
                    writer.addDocument(singleton(new SortedNumericDocValuesField(anotherFieldType.name(), randomLong())));
                }
            }
        });

        if (isIndexed) {
            // The precompute optimization kicked in, so no docs were traversed.
            testCase(newMatchAllQuery(), builder, writeIndex, internalMissing -> {
                assertEquals(numDocs, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), 0);
        } else {
            // We can use precomputation because we are counting a field that has never been added.
            testCase(newMatchAllQuery(), builder, writeIndex, internalMissing -> {
                assertEquals(numDocs, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), 0);
        }
    }

    public void testMatchSparse() throws IOException {
        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);
        final MappedFieldType anotherFieldType = new NumberFieldMapper.NumberFieldType("another_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field(aggFieldType.name());

        final int numDocs = randomIntBetween(100, 200);
        int docsMissingAggField = 0;
        final List<Set<IndexableField>> docs = new ArrayList<>();
        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();

        for (int i = 0; i < numDocs; i++) {
            if (isIndexed) {
                if (randomBoolean()) {
                    final long randomLong = randomLong();
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong),
                            new StringField(aggFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                } else {
                    final long randomLong = randomLong();
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(anotherFieldType.name(), randomLong),
                            new StringField(anotherFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                    docsMissingAggField++;
                }
            } else {
                if (randomBoolean()) {
                    docs.add(singleton(new SortedNumericDocValuesField(aggFieldType.name(), randomLong())));
                } else {
                    docs.add(singleton(new SortedNumericDocValuesField(anotherFieldType.name(), randomLong())));
                    docsMissingAggField++;
                }
            }
        }
        final int finalDocsMissingAggField = docsMissingAggField;

        if (isIndexed) {
            // The precompute optimization kicked in, so no docs were traversed.
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingAggField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), 0);
        } else {
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingAggField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), numDocs);
        }
    }

    public void testMatchSparseRangeField() throws IOException {
        final RangeType rangeType = RangeType.DOUBLE;
        final MappedFieldType aggFieldType = new RangeFieldMapper.RangeFieldType("agg_field", rangeType);
        final MappedFieldType anotherFieldType = new RangeFieldMapper.RangeFieldType("another_field", rangeType);

        final RangeFieldMapper.Range range = new RangeFieldMapper.Range(rangeType, 1.0D, 5.0D, true, true);
        final BytesRef encodedRange = rangeType.encodeRanges(singleton(range));
        final BinaryDocValuesField encodedRangeField = new BinaryDocValuesField(aggFieldType.name(), encodedRange);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field(aggFieldType.name());

        final int numDocs = randomIntBetween(100, 200);
        int docsMissingAggField = 0;
        final List<Set<IndexableField>> docs = new ArrayList<>();

        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();

        for (int i = 0; i < numDocs; i++) {
            if (isIndexed) {
                if (randomBoolean()) {
                    docs.add(singleton(encodedRangeField));
                } else {
                    final long randomLong = randomLong();
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(anotherFieldType.name(), randomLong),
                            new StringField(anotherFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                    docsMissingAggField++;
                }
            } else {
                if (randomBoolean()) {
                    docs.add(singleton(encodedRangeField));
                } else {
                    docs.add(singleton(new SortedNumericDocValuesField(anotherFieldType.name(), randomLong())));
                    docsMissingAggField++;
                }
            }
        }
        final int finalDocsMissingAggField = docsMissingAggField;

        if (isIndexed) {
            // The precompute does not work because only the other field was actually indexed. Therefore, the
            // precomputation could not declare whether the field was simply not indexed or if there were
            // actually no values in that field.
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingAggField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, Arrays.asList(aggFieldType, anotherFieldType), numDocs);
        } else {
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingAggField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, Arrays.asList(aggFieldType, anotherFieldType), numDocs);
        }
    }

    public void testUnmappedWithoutMissingParam() throws IOException {
        final int numDocs = randomIntBetween(10, 20);
        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field("unknown_field");

        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();

        CheckedConsumer<RandomIndexWriter, IOException> writeIndex = (writer -> {
            for (int i = 0; i < numDocs; i++) {
                if (isIndexed) {
                    final long randomLong = randomLong();
                    writer.addDocument(
                        Set.of(
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong),
                            new StringField(aggFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                } else {
                    writer.addDocument(singleton(new SortedNumericDocValuesField(aggFieldType.name(), randomLong())));
                }
            }
        });

        if (isIndexed) {
            // Unfortunately, the values source is not provided, therefore, we cannot use the precomputation.
            testCase(newMatchAllQuery(), builder, writeIndex, internalMissing -> {
                assertEquals(numDocs, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, singleton(aggFieldType), numDocs);
        } else {
            testCase(newMatchAllQuery(), builder, writeIndex, internalMissing -> {
                assertEquals(numDocs, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, singleton(aggFieldType), numDocs);
        }
    }

    public void testUnmappedWithMissingParam() throws IOException {
        final int numDocs = randomIntBetween(10, 20);
        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field("unknown_field").missing(randomLong());

        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();

        // Having the missing parameter will make the missing aggregator not responsible for any documents, so it will short circuit
        testCase(newMatchAllQuery(), builder, writer -> {
            for (int i = 0; i < numDocs; i++) {
                if (isIndexed) {
                    final long randomLong = randomLong();
                    writer.addDocument(
                        Set.of(
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong),
                            new StringField(aggFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                } else {
                    writer.addDocument(singleton(new SortedNumericDocValuesField(aggFieldType.name(), randomLong())));
                }
            }
        }, internalMissing -> {
            assertEquals(0, internalMissing.getDocCount());
            assertFalse(AggregationInspectionHelper.hasValue(internalMissing));
        }, singleton(aggFieldType), 0);
    }

    public void testMissingParam() throws IOException {
        final int numDocs = randomIntBetween(10, 20);

        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);
        final MappedFieldType anotherFieldType = new NumberFieldMapper.NumberFieldType("another_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field(aggFieldType.name()).missing(randomLong());

        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();

        // Having the missing parameter will make the missing aggregator not responsible for any documents, so it will short-circuit
        testCase(newMatchAllQuery(), builder, writer -> {
            for (int i = 0; i < numDocs; i++) {
                if (isIndexed) {
                    final long randomLong = randomLong();
                    writer.addDocument(
                        Set.of(
                            new SortedNumericDocValuesField(anotherFieldType.name(), randomLong),
                            new StringField(anotherFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                } else {
                    writer.addDocument(singleton(new SortedNumericDocValuesField(anotherFieldType.name(), randomLong())));
                }
            }
        }, internalMissing -> {
            assertEquals(0, internalMissing.getDocCount());
            assertFalse(AggregationInspectionHelper.hasValue(internalMissing));
        }, List.of(aggFieldType, anotherFieldType), 0);
    }

    public void testMultiValuedField() throws IOException {
        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);
        final MappedFieldType anotherFieldType = new NumberFieldMapper.NumberFieldType("another_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field(aggFieldType.name());

        final int numDocs = randomIntBetween(100, 200);
        int docsMissingAggField = 0;
        final List<Set<IndexableField>> docs = new ArrayList<>();

        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();

        for (int i = 0; i < numDocs; i++) {
            if (isIndexed) {
                if (randomBoolean()) {
                    final long randomLong = randomLong();
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong),
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong + 1),
                            new StringField(aggFieldType.name(), String.valueOf(randomLong), Store.NO),
                            new StringField(aggFieldType.name(), String.valueOf(randomLong + 1), Store.NO)

                        )
                    );
                } else {
                    final long randomLong = randomLong();
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(anotherFieldType.name(), randomLong),
                            new StringField(anotherFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                    docsMissingAggField++;
                }
            } else {
                if (randomBoolean()) {
                    final long randomLong = randomLong();
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong),
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong + 1)
                        )
                    );
                } else {
                    docs.add(singleton(new SortedNumericDocValuesField(anotherFieldType.name(), randomLong())));
                    docsMissingAggField++;
                }
            }
        }
        final int finalDocsMissingAggField = docsMissingAggField;

        if (isIndexed) {
            // The precompute optimization kicked in, so no docs were traversed.
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingAggField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), 0);
        } else {
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingAggField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), numDocs);
        }
    }

    public void testSingleValuedFieldWithValueScript() throws IOException {
        valueScriptTestCase(new Script(ScriptType.INLINE, MockScriptEngine.NAME, VALUE_SCRIPT, emptyMap()));
    }

    public void testSingleValuedFieldWithValueScriptWithParams() throws IOException {
        valueScriptTestCase(new Script(ScriptType.INLINE, MockScriptEngine.NAME, VALUE_SCRIPT_PARAMS, singletonMap("inc", 10)));
    }

    private void valueScriptTestCase(Script script) throws IOException {
        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);
        final MappedFieldType anotherFieldType = new NumberFieldMapper.NumberFieldType("another_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").field(aggFieldType.name()).script(script);

        final int numDocs = randomIntBetween(100, 200);
        int docsMissingAggField = 0;
        final List<Set<IndexableField>> docs = new ArrayList<>();

        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();

        for (int i = 0; i < numDocs; i++) {
            if (isIndexed) {
                final long randomLong = randomLong();
                if (randomBoolean()) {
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(aggFieldType.name(), randomLong),
                            new StringField(aggFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                } else {
                    docs.add(
                        Set.of(
                            new SortedNumericDocValuesField(anotherFieldType.name(), randomLong),
                            new StringField(anotherFieldType.name(), String.valueOf(randomLong), Store.NO)
                        )
                    );
                    docsMissingAggField++;
                }
            } else {
                if (randomBoolean()) {
                    docs.add(singleton(new SortedNumericDocValuesField(aggFieldType.name(), randomLong())));
                } else {
                    docs.add(singleton(new SortedNumericDocValuesField(anotherFieldType.name(), randomLong())));
                    docsMissingAggField++;
                }
            }
        }
        final int finalDocsMissingField = docsMissingAggField;

        if (isIndexed) {
            // The precompute optimization kicked in, so no docs were traversed.
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), 0);
        } else {
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsMissingField, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, List.of(aggFieldType, anotherFieldType), numDocs);
        }
    }

    public void testMultiValuedFieldWithFieldScriptWithParams() throws IOException {
        final long threshold = 10;
        final Map<String, Object> params = Map.of("field", "agg_field", "threshold", threshold);
        fieldScriptTestCase(new Script(ScriptType.INLINE, MockScriptEngine.NAME, FIELD_SCRIPT_PARAMS, params), threshold);
    }

    public void testMultiValuedFieldWithFieldScript() throws IOException {
        fieldScriptTestCase(
            new Script(ScriptType.INLINE, MockScriptEngine.NAME, FIELD_SCRIPT, singletonMap("field", "agg_field")),
            DEFAULT_THRESHOLD_PARAM
        );
    }

    private void fieldScriptTestCase(Script script, long threshold) throws IOException {
        final MappedFieldType aggFieldType = new NumberFieldMapper.NumberFieldType("agg_field", NumberType.LONG);

        final MissingAggregationBuilder builder = new MissingAggregationBuilder("_name").script(script);

        final int numDocs = randomIntBetween(100, 200);
        int docsBelowThreshold = 0;
        final List<Set<IndexableField>> docs = new ArrayList<>();

        // Determines whether the fields we add to the documents are indexed.
        final boolean isIndexed = randomBoolean();
        for (int i = 0; i < numDocs; i++) {
            if (isIndexed) {
                final long firstValue = randomLongBetween(0, 100);
                final long secondValue = firstValue + 1;
                if (firstValue < threshold && secondValue < threshold) {
                    docsBelowThreshold++;
                }
                docs.add(
                    Set.of(
                        new SortedNumericDocValuesField(aggFieldType.name(), firstValue),
                        new StringField(aggFieldType.name(), String.valueOf(firstValue), Store.NO),
                        new SortedNumericDocValuesField(aggFieldType.name(), secondValue),
                        new StringField(aggFieldType.name(), String.valueOf(secondValue), Store.NO)
                    )
                );
            } else {
                final long firstValue = randomLongBetween(0, 100);
                final long secondValue = firstValue + 1;
                if (firstValue < threshold && secondValue < threshold) {
                    docsBelowThreshold++;
                }
                docs.add(
                    Set.of(
                        new SortedNumericDocValuesField(aggFieldType.name(), firstValue),
                        new SortedNumericDocValuesField(aggFieldType.name(), secondValue)
                    )
                );
            }
        }
        final int finalDocsBelowThreshold = docsBelowThreshold;

        if (isIndexed) {
            // The precompute optimization did not kick in because the values source did not have an indexed name.
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsBelowThreshold, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, singleton(aggFieldType), numDocs);
        } else {
            testCase(newMatchAllQuery(), builder, writer -> writer.addDocuments(docs), internalMissing -> {
                assertEquals(finalDocsBelowThreshold, internalMissing.getDocCount());
                assertTrue(AggregationInspectionHelper.hasValue(internalMissing));
            }, singleton(aggFieldType), numDocs);
        }
    }

    private void testCase(
        Query query,
        MissingAggregationBuilder builder,
        CheckedConsumer<RandomIndexWriter, IOException> writeIndex,
        Consumer<InternalMissing> verify,
        Collection<MappedFieldType> fieldTypes,
        int expectedCount
    ) throws IOException {
        try (Directory directory = newDirectory()) {
            try (RandomIndexWriter indexWriter = new RandomIndexWriter(random(), directory)) {
                writeIndex.accept(indexWriter);
            }

            try (IndexReader indexReader = DirectoryReader.open(directory)) {
                final IndexSearcher indexSearcher = newSearcher(indexReader, true, true);
                final MappedFieldType[] fieldTypesArray = fieldTypes.toArray(new MappedFieldType[0]);
                // When counting the number of collects, we want to record how many collects actually happened. The new composite type
                // ends up keeping track of the number of counts that happened, allowing us to verify whether the precomputation was used
                // or not.
                final InternalMissing missing = searchAndReduceCounting(expectedCount, indexSearcher, query, builder, fieldTypesArray);
                verify.accept(missing);
            }
        }
    }

    @Override
    protected AggregationBuilder createAggBuilderForTypeTest(MappedFieldType fieldType, String fieldName) {
        return new MissingAggregationBuilder("_name").field(fieldName);
    }

    @Override
    protected List<ValuesSourceType> getSupportedValuesSourceTypes() {
        return List.of(
            CoreValuesSourceType.NUMERIC,
            CoreValuesSourceType.BYTES,
            CoreValuesSourceType.GEOPOINT,
            CoreValuesSourceType.RANGE,
            CoreValuesSourceType.IP,
            CoreValuesSourceType.BOOLEAN,
            CoreValuesSourceType.DATE
        );
    }

    @Override
    protected ScriptService getMockScriptService() {
        final Map<String, Function<Map<String, Object>, Object>> deterministicScripts = new HashMap<>();
        deterministicScripts.put(VALUE_SCRIPT_PARAMS, vars -> {
            final double value = ((Number) vars.get("_value")).doubleValue();
            final long inc = ((Number) vars.get("inc")).longValue();
            return value + inc;
        });
        deterministicScripts.put(VALUE_SCRIPT, vars -> {
            final double value = ((Number) vars.get("_value")).doubleValue();
            return value + DEFAULT_INC_PARAM;
        });
        deterministicScripts.put(FIELD_SCRIPT_PARAMS, vars -> {
            final String fieldName = (String) vars.get("field");
            final long threshold = ((Number) vars.get("threshold")).longValue();
            return threshold(fieldName, threshold, vars);
        });
        deterministicScripts.put(FIELD_SCRIPT, vars -> {
            final String fieldName = (String) vars.get("field");
            return threshold(fieldName, DEFAULT_THRESHOLD_PARAM, vars);
        });
        final MockScriptEngine scriptEngine = new MockScriptEngine(MockScriptEngine.NAME, deterministicScripts, emptyMap(), emptyMap());
        final Map<String, ScriptEngine> engines = singletonMap(scriptEngine.getType(), scriptEngine);
        return new ScriptService(Settings.EMPTY, engines, ScriptModule.CORE_CONTEXTS);
    }

    private static List<Long> threshold(String fieldName, long threshold, Map<String, Object> vars) {
        final LeafDocLookup lookup = (LeafDocLookup) vars.get("doc");
        return lookup.get(fieldName)
            .stream()
            .map(value -> ((Number) value).longValue())
            .filter(value -> value >= threshold)
            .collect(toList());
    }
}
