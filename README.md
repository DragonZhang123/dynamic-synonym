# elasticsearch-dynamic-synonym
An Elasticsearch token filter sopport load dynamic synonym.

Elasticsearch自带了一个synonym同义词插件，但是该插件只能使用文件或在分析器中静态地配置同义词，如果需要添加或修改，需要重启。
通过对开源代码的研究，改写了一个可以动态更新并用于5.5.9版本的插件，大部分参考了两个插件的代码。

# Elasticsearch自带的SynonymTokenFilter
Elasticsearch自带的同义词过滤器支持在分析器配置（使用synonyms参数）和文件中配置（使用synonyms_path参数）同义词，配置方式如下：

    {
        "index" : {
            "analysis" : {
                "analyzer" : {
                    "synonym_analyzer" : {
                        "tokenizer" : "whitespace",
                        "filter" : ["my_synonym"]
                    }
                },
                "filter" : {
                    "my_synonym" : {
                        "type" : "synonym",
                        "expand": true,
                        "ignore_case": true, 
                        "synonyms_path" : "analysis/synonym.txt"
                        "synonyms" : ["阿迪, 阿迪达斯, adidasi => Adidas","Nike, 耐克, naike"]
                    }
                }
            }
        }
    }

在配置同义词规则时有[Solr synonyms](https://www.elastic.co/guide/en/elasticsearch/reference/2.3/analysis-synonym-tokenfilter.html#_solr_synonyms)和[WordNet synonyms](https://www.elastic.co/guide/en/elasticsearch/reference/2.3/analysis-synonym-tokenfilter.html#_wordnet_synonyms)，一般我们使用的都是Solr synonyms。在配置时又存在映射和对等两种方式，区别如下：



    // 精确映射同义词，【阿迪】、【阿迪达斯】和【adidasi】的token将会转换为【Adidas】存入倒排索引中
    阿迪, 阿迪达斯, adidasi => Adidas
    
    // 对等同义词
    // 当expand为true时，当出现以下任何一个token，三个token都会存入倒排索引中
    // 当expand为false时，当出现以下任何一个token，第一个token也就是【Nike】会存入倒排索引中
    Nike, 耐克, naike

# DynamicSynonymTokenFilter
- DynamicSynonymTokenFilter参考了SynonymTokenFilter的方式，但又予以简化，使用一个HashMap来保存同义词之间的转换关系；
- DynamicSynonymTokenFilter只支持Solr synonyms，同时也支持expand和ignore_case参数的配置；
- DynamicSynonymTokenFilter通过数据库来管理同义词的配置，并轮询数据库（通过version字段判断是否存在规则变化）实现同义词的动态管理；

## 安装
1.下载插件源码

    git@github.com:DragonZhang123/dynamic-synonym.git

2.使用maven编译插件

    mvn clean install -DskipTests
   
3.在ES_HOME/plugin目录新建dynamic-synonym目录，并将target/releases/elasticsearch-dynamic-synonym-5.6.9.zip文件解压到该目录

4.在MySQL中创建Elasticsearch同义词数据库并创建用户（懒得写了，把大佬demo的贴过来）

    create database elasticsearch;
    DROP TABLE IF EXISTS `dynamic_synonym_rule`;
    CREATE TABLE `dynamic_synonym_rule` (
      `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
      `rule` varchar(255) NOT NULL,
      `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1: available, 0:unavailable',
      `version` int(11) NOT NULL,
      PRIMARY KEY (`id`),
      KEY `IDX_DYNAMIC_SYNONYM_VERSION` (`version`),
      KEY `IDX_DYNAMIC_SYNONYM_RULE` (`rule`)
    ) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;
    
    -- ----------------------------
    -- insert sample records
    -- ----------------------------
    INSERT INTO `dynamic_synonym_rule` VALUES ('1', '阿迪, 阿迪达斯, adidasi => Adidas', '1', '1');
    INSERT INTO `dynamic_synonym_rule` VALUES ('2', 'Nike, 耐克, naike', '1', '2');


5.重启Elasticsearch

## 配置 
（还是用的大佬的demo，大佬的地址见参考）

Elasticsearch创建索引时配置分析器和过滤器：

    PUT /index_synonym
    {
          "settings": {
            "analysis": {
              "analyzer": {
                "analyzer_with_dynamic_synonym": {
                  "type": "custom",
                  "tokenizer": "whitespace",
                  "filter": ["my_synonym"]
                }
              },
              "filter": {
                "my_synonym": {
                  "type": "dynamic-synonym",
                  "expand": true,
                  "ignore_case": true,
                  "tokenizer": "whitespace",
                  "db_url": "jdbc:mysql://localhost:3306/elasticsearch?user=es_user&password=es_pwd&useUnicode=true&characterEncoding=UTF8"
                }
              }
            }
          }
    }

设置Mapping

    POST /index_synonym/product/_mapping
    {
        "product": {
            "properties": {
                "productName": {
                    "type": "text",
                    "analyzer": "analyzer_with_dynamic_synonym"
                }
            }
        }
    }


## 使用
索引一些测试数据

    POST /index_synonym/product/1
    {"productName":"This is a nike shoes"}
    
    POST /index_synonym/product/2
    {"productName":"This is a nike sports jacket"}
    
    POST /index_synonym/product/3
    {"productName":"This is a adidas shoes"}
    
    POST /index_synonym/product/4
    {"productName":"This is a adidas sports jacket"}
    
    POST /index_synonym/product/5
    {"productName":"This is a vans shoes"}
    
    POST /index_synonym/product/6
    {"productName":"This is a vans sports jacket"}


测试分析器效果【耐克】

    POST index_synonym/_search
    {
      "query": {
        "match": {
          "productName": "耐克"
        }
      }
    }

    {
      "took": 7,
      "timed_out": false,
      "_shards": {
        "total": 5,
        "successful": 5,
        "failed": 0
      },
      "hits": {
        "total": 2,
        "max_score": 2.4740286,
        "hits": [
          {
            "_index": "index_synonym",
            "_type": "product",
            "_id": "2",
            "_score": 2.4740286,
            "_source": {
              "productName": "This is a nike sports jacket"
            }
          },
          {
            "_index": "index_synonym",
            "_type": "product",
            "_id": "1",
            "_score": 0.85747814,
            "_source": {
              "productName": "This is a nike shoes"
            }
          }
        ]
      }
    }

往数据库中插入一条同义词，测试【范斯】

    INSERT INTO `dynamic_synonym_rule` VALUES ('3', 'Vans, 范斯', '1', '3');

    // wait for 2 minutes to reload 
    [2017-03-15 15:52:28,895][INFO ][node                     ] [node-local] started
    [2017-03-15 15:55:29,645][INFO ][dynamic-synonym          ] Start to reload synonym rule...
    [2017-03-15 15:55:29,661][INFO ][dynamic-synonym          ] Succeed to reload 3 synonym rule!

    POST index_synonym/_search
    {
      "query": {
        "match": {
          "productName": "范斯"
        }
      }
    }
    
    {
      "took": 4,
      "timed_out": false,
      "_shards": {
        "total": 5,
        "successful": 5,
        "failed": 0
      },
      "hits": {
        "total": 2,
        "max_score": 1.9490025,
        "hits": [
          {
            "_index": "index_synonym",
            "_type": "product",
            "_id": "6",
            "_score": 1.9490025,
            "_source": {
              "productName": "This is a vans sports jacket"
            }
          },
          {
            "_index": "index_synonym",
            "_type": "product",
            "_id": "5",
            "_score": 0.53484553,
            "_source": {
              "productName": "This is a vans shoes"
            }
          }
        ]
      }
    }

# Conclusion
- 通过学习Elasticsearch源码自己实现(抄)了一个同义词插件，通过同义词的配置可以实现同义词规则的增删改的动态更新；
- 同义词的动态更新时原本在索引中已存在的数据不受同义词更新动态的影响，因此在使用时需要考虑该问题。
- 在修改大佬的代码时，部分代码因时间问题没全改，token analyzer 只支持几种，在代码中写死了，需要后续修改

# 参考资料
- [Using Synonyms](https://www.elastic.co/guide/en/elasticsearch/guide/current/using-synonyms.html)
- [Synonym Token Filter](https://www.elastic.co/guide/en/elasticsearch/reference/2.3/analysis-synonym-tokenfilter.html)
- [比这个改（抄）的](https://github.com/ginobefun/elasticsearch-dynamic-synonym)
- [参考（抄）了一下](https://github.com/bells/elasticsearch-analysis-dynamic-synonym)

