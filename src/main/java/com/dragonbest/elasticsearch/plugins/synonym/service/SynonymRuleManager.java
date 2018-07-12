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

import com.dragonbest.elasticsearch.plugins.synonym.service.utils.JDBCUtils;
import com.dragonbest.elasticsearch.plugins.synonym.service.utils.Monitor;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.ESLoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author zhangguanlong
 * @date 2018/7/10
 */
public class SynonymRuleManager {

    private static final Logger LOGGER = ESLoggerFactory.getLogger(Monitor.class.getName());

    private static final int DB_CHECK_URL = 60;

    private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, r -> new Thread(r, "monitor-thread"));

    private static SynonymRuleManager singleton;

    private Configuration configuration;

    private SimpleSynonymMap synonymMap;

    public static synchronized SynonymRuleManager initial(Configuration cfg) {
        if (singleton == null) {
            synchronized (SynonymRuleManager.class) {
                if (singleton == null) {
                    singleton = new SynonymRuleManager();
                    singleton.configuration = cfg;
                    long loadedMaxVersion = singleton.loadSynonymRule();
                    executorService.scheduleWithFixedDelay(new Monitor(cfg, loadedMaxVersion), 1,
                            DB_CHECK_URL, TimeUnit.SECONDS);
                }
            }
        }

        return singleton;
    }

    public static SynonymRuleManager getSingleton() {
        if (singleton == null) {
            throw new IllegalStateException("Please initial first.");
        }
        return singleton;
    }

    public List<String> getSynonymWords(String inputToken) {
        if (this.synonymMap == null) {
            return null;
        }

        return this.synonymMap.getSynonymWords(inputToken);
    }

    private long loadSynonymRule() {
        try {
            long currentMaxVersion = JDBCUtils.queryMaxSynonymRuleVersion(configuration.getDBUrl());
            List<String> synonymRuleList = JDBCUtils.querySynonymRules(configuration.getDBUrl(), currentMaxVersion);
            this.synonymMap = new SimpleSynonymMap(this.configuration);
            for (String rule : synonymRuleList) {
                this.synonymMap.addRule(rule);
            }

            LOGGER.info("Load {} synonym rule succeed!", synonymRuleList.size());
            //
            LOGGER.info(" synonym rule :" + synonymRuleList.toString());
            return currentMaxVersion;
        } catch (Exception e) {
            LOGGER.error("Load synonym rule failed!", e);
            //throw new RuntimeException(e);
            return 0L;
        }
    }

    public boolean reloadSynonymRule(long maxVersion) {
        LOGGER.info("Start to reload synonym rule...");
        boolean reloadResult = true;
        try {
            SynonymRuleManager tmpManager = new SynonymRuleManager();
            tmpManager.configuration = getSingleton().configuration;
            List<String> synonymRuleList = JDBCUtils.querySynonymRules(configuration.getDBUrl(), maxVersion);
            SimpleSynonymMap tempSynonymMap = new SimpleSynonymMap(tmpManager.configuration);
            for (String rule : synonymRuleList) {
                tempSynonymMap.addRule(rule);
            }

            this.synonymMap = tempSynonymMap;
            LOGGER.info("Succeed to reload {} synonym rule!", synonymRuleList.size());
            LOGGER.info(" synonym rule :" + synonymRuleList.toString());
        } catch (Throwable t) {
            LOGGER.error("Failed to reload synonym rule!", t);
            reloadResult = false;
        }

        return reloadResult;
    }
}
