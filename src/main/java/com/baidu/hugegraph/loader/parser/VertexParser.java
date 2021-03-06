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
import com.baidu.hugegraph.loader.source.VertexSource;
import com.baidu.hugegraph.structure.constant.IdStrategy;
import com.baidu.hugegraph.structure.graph.Vertex;
import com.baidu.hugegraph.structure.schema.VertexLabel;
import com.baidu.hugegraph.util.E;

public class VertexParser extends ElementParser<Vertex> {

    private final VertexSource source;
    private final VertexLabel vertexLabel;

    public VertexParser(VertexSource source, InputReader reader) {
        super(reader);
        this.source = source;
        this.vertexLabel = this.getVertexLabel(source.label());
        // Ensure the id field is matched with id strategy
        this.checkIdField();
    }

    @Override
    public VertexSource source() {
        return this.source;
    }

    @Override
    protected Vertex parse(Map<String, Object> keyValues) {
        Vertex vertex = new Vertex(this.source.label());
        // Assign or check id if need
        this.assignIdIfNeed(vertex, keyValues);
        // Add properties
        this.addProperties(vertex, keyValues);
        return vertex;
    }

    @Override
    protected boolean isIdField(String fieldName) {
        return fieldName.equals(this.source.idField());
    }

    private void assignIdIfNeed(Vertex vertex, Map<String, Object> keyValues) {
        // The id strategy must be CUSTOMIZE/PRIMARY_KEY via 'checkIdField()'
        if (isCustomize(this.vertexLabel.idStrategy())) {
            assert this.source.idField() != null;
            Object idValue = keyValues.get(this.source.idField());
            E.checkArgument(idValue != null,
                            "The value of id field '%s' can't be null",
                            this.source.idField());

            String id = String.valueOf(idValue);
            if (this.vertexLabel.idStrategy() == IdStrategy.CUSTOMIZE_STRING) {
                this.checkVertexIdLength(id);
                vertex.id(id);
            }
        } else {
            assert isPrimaryKey(this.vertexLabel.idStrategy());
            List<String> primaryKeys = this.vertexLabel.primaryKeys();
            List<Object> primaryValues = new ArrayList<>(primaryKeys.size());
            for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
                String fieldName = entry.getKey();
                Object fieldValue = entry.getValue();

                String key = this.source.mappingField(fieldName);
                Object value = this.validatePropertyValue(key, fieldValue);

                if (primaryKeys.contains(key)) {
                    int index = primaryKeys.indexOf(key);
                    primaryValues.add(index, value);
                }
            }
            String id = this.spliceVertexId(this.vertexLabel, primaryValues);
            this.checkVertexIdLength(id);
        }
    }

    private void checkIdField() {
        if (isCustomize(this.vertexLabel.idStrategy())) {
            E.checkState(this.source.idField() != null,
                         "The id field can't be empty or null " +
                         "when id strategy is CUSTOMIZE");
        } else if (isPrimaryKey(this.vertexLabel.idStrategy())) {
            E.checkState(this.source.idField() == null,
                         "The id field must be empty or null " +
                         "when id strategy is PRIMARY_KEY");
        } else {
            // The id strategy is automatic
            throw new IllegalArgumentException(
                      "Unsupported AUTOMATIC id strategy for hugegraph-loader");
        }
    }
}
