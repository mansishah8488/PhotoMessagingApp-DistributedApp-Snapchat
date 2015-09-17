package poke.server.managers;

import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.server.cluster.ClusterQueue;
import poke.server.conf.ClusterNodeDesc;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.management.ManagementQueue;
import poke.cluster.Image.Header;
import poke.cluster.Image.PayLoad;
import poke.cluster.Image.Ping;
import poke.cluster.Image.Request;

public class ClusterManager implements Runnable  {
	
	/*
	 * @author Mario Vinay
	 * @description This class will manager the replication of cluster information and cluster handshake
	 */
	
	protected static Logger logger = LoggerFactory.getLogger("clustermanager");
	private boolean DEBUG = true;
	protected static AtomicReference<ClusterManager> instance = new AtomicReference<ClusterManager>();
	private static ServerConf conf;
	boolean forever = true;
    boolean isPingDone = false;	
	
    private static TreeMap<Float, ClusterNodeDesc> remoteClusterInfo = new TreeMap<Float,ClusterNodeDesc>();
	private static TreeMap<Integer, NodeDesc> localClusterInfo = new TreeMap<Integer, NodeDesc>();
	private static TreeMap<Integer, NodeDesc> localClientInfo = new TreeMap<Integer, NodeDesc>();
	
	private HashMap<Integer,Integer> remoteClusterLeaders = new HashMap<Integer,Integer>();
	
	
	public static ClusterManager initialize(ServerConf config) {
		conf = config;
		instance.compareAndSet(null, new ClusterManager());
		remoteClusterInfo = conf.getOtherCluster().getOtherClusterNodes();
		localClusterInfo = conf.getAdjacent().getAdjacentNodes();
		printRemoteClusterInformation();
		//getInstance().addRemoteClusterLeader(5, 55); //This line is just for debugging of replication
		logger.info("ClusterManager Initialized");
		return instance.get();
	}
	
	public ServerConf getServerConf() {
		return conf;
	}
	
	public static ClusterManager getInstance() {
		return instance.get();
	}
	
	public void addLocalClient(int clientId, String host, int port) {
		NodeDesc nd = new NodeDesc();
		nd.setNodeName("Some client");
		nd.setNodeId(clientId);
		//nd.setHost(host);
		//nd.setPort(port);
		localClientInfo.put(clientId, nd);
		//ConnectionManager.createClientConnection(host,port,clientId);
	}
	
	public boolean isClientPresent(int clientId) {
		return localClientInfo.containsKey(clientId);
	}
	
	public void run() {
		
		//Check if current node is the leader
		//If so, try to connect to leaders of all other nodes
		while(true) {
		
			try
			{
				//logger.info("isPingDone:"+isPingDone);
				//logger.info("isLeaderAlive():"+ElectionManager.getInstance().isLeaderAlive());
				//logger.info("conf.getNodeId():"+conf.getNodeId());
				//logger.info("ElectionManager.getInstance().getLeaderNode():"+ElectionManager.getInstance().getLeaderNode());
				Thread.sleep(5000);
				if(!isPingDone && ElectionManager.getInstance().isLeaderAlive() && ElectionManager.getInstance().getLeaderNode() !=null && conf.getNodeId() == ElectionManager.getInstance().getLeaderNode())
				{
				//First traverse through all the leaders we know of
				//If we are not able to connect to them, we send a ping to each of their cluster nodes!
				sendPingToKnownLeaders();
				sendPingToUnknownLeaders();
				isPingDone = true;
				}
			}catch(Exception ex)
			{
				logger.info("Error in ping to the node in the other cluster");
				ex.printStackTrace();
			}
			finally {
				
			}
		}	
		
	}

	private void sendPingToKnownLeaders() {
		for(Map.Entry<Integer, Integer> clusterLeader: remoteClusterLeaders.entrySet()) {
			//If we cannot connect to the leader, remove the entry for the cluster
			//Otherwise initialize the connection to the leader
			logger.info("Establishing connections with known leaders.....");
			ClusterNodeDesc clusterInfo = remoteClusterInfo.get(Float.parseFloat(clusterLeader.getKey()+"."+clusterLeader.getValue()));
			logger.info("Establishing a connection with cluster id:"+clusterInfo.getClusterId());
			logger.info("Establishing a connection with node id:"+clusterInfo.getNodeId());
			
			if(ConnectionManager.createClusterConnection(clusterInfo.getHost(),clusterInfo.getMgmtPort(),clusterLeader.getKey(),clusterLeader.getValue())) {
				//We are able to connect. Now lets send a ping
				Header.Builder headerBuilder = Header.newBuilder();
				headerBuilder.setClientId(clusterInfo.getNodeId());
				headerBuilder.setClusterId(clusterInfo.getClusterId());
				headerBuilder.setIsClient(false);
				headerBuilder.setCaption("Ping");
				
				PayLoad.Builder payLoadBuilder = PayLoad.newBuilder();
				byte[] arr = new byte[10];
				
				payLoadBuilder.setData(ByteString.copyFrom(arr));
								
				Ping.Builder pingBuilder = Ping.newBuilder();
				pingBuilder.setIsPing(true);
				
				Request.Builder requestBuilder = Request.newBuilder();
				requestBuilder.setHeader(headerBuilder);
				requestBuilder.setPayload(payLoadBuilder);
				requestBuilder.setPing(pingBuilder);
				ConnectionManager.broadcastToRemoteCluster(requestBuilder.build(),clusterLeader.getKey());
			} else {
				//Unable to connect. remove the entry
				remoteClusterLeaders.remove(clusterLeader.getKey(),clusterLeader.getValue());
			}			
		} //End of for loop
	}
	
