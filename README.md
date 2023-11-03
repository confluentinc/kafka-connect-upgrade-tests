# connect-test-framework-upgrade
## Objective:
The goal of the framework is to mimic the upgrade scenario as it happens when a connector gets upgraded to a newer version.

Integration tests are carried out by only changing the connector versions while external system remain intact.

## For Implementing Upgrade Test for a Connector

All Upgrade Tests exists in [connect-test-framework-upgrade](src/test/java/io/confluent/connect/test/framework/upgrade) module.
For onboarding any new connector, we need to add a new IT class in the above-mentioned module.
ex. [ElasticSearchSinkIT](src/test/java/io/confluent/connect/test/framework/upgrade/elasticsearch/sink/ElasticsearchSinkIT.java)

1. You need to extend the abstract [ConnectorIT](https://github.com/confluentinc/connect-comprehensive-test-framework/blob/master/connect-test-framework-upgrade/src/main/java/io/confluent/connect/test/framework/upgrade/common/ConnectorIT.java) class and implement the methods -
    - **setup()** - Initialize the sink/source systems, clients need for communication etc.
      Create the required topics for test using the EmbeddedConnectCluster which was passed as parameter.
      - **getConnectorConfigs()** - Here create the connector related configs required for test.
        ```java Example
          public List<UpgradeConfig> getConnectorConfigs() {
            List<UpgradeConfig> upgradeConfigs = new ArrayList<>();
            upgradeConfigs.add(new UpgradeConfig(TOPICS_CONFIG, TOPIC));
            upgradeConfigs.add(new UpgradeConfig(TASKS_MAX_CONFIG, Integer.toString(1), "10.0.3", ">"));
            return upgradeConfigs;
         }
        ```
        Note: In here if we want to skip some config for a specific version then version and condition can also be added in UpgradeConfig. When ConfigHelper is called to generate the config, it will evaluate the condition and pick the required ones for the current version.

    - **publishData()** - Here produce data required for test, In case of Source IT test, publish data to the source system and For Sink IT test, publish data to the topic using the EmbeddedConnectCluster which was available as parameter. To generate records uniquely a unique identifier (uniqueId) is also passed as paramter.
    - **verify()** - Here we need to verify the data between kafka topic and external system. EmbeddedConnectCluster is also available as parameter for consuming the records from topic.
2. Add Annotation [TestPlugin(pluginName = "kafka-connect-dummy")](https://github.com/confluentinc/connect-comprehensive-test-framework/blob/master/connect-test-framework-upgrade/src/main/java/io/confluent/connect/test/framework/upgrade/annotations/TestPlugin.java) over the newly created IT class.
3. In order to skip the test for some version use Annotation SkipUpgradeTest on the IT class. 
 
    Examples. 
    
   1. @SkipUpgradeTest(version="x.y.z", condition='=') Test will skip only for specified version.
   2. @SkipUpgradeTest(version="x.y.z", condition='>') Test will skip for all versions greater than specified version.
   3. @SkipUpgradeTest(version="x.y.z", condition='<') Test will skip for all versions smaller than specified version.
   4. We can also add multiple conditions using @SkipUpgradeTests
      1. example - 
      
         ```java
            @SkipUpgradeTests(
            @SkipUpgradeTest(version="x.y.z", condition='<')
            @SkipUpgradeTest(version="x.y.z", condition='=')
            )
         ```
4. Also we need to add the plugin repo & hub path in [PluginEnum](src/main/java/io/confluent/kafka/connect/upgrade/test/framework/util/PluginEnum.java) class, so that runner class will be able to download and clone repo on its own.
    Example -
   ```java 
         ELASTIC_SEARCH_SINK("kafka-connect-elasticsearch",
      "confluentinc/kafka-connect-elasticsearch",
      "confluentinc/kafka-connect-elasticsearch.git"), 
   ```
5. To run the upgrade test on each commit in connector's repo (including PRs), we need to add the below code in the connector JenkinsFile. NOTE: pass the PLUGIN_NAME w.r.t connector repo.
    Example - https://github.com/confluentinc/kafka-connect-elasticsearch/blob/master/Jenkinsfile#L10
    ```groovy
    def upgradeConnectorTest(body) {
        build job: "confluentinc-pr/connect-comprehensive-test-framework/PR-3/", parameters: [
                string(name: 'PLUGIN_NAME', value: 'kafka-connect-elasticsearch'),
                string(name: 'BRANCH_TO_TEST', value: env.CHANGE_BRANCH)
        ], wait: true
    }
    upgradeConnectorTest{}
    ```
6. For local testing clone the repo and set environment variables, then run `mvn integration-test`
    ```
    export PLUGIN_NAME=<plugin-name-as-in-test-plugin-annotation>;
    export BRANCH_TO_TEST=11.0.x
    ```