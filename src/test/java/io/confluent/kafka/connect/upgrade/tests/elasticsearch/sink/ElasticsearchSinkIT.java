/*
 * Copyright 2023 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink;


import io.confluent.connect.test.sdk.upgrade.annotations.TestPlugin;
import io.confluent.connect.test.sdk.upgrade.common.ConnectorIT;
import io.confluent.connect.test.sdk.upgrade.common.UpgradeConfig;
import io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink.helper.ElasticsearchContainer;
import io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink.helper.ElasticsearchHelperClient;
import io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink.helper.ElasticsearchSinkConnectorConfig;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.storage.StringConverter;
import org.apache.kafka.connect.util.clusters.EmbeddedConnectCluster;
import org.apache.kafka.test.IntegrationTest;
import org.apache.kafka.test.TestUtils;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.security.user.User;
import org.elasticsearch.client.security.user.privileges.IndicesPrivileges;
import org.elasticsearch.client.security.user.privileges.Role;
import org.elasticsearch.search.SearchHit;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink.helper.ElasticsearchContainer.CONNECTION_PASSWORD_CONFIG;
import static io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink.helper.ElasticsearchContainer.CONNECTION_URL_CONFIG;
import static io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink.helper.ElasticsearchContainer.CONNECTION_USERNAME_CONFIG;
import static org.apache.kafka.connect.json.JsonConverterConfig.SCHEMAS_ENABLE_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.CONNECTOR_CLASS_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.KEY_CONVERTER_CLASS_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.TASKS_MAX_CONFIG;
import static org.apache.kafka.connect.runtime.ConnectorConfig.VALUE_CONVERTER_CLASS_CONFIG;
import static org.apache.kafka.connect.runtime.SinkConnectorConfig.TOPICS_CONFIG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
@TestPlugin(pluginName = "kafka-connect-elasticsearch",
    hubPath = "confluentinc/kafka-connect-elasticsearch",
    repoPath = "confluentinc/kafka-connect-elasticsearch.git")
public class ElasticsearchSinkIT extends ConnectorIT {

  protected static final String CONNECTOR_NAME = "es-connector";
  protected static final String TOPIC = "test";

  // User that has a minimal required and documented set of privileges
  public static final String ELASTIC_MINIMAL_PRIVILEGES_NAME = "frank";
  public static final String ELASTIC_MINIMAL_PRIVILEGES_PASSWORD = "WatermelonInEasterHay";

  public static final String ELASTIC_DATA_STREAM_MINIMAL_PRIVILEGES_NAME = "bob";
  public static final String ELASTIC_DS_MINIMAL_PRIVILEGES_PASSWORD = "PeachesInGeorgia";

  private static final String ES_SINK_CONNECTOR_ROLE = "es_sink_connector_role";
  private static final String ES_SINK_CONNECTOR_DS_ROLE = "es_sink_connector_ds_role";
  private static final long CONSUME_MAX_DURATION_MS = TimeUnit.SECONDS.toMillis(60);

  protected static ElasticsearchContainer container;

  protected boolean isDataStream;
  protected ElasticsearchHelperClient helperClient;
  protected Map<String, String> props;
  protected String index;

  public ElasticsearchSinkIT() {
    super();
    NUM_OF_RECORDS = 5;
  }

  @Override
  public void setup(EmbeddedConnectCluster connect) {
    if (container == null || !container.isRunning()) {
      setupBeforeAll();
    }
    index = TOPIC;
    isDataStream = false;
    props = new HashMap<>();
    props.put(CONNECTION_URL_CONFIG, container.getConnectionUrl());
    helperClient = container.getHelperClient(props);
    connect.kafka().createTopic(TOPIC);
  }

  @Override
  public List<UpgradeConfig> getConnectorConfigs() {
    List<UpgradeConfig> upgradeConfigs = new ArrayList<>();
    upgradeConfigs.add(new UpgradeConfig(CONNECTOR_CLASS_CONFIG,
        "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector"));
    upgradeConfigs.add(new UpgradeConfig(TOPICS_CONFIG, TOPIC));
    upgradeConfigs.add(new UpgradeConfig(TASKS_MAX_CONFIG, Integer.toString(1)));
    upgradeConfigs.add(new UpgradeConfig(KEY_CONVERTER_CLASS_CONFIG, StringConverter.class.getName()));
    upgradeConfigs.add(new UpgradeConfig(VALUE_CONVERTER_CLASS_CONFIG, JsonConverter.class.getName()));
    upgradeConfigs.add(new UpgradeConfig("value.converter." + SCHEMAS_ENABLE_CONFIG, "false"));
    upgradeConfigs.add(new UpgradeConfig("value.converter." + SCHEMAS_ENABLE_CONFIG, "false"));
    upgradeConfigs.add(new UpgradeConfig(CONNECTION_USERNAME_CONFIG, ELASTIC_MINIMAL_PRIVILEGES_NAME));
    upgradeConfigs.add(new UpgradeConfig(CONNECTION_PASSWORD_CONFIG, ELASTIC_MINIMAL_PRIVILEGES_PASSWORD));

    upgradeConfigs.add(new UpgradeConfig(CONNECTION_URL_CONFIG, container.getConnectionUrl()));
    upgradeConfigs.add(new UpgradeConfig(ElasticsearchSinkConnectorConfig.IGNORE_KEY_CONFIG, "true"));
    upgradeConfigs.add(new UpgradeConfig(ElasticsearchSinkConnectorConfig.IGNORE_SCHEMA_CONFIG, "true"));

    return upgradeConfigs;
  }

  @Override
  public void publishData(EmbeddedConnectCluster connect, int uniqueId) {
    writeRecordsFromStartIndex(connect, uniqueId, NUM_OF_RECORDS);
  }

  @Override
  public void verify(EmbeddedConnectCluster connect, int uniqueId) throws Exception {
    verifySearchResults(uniqueId, NUM_OF_RECORDS);
  }

  @Override
  public void cleanUp() {
    this.helperClient.close();
    container.close();
  }

  protected void verifySearchResults(int startId, int numRecords) throws Exception {
    waitForRecords(startId - 1 + numRecords);
    List<Integer> ids = new ArrayList<>();

    for (SearchHit hit : helperClient.search(index)) {
      int id = (Integer) hit.getSourceAsMap().get("doc_num");
      assertNotNull(id);
      ids.add(id);
      if (isDataStream) {
        assertTrue(hit.getIndex().contains(index));
      } else {
        assertEquals(index, hit.getIndex());
      }
    }

    assertTrue(ids.size() >= startId - 1 + numRecords);
    for (int i = 0; i < startId - 1 + numRecords; i++) {
      assertTrue(ids.contains(i+1));
    }
  }


  protected void waitForRecords(int numRecords) throws InterruptedException {
    TestUtils.waitForCondition(
        () -> {
          try {
            return helperClient.getDocCount(index) == numRecords;
          } catch (ElasticsearchStatusException e) {
            if (e.getMessage().contains("index_not_found_exception")) {
              return false;
            }

            throw e;
          }
        },
        CONSUME_MAX_DURATION_MS,
        "Sufficient amount of document were not found in ES on time."
    );
  }
  protected void writeRecords(EmbeddedConnectCluster connect, int numRecords) {
    writeRecordsFromStartIndex(connect, 0, numRecords);
  }
  protected void writeRecordsFromStartIndex(EmbeddedConnectCluster connect, int start, int numRecords) {
    for (int i = start; i < start + numRecords; i++) {
      connect.kafka().produce(
          TOPIC,
          String.valueOf(i),
          String.format("{\"doc_num\":%d,\"@timestamp\":\"2021-04-28T11:11:22.%03dZ\"}", i, i)
      );
    }
  }

  public static void setupBeforeAll() {
    Map<User, String> users = getUsers();
    List<Role> roles = getRoles();
    container = ElasticsearchContainer.fromSystemProperties().withBasicAuth(users, roles);
    container.start();
  }
  protected static List<Role> getRoles() {
    List<Role> roles = new ArrayList<>();
    roles.add(getMinimalPrivilegesRole(false));
    roles.add(getMinimalPrivilegesRole(true));
    return roles;
  }

  protected static Map<User, String> getUsers() {
    Map<User, String> users = new HashMap<>();
    users.put(getMinimalPrivilegesUser(true), getMinimalPrivilegesPassword(true));
    users.put(getMinimalPrivilegesUser(false), getMinimalPrivilegesPassword(false));
    return users;
  }
  private static Role getMinimalPrivilegesRole(boolean forDataStream) {
    IndicesPrivileges.Builder indicesPrivilegesBuilder = IndicesPrivileges.builder();
    IndicesPrivileges indicesPrivileges = indicesPrivilegesBuilder
        .indices("*")
        .privileges("create_index", "read", "write", "view_index_metadata")
        .build();
    // Historically (i.e. ES the previous test base version 7.9.3), ES_SINK_CONNECTOR_ROLE would not require the
    // "monitor" cluster privilege.  However, this has changed for 7.16.3, although leaving the surrounding
    // logic in place in the case that future ES versions or tests wish to diverge the permissions.
    return Role.builder()
        .name(forDataStream ? ES_SINK_CONNECTOR_DS_ROLE : ES_SINK_CONNECTOR_ROLE)
        .indicesPrivileges(indicesPrivileges)
        .clusterPrivileges("monitor")
        .build();
  }

  private static User getMinimalPrivilegesUser(boolean forDataStream) {
    return new User(forDataStream ? ELASTIC_DATA_STREAM_MINIMAL_PRIVILEGES_NAME : ELASTIC_MINIMAL_PRIVILEGES_NAME,
        Collections.singletonList(forDataStream ? ES_SINK_CONNECTOR_DS_ROLE : ES_SINK_CONNECTOR_ROLE));
  }
  private static String getMinimalPrivilegesPassword(boolean forDataStream) {
    return forDataStream ? ELASTIC_DS_MINIMAL_PRIVILEGES_PASSWORD : ELASTIC_MINIMAL_PRIVILEGES_PASSWORD;
  }
}