	public void sendPingToKnownLeaders(int clusterId) {
		for(Map.Entry<Integer, Integer> clusterLeader: remoteClusterLeaders.entrySet()) {
			//If we cannot connect to the leader, remove the entry for the cluster
			//Otherwise initialize the connection to the leader
			if(clusterLeader.getKey() == clusterId) {
				logger.info("Establishing connections with cluster.....");
				ClusterNodeDesc clusterInfo = remoteClusterInfo.get(Float.parseFloat(clusterLeader.getKey()+"."+clusterLeader.getValue()));
				logger.info("Establishing a connection with cluster id:"+clusterInfo.getClusterId());
				logger.info("Establishing a connection with node id:"+clusterInfo.getNodeId());
				if(ConnectionManager.createClusterConnection(clusterInfo.getHost(),clusterInfo.getMgmtPort(),clusterLeader.getKey(),clusterLeader.getValue())) {
					//We are able to connect. Now lets send a ping
					Header.Builder headerBuilder = Header.newBuilder();
					headerBuilder.setClientId(clusterInfo.getNodeId());
					headerBuilder.setClusterId(clusterInfo.getClusterId());
					headerBuilder.setIsClient(false);
					headerBuilder.setCaption("Ping");
					
					PayLoad.Builder payLoadBuilder = PayLoad.newBuilder();
					byte[] arr = new byte[10];
					
					payLoadBuilder.setData(ByteString.copyFrom(arr));
									
					Ping.Builder pingBuilder = Ping.newBuilder();
					pingBuilder.setIsPing(true);
					
					Request.Builder requestBuilder = Request.newBuilder();
					requestBuilder.setHeader(headerBuilder);
					requestBuilder.setPayload(payLoadBuilder);
					requestBuilder.setPing(pingBuilder);
					ConnectionManager.broadcastToRemoteCluster(requestBuilder.build(),clusterLeader.getKey());
				} else {
					//Unable to connect. remove the entry
					remoteClusterLeaders.remove(clusterLeader.getKey(),clusterLeader.getValue());
				}
			}
			
			
						
		} //End of for loop
	}
	
	private void sendPingToUnknownLeaders() {
		for (ClusterNodeDesc clusterInfo : remoteClusterInfo.values())
		{
			if(!remoteClusterLeaders.containsKey(clusterInfo.getClusterId()))
			{	
				logger.info("Sending ping to all nodes (unknown leaders) of remote clusters.....");
				logger.info("Cluster id:"+clusterInfo.getClusterId());
				logger.info("Node id:"+clusterInfo.getNodeId());
				
				Header.Builder headerBuilder = Header.newBuilder();
				headerBuilder.setClientId(clusterInfo.getNodeId());
				headerBuilder.setClusterId(clusterInfo.getClusterId());
				headerBuilder.setIsClient(false);
				headerBuilder.setCaption("Ping");
				
				PayLoad.Builder payLoadBuilder = PayLoad.newBuilder();
				byte[] arr = new byte[10];
				
				payLoadBuilder.setData(ByteString.copyFrom(arr));
								
				Ping.Builder pingBuilder = Ping.newBuilder();
				pingBuilder.setIsPing(true);
				
				Request.Builder requestBuilder = Request.newBuilder();
				requestBuilder.setHeader(headerBuilder);
				requestBuilder.setPayload(payLoadBuilder);
				requestBuilder.setPing(pingBuilder);
				
				//connection needs to be done using new handle made
				ConnectionManager.tempBroadcastToRemoteCluster(clusterInfo.getHost(),clusterInfo.getMgmtPort(),clusterInfo.getNodeId(),clusterInfo.getClusterId(),requestBuilder.build());
			} else {
				//Case where we know who is the leader -- nothing to do
			}
		}//End of for loop	
	}

	public void addRemoteClusterLeader(int clusterID, int leaderNodeID) {
		if(remoteClusterLeaders.containsKey(clusterID)) {
			if(remoteClusterLeaders.get(clusterID) != leaderNodeID) {
				logger.info("Update cluster: "+clusterID+" and leader: "+leaderNodeID);
			}
		} else {
			logger.info("Added new cluster: "+clusterID+" and leader: "+leaderNodeID);
		}
		//New leader nodes are added/updated for each cluster that has a leader
		remoteClusterLeaders.put(clusterID, leaderNodeID); 
	}
	
	public void removeRemoteClusterLeader(int clusterID) {
		logger.info("Removing cluster: "+clusterID);
		remoteClusterLeaders.remove(clusterID);
	}
	
	
	public HashMap<Integer, Integer> getRemoteClusterLeaders() {
		return remoteClusterLeaders;
	}
	
	public TreeMap<Integer, NodeDesc> getLocalClusterInfo() {
		return localClusterInfo;
	}
	
	public TreeMap<Float, ClusterNodeDesc> getRemoteClusterInfo() {
		return remoteClusterInfo;
	}
	
	public static void printRemoteClusterInformation() {
		logger.info("Printing remote cluster information");
		
		if(remoteClusterInfo.size() == 0) {
			logger.info("No remote clusters configured");
		} else {
			for(ClusterNodeDesc CND : remoteClusterInfo.values()) {
				logger.info("----------------------------------");
				logger.info("Cluster ID:"+CND.getClusterId());
				logger.info("Node ID:"+CND.getNodeId());
				logger.info("Node Name:"+CND.getNodeName());
				logger.info("Host:"+CND.getHost());
				logger.info("Port:"+CND.getPort());
				logger.info("Management Port:"+CND.getMgmtPort());
			}
		}
	}

	public void resetRemoteClusterLeaders() {
		remoteClusterLeaders = new HashMap<Integer,Integer>();
	}
}
