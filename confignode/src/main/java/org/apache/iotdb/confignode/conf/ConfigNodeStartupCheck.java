/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.confignode.conf;

import org.apache.iotdb.commons.conf.IoTDBConstant;
import org.apache.iotdb.commons.exception.ConfigurationException;
import org.apache.iotdb.commons.exception.StartupException;
import org.apache.iotdb.confignode.manager.load.balancer.RouteBalancer;
import org.apache.iotdb.consensus.ConsensusFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * ConfigNodeStartupCheck checks the parameters in iotdb-confignode.properties and
 * confignode-system.properties when start and restart
 */
public class ConfigNodeStartupCheck {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigNodeStartupCheck.class);

  private static final ConfigNodeConfig CONF = ConfigNodeDescriptor.getInstance().getConf();

  public void startUpCheck() throws StartupException, IOException, ConfigurationException {
    checkGlobalConfig();
    createDirsIfNecessary();
    if (SystemPropertiesUtils.isRestarted()) {
      SystemPropertiesUtils.checkSystemProperties();
    }
  }

  /** Check whether the global configuration of the cluster is correct */
  private void checkGlobalConfig() throws ConfigurationException {
    // When the ConfigNode consensus protocol is set to StandAlone,
    // the target_config_nodes needs to point to itself
    if (CONF.getConfigNodeConsensusProtocolClass().equals(ConsensusFactory.StandAloneConsensus)
        && (!CONF.getInternalAddress().equals(CONF.getTargetConfigNode().getIp())
            || CONF.getInternalPort() != CONF.getTargetConfigNode().getPort())) {
      throw new ConfigurationException(
          IoTDBConstant.TARGET_CONFIG_NODES,
          CONF.getTargetConfigNode().getIp() + ":" + CONF.getTargetConfigNode().getPort(),
          CONF.getInternalAddress() + ":" + CONF.getInternalPort());
    }

    // When the data region consensus protocol is set to StandAlone,
    // the data replication factor must be 1
    if (CONF.getDataRegionConsensusProtocolClass().equals(ConsensusFactory.StandAloneConsensus)
        && CONF.getDataReplicationFactor() != 1) {
      throw new ConfigurationException(
          "data_replication_factor",
          String.valueOf(CONF.getDataReplicationFactor()),
          String.valueOf(1));
    }

    // When the schema region consensus protocol is set to StandAlone,
    // the schema replication factor must be 1
    if (CONF.getSchemaRegionConsensusProtocolClass().equals(ConsensusFactory.StandAloneConsensus)
        && CONF.getSchemaReplicationFactor() != 1) {
      throw new ConfigurationException(
          "schema_replication_factor",
          String.valueOf(CONF.getSchemaReplicationFactor()),
          String.valueOf(1));
    }

    // When the schema region consensus protocol is set to MultiLeaderConsensus,
    // we should report an error
    if (CONF.getSchemaRegionConsensusProtocolClass()
        .equals(ConsensusFactory.MultiLeaderConsensus)) {
      throw new ConfigurationException(
          "schema_region_consensus_protocol_class",
          String.valueOf(CONF.getSchemaRegionConsensusProtocolClass()),
          String.format(
              "%s or %s", ConsensusFactory.StandAloneConsensus, ConsensusFactory.RatisConsensus));
    }

    if (!CONF.getRoutingPolicy().equals(RouteBalancer.LEADER_POLICY)
        && !CONF.getRoutingPolicy().equals(RouteBalancer.GREEDY_POLICY)) {
      throw new ConfigurationException(
          "routing_policy", CONF.getRoutingPolicy(), "leader or greedy");
    }
  }

  private void createDirsIfNecessary() throws IOException {
    // If systemDir does not exist, create systemDir
    File systemDir = new File(CONF.getSystemDir());
    createDirIfEmpty(systemDir);

    // If consensusDir does not exist, create consensusDir
    File consensusDir = new File(CONF.getConsensusDir());
    createDirIfEmpty(consensusDir);
  }

  private void createDirIfEmpty(File dir) throws IOException {
    if (!dir.exists()) {
      if (dir.mkdirs()) {
        LOGGER.info("Make dirs: {}", dir);
      } else {
        throw new IOException(
            String.format(
                "Start ConfigNode failed, because couldn't make system dirs: %s.",
                dir.getAbsolutePath()));
      }
    }
  }

  private static class ConfigNodeConfCheckHolder {

    private static final ConfigNodeStartupCheck INSTANCE = new ConfigNodeStartupCheck();

    private ConfigNodeConfCheckHolder() {
      // Empty constructor
    }
  }

  public static ConfigNodeStartupCheck getInstance() {
    return ConfigNodeConfCheckHolder.INSTANCE;
  }
}
