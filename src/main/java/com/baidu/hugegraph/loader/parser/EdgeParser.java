/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.loader.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baidu.hugegraph.loader.reader.InputReader;
import com.baidu.hugegraph.loader.source.EdgeSource;
import com.baidu.hugegraph.structure.constant.IdStrategy;
import com.baidu.hugegraph.structure.graph.Edge;
import com.baidu.hugegraph.structure.schema.EdgeLabel;
import com.baidu.hugegraph.structure.schema.VertexLabel;
import com.baidu.hugegraph.util.E;

public class EdgeParser extends ElementParser<Edge> {

    private final EdgeSource source;
    private final EdgeLabel edgeLabel;
    private final VertexLabel sourceLabel;
    private final VertexLabel targetLabel;

    public EdgeParser(EdgeSource source, InputReader reader) {
        super(reader);
        this.source = source;
        this.edgeLabel = this.getEdgeLabel(source.label());
        this.sourceLabel = this.getVertexLabel(this.edgeLabel.sourceLabel());
        this.targetLabel = this.getVertexLabel(this.edgeLabel.targetLabel());
        // Ensure that the source/target id fileds are matched with id strategy
        this.checkIdFields(this.sourceLabel, this.source.sourceFields());
        this.checkIdFields(this.targetLabel, this.source.targetFields());
    }

    @Override
    public EdgeSource source() {
        return this.source;
    }

    @Override
    protected Edge parse(Map<String, Object> keyValues) {
        Edge edge = new Edge(this.source.label());
        // Must add source/target vertex id
        edge.source(this.buildVertexId(this.sourceLabel,
                                       this.source.sourceFields(), keyValues));
        edge.target(this.buildVertexId(this.targetLabel,
                                       this.source.targetFields(), keyValues));
        // Must add source/target vertex label
        edge.sourceLabel(this.sourceLabel.name());
        edge.targetLabel(this.targetLabel.name());
        // Add properties
        this.addProperties(edge, keyValues);
        return edge;
    }

    @Override
    protected boolean isIdField(String fieldName) {
        return this.source.sourceFields().contains(fieldName) ||
               this.source.targetFields().contains(fieldName);
    }

    private Object buildVertexId(VertexLabel vertexLabel,
                                 List<String> fieldNames,
                                 Map<String, Object> keyValues) {
        List<String> primaryKeys = vertexLabel.primaryKeys();
        List<Object> primaryValues = new ArrayList<>(primaryKeys.size());
        for (String fieldName : fieldNames) {
            if (!keyValues.containsKey(fieldName)) {
                continue;
            }
            Object fieldValue = keyValues.get(fieldName);
            String key = this.source.mappingField(fieldName);
            Object value = this.validatePropertyValue(key, fieldValue);

            IdStrategy idStrategy = vertexLabel.idStrategy();
            if (isCustomize(idStrategy)) {
                /*
                 * Check vertex id length when the id strategy of
                 * source/target label is CUSTOMIZE_STRING,
                 * just return when id strategy is CUSTOMIZE_NUMBER
                 */
                if (idStrategy == IdStrategy.CUSTOMIZE_STRING) {
                    String id = String.valueOf(fieldValue);
                    this.checkVertexIdLength(id);
                    return id;
                } else {
                    assert idStrategy == IdStrategy.CUSTOMIZE_NUMBER;
                    return fieldValue;
                }
            } else {
                // The id strategy of source/target label must be PRIMARY_KEY
                if (primaryKeys.contains(key)) {
                    int index = primaryKeys.indexOf(key);
                    primaryValues.add(index, value);
                }
            }
        }

        String id = this.spliceVertexId(vertexLabel, primaryValues);
        this.checkVertexIdLength(id);
        return id;
    }

    private void checkIdFields(VertexLabel vertexLabel, List<String> fields) {
        if (isCustomize(vertexLabel.idStrategy())) {
            E.checkArgument(fields.size() == 1,
                            "The source/target field can contains only one " +
                            "column when id strategy is CUSTOMIZE");
        } else if (isPrimaryKey(vertexLabel.idStrategy())) {
            E.checkArgument(fields.size() >= 1,
                            "The source/target field must contains some " +
                            "columns when id strategy is CUSTOMIZE");
        } else {
            throw new IllegalArgumentException(
                      "Unsupported AUTOMATIC id strategy for hugegraph-loader");
        }
    }
}
