/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dragonbest.elasticsearch.plugins.synonym.service;

import org.apache.lucene.analysis.Analyzer;

/**
 * Created by zhangguanlong on 2018/7/10.
 */
public class Configuration {

    private final boolean ignoreCase;

    private final boolean expand;

    private final String dbUrl;

    private final Analyzer analyzer;

    public Configuration(boolean ignoreCase, boolean expand, Analyzer analyzer, String dbUrl) {
        this.ignoreCase = ignoreCase;
        this.expand = expand;
        this.analyzer = analyzer;
        this.dbUrl = dbUrl;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public boolean isExpand() {
        return expand;
    }

    public String getDBUrl() {
        return dbUrl;
    }
}
