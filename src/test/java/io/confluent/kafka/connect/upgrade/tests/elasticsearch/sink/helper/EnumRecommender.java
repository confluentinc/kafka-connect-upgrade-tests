/*
 * Copyright [2023 - 2019] Confluent Inc.
 */

package io.confluent.kafka.connect.upgrade.tests.elasticsearch.sink.helper;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;

class EnumRecommender<T extends Enum<?>> implements ConfigDef.Validator, ConfigDef.Recommender {

  private final Set<String> validValues;
  private final Class<T> enumClass;

  public EnumRecommender(Class<T> enumClass) {
    this.enumClass = enumClass;
    Set<String> validEnums = new LinkedHashSet<>();
    for (Object o : enumClass.getEnumConstants()) {
      String key = o.toString().toLowerCase();
      validEnums.add(key);
    }

    this.validValues = Collections.unmodifiableSet(validEnums);
  }

  @Override
  public void ensureValid(String key, Object value) {
    if (value == null) {
      return;
    }
    String enumValue = value.toString().toLowerCase();
    if (value != null && !validValues.contains(enumValue)) {
      throw new ConfigException(key, value, "Value must be one of: " + this);
    }
  }

  @Override
  public String toString() {
    return validValues.toString();
  }

  @Override
  public List<Object> validValues(String name, Map<String, Object> connectorConfigs) {
    return Collections.unmodifiableList(new ArrayList<>(validValues));
  }

  @Override
  public boolean visible(String name, Map<String, Object> connectorConfigs) {
    return true;
  }
}
