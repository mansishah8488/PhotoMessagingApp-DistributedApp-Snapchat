/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.managers;

import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.comm.App.Request;
import poke.core.Mgmt.Management;
import poke.cluster.Image;
import poke.server.cluster.ClusterMonitor;
import poke.server.conf.NodeDesc;
import poke.server.monitor.HeartMonitor;
import poke.server.managers.ClusterManager;

/**
 * the connection map for server-to-server communication.
 * 
 * Note the connections/channels are initialized through the heartbeat manager
 * as it starts (and maintains) the connections through monitoring of processes.
 * 
 * 
 * TODO refactor to make this the consistent form of communication for the rest
 * of the code
 * 
 * @author gash
 * 
 */
public class ConnectionManager {
	protected static Logger logger = LoggerFactory.getLogger("management");
	private static ConcurrentLinkedQueue<ClusterMonitor> monitors = new ConcurrentLinkedQueue<ClusterMonitor>();
	private static ConcurrentLinkedQueue<ClusterMonitor> clusterMonitors = new ConcurrentLinkedQueue<ClusterMonitor>();
	private static ConcurrentLinkedQueue<ClusterMonitor> clientMonitors = new ConcurrentLinkedQueue<ClusterMonitor>();
			
	/** node ID to channel */
	private static HashMap<Integer, Channel> connections = new HashMap<Integer, Channel>();
	private static HashMap<Integer, Channel> mgmtConnections = new HashMap<Integer, Channel>();
	private static HashMap<Integer, Channel> imgConnections = new HashMap<Integer, Channel>();
	private static HashMap<Integer, Channel> clusterConnections = new HashMap<Integer, Channel>();
	private static HashMap<Integer, Channel> clientConnections = new HashMap<Integer, Channel>();
	/*
	 * Intra-Cluster Communication
	 * Client connections and Management Connections
	 */
	public static void addConnection(Integer nodeId, Channel channel, boolean isMgmt) {
		logger.info("ConnectionManager adding connection to " + nodeId);

		if (isMgmt) {
			mgmtConnections.put(nodeId, channel);
			logger.info("ConnectionManager: Current Management connections are " + (mgmtConnections.size()));
		}
		else {
			connections.put(nodeId, channel);
			logger.info("ConnectionManager: Current connections are " + (connections.size()));
		}
	}

	public static Channel getConnection(Integer nodeId, boolean isMgmt) {

		if (isMgmt)
			return mgmtConnections.get(nodeId);
		else
			return connections.get(nodeId);
	}

	
	public synchronized static void removeConnection(Integer nodeId, boolean isMgmt) {
		if (isMgmt) {
			mgmtConnections.remove(nodeId);
			logger.info("ConnectionManager: Management Connection removed.");
			logger.info("ConnectionManager: Current Management connections are " + (mgmtConnections.size()));
		}
		else {
			connections.remove(nodeId);
			logger.info("ConnectionManager: Connection removed.");
			logger.info("ConnectionManager: Current connections are " + (connections.size()));
		}
			
	}

	public synchronized static void removeConnection(Channel channel, boolean isMgmt) {

		if (isMgmt) {
			if (!mgmtConnections.containsValue(channel)) {
				return;
			}

			for (Integer nid : mgmtConnections.keySet()) {
				if (channel == mgmtConnections.get(nid)) {
					mgmtConnections.remove(nid);
					logger.info("ConnectionManager: Management Connection removed.");
					logger.info("ConnectionManager: Current Management connections are " + (mgmtConnections.size()));
					break;
				}
			}
		} else {
			if (!connections.containsValue(channel)) {
				return;
			}

			for (Integer nid : connections.keySet()) {
				if (channel == connections.get(nid)) {
					connections.remove(nid);
					logger.info("ConnectionManager: Connection removed.");
					logger.info("ConnectionManager: Current Management connections are " + (connections.size()));
					break;
				}
			}
		}
	}
	
	public synchronized static void broadcast(Request req) {
		if (req == null)
			return;

		for (Channel ch : connections.values())
			ch.write(req);
	}

	public synchronized static void broadcast(Management mgmt) {
		if (mgmt == null)
			return;

		for (Channel ch : mgmtConnections.values())
			ch.write(mgmt);
	}
	
	
	public synchronized static void broadcast(Image.Request req) {
		if (req == null)
			return;

		for (Channel ch : mgmtConnections.values())
			ch.write(req);
		
	}
	
