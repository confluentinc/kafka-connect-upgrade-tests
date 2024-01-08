# kafka-connect-upgrade-tests
This Repo contains upgrade connector tests for public connectors using framework: [kafka-connect-test-sdk](https://github.com/confluentinc/kafka-connect-test-sdk).

To run a upgrade integration test:

1. Export the following environment variables
```shell
export PLUGIN_NAME=<plugin_name_to_test> #example: kafka-connect-elasticsearch
export REFLECTIONS_PATH=<reflection-path-to-search-for-integration-test> #example: io.confluent
export CLONED_REPO_PATH=<connector-repo-path> #example: ~/repo/kafka-connect-elasticsearch
```
2. Run cmd
```shell
mvn clean verify
```
Upgrade IT will run for versions - Version that is being present in CLONED_REPO_PATH and its previously released versions on confluent hub.
