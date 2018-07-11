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
package com.dragonbest.elasticsearch.plugins.synonym;

import com.dragonbest.elasticsearch.plugins.synonym.service.Configuration;
import com.dragonbest.elasticsearch.plugins.synonym.service.DynamicSynonymTokenFilter;
import com.dragonbest.elasticsearch.plugins.synonym.service.SynonymRuleManager;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.synonym.WordnetSynonymParser;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.analysis.SynonymTokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;

import java.io.IOException;
import java.io.Reader;

public class DynamicSynonymTokenFilterFactory extends AbstractTokenFilterFactory {

    public DynamicSynonymTokenFilterFactory(IndexSettings indexSettings, Environment env,
                                            String name, Settings settings) throws IOException {
        super(indexSettings, name, settings);

        // get the filter setting params
        final boolean ignoreCase = settings.getAsBoolean("ignore_case", false);
        final boolean expand = settings.getAsBoolean("expand", true);
        final String dbUrl = settings.get("db_url");
        final String tokenizerName = settings.get("tokenizer", "whitespace");
        logger.debug(" dbUrl ="+dbUrl);
        Analyzer analyzer;
        if ("standand".equalsIgnoreCase(tokenizerName)) {
            analyzer = new StandardAnalyzer();
        } else if ("keyword".equalsIgnoreCase(tokenizerName)) {
            analyzer = new KeywordAnalyzer();
        } else if ("simple".equalsIgnoreCase(tokenizerName)) {
            analyzer = new SimpleAnalyzer();
        } else {
            // 默认使用 WhitespaceAnalyzer  TODO 可以在此扩展动态获取 analyzer 比如ik 的by_smart 源码本人看不懂 后来的大牛加油
            analyzer = new WhitespaceAnalyzer();
        }
        //  原实现为 org.elasticsearch.index.analysis.SynonymTokenFilterFactory
        //  此处参考 https://github.com/ginobefun/elasticsearch-dynamic-synonym
        // AnalysisProvider<TokenizerFactory> tokenizerFactoryFactory

      /*  AnalysisModule.AnalysisProvider<TokenizerFactory> tokenizerFactoryFactory = analysisRegistry.getTokenizerProvider(tokenizerName, indexSettings);
        if (tokenizerFactoryFactory == null) {
            throw new IllegalArgumentException("failed to find tokenizer [" + tokenizerName + "] for synonym token filter");
        } else {
            final TokenizerFactory tokenizerFactory = (TokenizerFactory)tokenizerFactoryFactory.get(indexSettings, env, tokenizerName, AnalysisRegistry.getSettingsFromIndexSettings(indexSettings, "index.analysis.tokenizer." + tokenizerName));
            Analyzer analyzer = new Analyzer() {
                protected TokenStreamComponents createComponents(String fieldName) {
                    Tokenizer tokenizer = tokenizerFactory == null ? new WhitespaceTokenizer() : tokenizerFactory.create();
                    TokenStream stream = SynonymTokenFilterFactory.this.ignoreCase ? new LowerCaseFilter((TokenStream)tokenizer) : tokenizer;
                    return new TokenStreamComponents((Tokenizer)tokenizer, (TokenStream)stream);
                }
            };

            try {
                SynonymMap.Builder parser = null;
                if ("wordnet".equalsIgnoreCase(settings.get("format"))) {
                    parser = new WordnetSynonymParser(true, expand, analyzer);
                    ((WordnetSynonymParser)parser).parse((Reader)rulesReader);
                } else {
                    parser = new SolrSynonymParser(true, expand, analyzer);
                    ((SolrSynonymParser)parser).parse((Reader)rulesReader);
                }

                this.synonymMap = ((SynonymMap.Builder)parser).build();
            } catch (Exception var13) {
                throw new IllegalArgumentException("failed to build synonyms", var13);
            }
        }*/

        // NOTE: the manager will only init once
        SynonymRuleManager.initial(new Configuration(ignoreCase, expand, analyzer, dbUrl));
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new DynamicSynonymTokenFilter(tokenStream);
    }
}
