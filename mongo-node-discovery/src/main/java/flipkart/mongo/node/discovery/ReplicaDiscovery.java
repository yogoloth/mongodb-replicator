/*
 * Copyright 2012-2015, the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package flipkart.mongo.node.discovery;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import flipkart.mongo.node.discovery.exceptions.MongoDiscoveryException;
import flipkart.mongo.replicator.core.model.Node;
import flipkart.mongo.replicator.core.model.ReplicaSetConfig;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by kishan.gajjar on 30/10/14.
 */
public class ReplicaDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ReplicaDiscovery.class);

    private List<Node> configSvrNodes;
    private static final String CONFIG_DB_NAME = "config";
    private static final String CONFIG_TABLE_NAME = "shards";

    public ReplicaDiscovery(List<Node> configSvrNodes) {
        this.configSvrNodes = configSvrNodes;
    }

    public ImmutableList<ReplicaSetConfig> discover() throws MongoDiscoveryException {

        List<ReplicaSetConfig> replicaSetConfigs = Lists.newArrayList();

        for (ReplicaSetConfig mongoSReplicaSet : this.getMongoSReplicaSets()) {
            NodeDiscovery nodeDiscovery = new NodeDiscovery(mongoSReplicaSet);
            ReplicaSetConfig replicaSetBasedOnDiscovery = nodeDiscovery.discover();
            replicaSetConfigs.add(replicaSetBasedOnDiscovery);
        }

        return ImmutableList.copyOf(replicaSetConfigs);
    }

    private List<ReplicaSetConfig> getMongoSReplicaSets() throws MongoDiscoveryException {

        List<ReplicaSetConfig> replicaSetConfigs = Lists.newArrayList();
        MongoClient client = MongoConnector.getMongoClient(configSvrNodes);

        FindIterable<Document> documents = client.getDatabase(CONFIG_DB_NAME).getCollection(CONFIG_TABLE_NAME).find();
        for (Document document : documents) {
            String shardName = (String) document.get("_id");
            String hostString = (String) document.get("host");

            List<Node> replicaNodes = this.getReplicaNodes(hostString);
            if (!replicaNodes.isEmpty()) {
                ReplicaSetConfig replicaConfig = new ReplicaSetConfig(shardName, replicaNodes);
                replicaSetConfigs.add(replicaConfig);
            }
        }

        return replicaSetConfigs;
    }

    private List<Node> getReplicaNodes(String hostString) {

        List<Node> replicaNodes = Lists.newArrayList();

        String[] hostsInfo = hostString.split(",");
        for (String hostInfo : hostsInfo) {

            String[] data = hostInfo.split("/");
            String hostPortInfo = data.length > 1 ? data[1] : data[0];
            String[] details = hostPortInfo.split(":");

            if (details.length < 2)
                continue;

            String host = details[0];
            int port = Integer.parseInt(details[1]);

            Node node = new Node(host, port);
            replicaNodes.add(node);
        }

        return replicaNodes;
    }
}
