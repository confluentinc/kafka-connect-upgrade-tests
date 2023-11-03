/*
 * Copyright [2023 - 2019] Confluent Inc.
 */

package io.confluent.kafka.connect.upgrade.tests.runner;


import io.confluent.connect.test.framework.upgrade.runner.UpgradeTestRunnerIT;

import org.apache.kafka.test.IntegrationTest;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({UpgradeTestRunnerIT.class})
@Category(IntegrationTest.class)
public class TestRunnerIT {
}