	public synchronized static void broadcast(Management mgmt,int toNode) {
		if (mgmt == null)
			return;
		
		if(mgmtConnections.get(toNode)!=null) {
			Channel ch = mgmtConnections.get(toNode);
			ch.write(mgmt);
		}
	}

	
	public static int getNumMgmtConnections() {
		return mgmtConnections.size();
	}
	
	/*
	 * Intra-Cluster Communication for images!
	 * Using different connection pool for images!
	 */
	public synchronized static void broadcastImgClusters(Image.Request img) {
		if (img == null)
			return;
		initializeImgConnections();
		for (Map.Entry<Integer,Channel> ch : imgConnections.entrySet()) {
			logger.info("Sending image to Node: "+ch.getKey());
			ch.getValue().writeAndFlush(img);
		}
			
	}
	
	public synchronized static void broadcastImgClusters(Image.Request img,int toNode) {
		if (img == null)
			return;
		
		if(imgConnections.get(toNode)!=null) {
			Channel ch = imgConnections.get(toNode);
			ch.writeAndFlush(img);
		} else {
			if(mgmtConnections.containsKey(toNode)) {
				Channel channel = createImgConnection(toNode);
				addImgConnection(toNode, channel);
				if (!channel.isWritable()) {
					logger.error("Channel to node " + toNode + " not writable!");
				}
				channel.writeAndFlush(img);
			} else {
				logger.info("Cannot send Image message. Heartbeat not established!");
			}
		}
	}

	public synchronized static void broadcastImgtoLeader(Image.Request img,int toLeaderNode) {
		if (img == null)
			return;
		
		if(imgConnections.get(toLeaderNode)!=null) {
			Channel ch = imgConnections.get(toLeaderNode);
			ch.writeAndFlush(img);
		}
	}

	public static int getNumImgConnections() {
		return imgConnections.size();
	}
	
	public static Channel createImgConnection(int toNode) {
		TreeMap<Integer, NodeDesc> LN = ClusterManager.getInstance().getLocalClusterInfo();
		ClusterMonitor CM = new ClusterMonitor(LN.get(toNode).getHost(), LN.get(toNode).getMgmtPort(), toNode);
		monitors.add(CM);
		Channel C = CM.connect();
		return C;
	}
	
	public static void initializeImgConnections() {
		TreeMap<Integer, NodeDesc> localNodes = ClusterManager.getInstance().getLocalClusterInfo();
		for(Map.Entry<Integer,NodeDesc> entry : localNodes.entrySet()) {
			  if(imgConnections.get(entry.getKey())==null) {
				  ClusterMonitor CM = new ClusterMonitor(entry.getValue().getHost(), entry.getValue().getMgmtPort(), entry.getKey());
				  monitors.add(CM);
				  try {
					  Channel C = CM.connect();
					  addImgConnection(entry.getKey(), C);
				  } catch(Exception e) {
					  logger.info("Local Server is down: "+entry.getKey());
					  continue;
				  }
				  
			  }
			} //End of for
	}

	public static void addImgConnection(Integer NodeId, Channel channel) {
		logger.info("ConnectionManager adding Img connection to " + NodeId);
		imgConnections.put(NodeId, channel);
		logger.info("ConnectionManager: Current Img connections are " + (imgConnections.size()));
	}
	
	/*
	 * Inter-Cluster Communication
	 */
	
	//Broadcast to all available cluster connections
	public synchronized static void broadcastClusters(Image.Request img) {
		if (img == null)
			return;

		for (Channel ch : clusterConnections.values())
			ch.writeAndFlush(img);
	}
	//Broadcast to a specific cluster connection
	public synchronized static void broadcastToRemoteCluster(Image.Request img,int clusterId) {
		if (img == null)
			return;
		
		if(clusterConnections.get(clusterId)!=null) {
			Channel ch = clusterConnections.get(clusterId);
			ch.writeAndFlush(img);
		} else {
			logger.info("Connection to remote cluster not present: "+clusterId);
		}
	}
	
	public synchronized static void tempBroadcastToRemoteCluster(String host, int mgmtPort, int nodeId, int clusterId, Image.Request img) {
		if (img == null)
			return;
		
		if(clusterConnections.get(nodeId)!=null) {
			//Highly unlikely case that we have a connection somewhere
			Channel ch = clusterConnections.get(nodeId);
			ch.writeAndFlush(img);
		} else {
			Channel C = createTempClusterConnection(host,mgmtPort,nodeId, clusterId);
			if(C != null){
				C.writeAndFlush(img);
			}
		}
	}
	
	
	public static int getNumClusterConnections() {
		return clusterConnections.size();
	}
	
	//add a cluster connection
	public static void addClusterConnection(Integer clusterId, Channel channel) {
		logger.info("ConnectionManager adding cluster connection to cluster" + clusterId);
		clusterConnections.put(clusterId, channel);
		logger.info("ConnectionManager: Current Cluster connections are " + (clusterConnections.size()));
	}
	
	//Retrieve particular cluster connection
	public static Channel getClusterConnection(Integer leaderNodeId) {
		return clusterConnections.get(leaderNodeId);
	}
	
	//Remove particular cluster connection
	public synchronized static void removeClusterConnection(Integer leaderNodeId) {
		clusterConnections.remove(leaderNodeId);

		//Remove the entry from the local hashmap for the respective leaderNode once the connection is lost
		ClusterManager.getInstance().removeRemoteClusterLeader(leaderNodeId);

		logger.info("ConnectionManager: Cluster Connection removed.");
		logger.info("ConnectionManager: Current Cluster connections are " + (clusterConnections.size()));
	}
	
	//Remove cluster connection based on channel
	public synchronized static void removeClusterConnection(Channel channel) {
			
			if (!clusterConnections.containsValue(channel)) {
				return;
			}

			for (Integer nid : clusterConnections.keySet()) {
				if (channel == clusterConnections.get(nid)) {
					clusterConnections.remove(nid);
					ClusterManager.getInstance().removeRemoteClusterLeader(nid);
					logger.info("ConnectionManager: Cluster Connection removed.");
					logger.info("ConnectionManager: Current Cluster connections are " + (clusterConnections.size()));
					break;
				}
			}
	}
	
	public synchronized static boolean createClusterConnection(String host, int mgmtPort, int nodeId, int clusterId) {
		if(clusterConnections.get(clusterId)==null) {
			  ClusterMonitor CM = new ClusterMonitor(host, mgmtPort, nodeId);
			  clusterMonitors.add(CM);
			  try {
				  Channel C = CM.connect();
				  addClusterConnection(clusterId, C);
				  return true;
			  } catch(Exception e) {
				  logger.info("Cluster: "+clusterId+" and its leader: "+nodeId+" are down");
				  return false;
			  }
			  
		  } else //Connection already exists case
			  return true;
	}
	
	public synchronized static Channel createTempClusterConnection(String host, int mgmtPort, int nodeId, int clusterId) {
			  ClusterMonitor CM = new ClusterMonitor(host, mgmtPort, nodeId);
			  try {
				  Channel C = CM.connect();
				  return C;
			  } catch(Exception e) {
				  logger.info("Cluster: "+clusterId+" and its node: "+nodeId+" are down");
				  return null;
			  }
		}

	public static void createClientConnection(String host, int port,
			int clientId) {
		if(clientConnections.get(clientId)==null) {
			  ClusterMonitor CM = new ClusterMonitor(host, port, clientId);
			  clientMonitors.add(CM);
			  try {
				  Channel C = CM.connect();
				  addClientConnection(clientId, C);
			  } catch(Exception e) {
				  logger.info("Could not add connection to client:"+clientId);
			  }
			  
		  } //Connection already exists case
			  //do nothing
	}
	
	public static void addClientConnection(Integer clientId, Channel channel) {
		logger.info("ConnectionManager adding client connection to client " + clientId);
		clientConnections.put(clientId, channel);
		logger.info("ConnectionManager: Current Client connections are " + (clientConnections.size()));
	}
	
	public synchronized static void broadcastToClient(Image.Request img, int clientId) {
		if (img == null)
			return;
		
		if(clientConnections.get(clientId)!=null) {
			Channel ch = clientConnections.get(clientId);
			ch.writeAndFlush(img);
		} else {
			logger.info("Connection to client not present: "+clientId);
		}
	}

	public static void updateClientConnection(int clientId, Channel notused) {
		if(clientConnections.get(clientId)==null) {
			addClientConnection(clientId, notused);
		}
	}
	
	public static void removeClientConnection(Channel channel) {
		if(clientConnections.containsValue(channel)) {
			for(Map.Entry<Integer, Channel> entry: clientConnections.entrySet()) {
				if(entry.getValue() == channel) {
					logger.info("Lost connection to client:"+entry.getKey());
					clientConnections.remove(entry.getKey(), entry.getValue());
					break;
				}
			}
		}
	} //End of method

}
