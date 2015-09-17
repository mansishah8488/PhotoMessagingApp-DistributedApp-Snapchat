/*
 * copyright 2012, gash
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
package poke.server.cluster;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.server.cluster.ClusterQueue.ClusterQueueEntry;
import poke.server.conf.ClusterNodeDesc;
import poke.server.conf.ServerConf;
import poke.server.managers.ConnectionManager;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatManager;
import poke.server.managers.JobManager;
import poke.server.managers.NetworkManager;
import poke.server.managers.ClusterManager;
import poke.cluster.Image.PayLoad;
import poke.cluster.Image.Request;
/**
 * The inbound management worker is the cortex for all work related to the
 * Health and Status (H&S) of the node.
 * 
 * Example work includes processing job bidding, elections, network connectivity
 * building. An instance of this worker is blocked on the socket listening for
 * events. If you want to approximate a timer, executes on a consistent interval
 * (e.g., polling, spin-lock), you will have to implement a thread that injects
 * events into this worker's queue.
 * 
 * HB requests to this node are NOT processed here. Nodes making a request to
 * receive heartbeats are in essence requesting to establish an edge (comm)
 * between two nodes. On failure, the connecter must initiate a reconnect - to
 * produce the heartbeatMgr.
 * 
 * On loss of connection: When a connection is lost, the emitter will not try to
 * establish the connection. The edge associated with the lost node is marked
 * failed and all outbound (enqueued) messages are dropped (TBD as we could
 * delay this action to allow the node to detect and re-establish the
 * connection).
 * 
 * Connections are bi-directional (reads and writes) at this time.
 * 
 * @author gash
 * 
 */
public class InboundClusterWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("cluster");
	protected ClusterManager clusterMgr;

	int workerId;
	boolean forever = true;

	/*int max=8;
    int min=2;
   
    Random rand = new Random();

    int  randomNum = rand.nextInt((max - min) + 1) + min;
    boolean isRunning=false;
   
   
    Timer timer = new Timer();
     
   
    TimerTask task = new TimerTask() { 
         int i = randomNum;
           
           // if(!isRunning){
            public void run() {
                System.out.println(i--);
                isRunning=true;
                if (i< 0)
                {
                   
                    i=randomNum;
                    timer.cancel();
                isRunning=false;
                return;
                }
            }
    };  
	*/
	public InboundClusterWorker(ThreadGroup tgrp, int workerId) {
		super(tgrp, "inbound-cluster-" + workerId);
		logger.info("InboundClusterWorker initialized");
		this.workerId = workerId;

		if (ClusterQueue.outbound == null)
			throw new RuntimeException("connection worker detected null queue");
	}

	@Override
	public void run() {
		while (true) {
			if (!forever && ClusterQueue.inbound.size() == 0)
				break;

			try {
				// block until a message is enqueued
				ClusterQueueEntry msg = ClusterQueue.inbound.take();
				//logger.info("Inside try!");
				if (logger.isDebugEnabled())
					logger.debug("Inbound management message received");

				Request req = (Request) msg.req;
				//logger.info("Received payload ahoy!");

				if(req.getPing().getIsPing())
				{ 
					//Case 1: Received ping from another cluster
					logger.info("Ping recieved from Cluster: "+req.getHeader().getClusterId()+" Node: "+req.getHeader().getClientId());
					
					if(ElectionManager.getInstance().isLeaderAlive() && ElectionManager.getInstance().getLeaderNode() == ClusterManager.getInstance().getServerConf().getNodeId()) {
						ClusterManager.getInstance().addRemoteClusterLeader(req.getHeader().getClusterId(), req.getHeader().getClientId());
						ClusterNodeDesc CND = ClusterManager.getInstance().getRemoteClusterInfo().get(Float.parseFloat((req.getHeader().getClusterId()+"."+req.getHeader().getClientId())));
						ConnectionManager.createClusterConnection(CND.getHost(), CND.getMgmtPort(), CND.getNodeId(), CND.getClusterId());
						sendPingtoClusterLeader(req.getHeader().getClusterId());
					}
					//If we are not the leader or leader election is not completed. Ignore all pings!	
				} else if(!req.getPing().getIsPing() && req.getHeader().getClusterId() == ClusterManager.getInstance().getServerConf().getClusterId()) {
					//Case 2: Received image intended for this cluster - inter or intra cluster communication
					//Need to maintain which clients are connected to this node. -- not necessary anymore. we just broadcast
					//If the destination client is connected to this node, send it to that client by creating a channel
					
					//Need to route check if client is connected to this node
					if(ClusterManager.getInstance().isClientPresent(req.getHeader().getClientId())) {
						ConnectionManager.broadcastToClient(req, req.getHeader().getClientId());
					} else if(ElectionManager.getInstance().isLeaderAlive() && ElectionManager.getInstance().getLeaderNode() == ClusterManager.getInstance().getServerConf().getNodeId()) {
						//If I am the leader and client is not connected to me, broad cast to all nodes
						//Drawback - for intra-cluster, server will broadcast to all nodes and client will again broad cast to all nodes resulting in duplicates!
						//Need to somehow check if this message is intra or inter - maybe have a separate queue.
						//For inter - broad cast to all nodes
						//For intra - do nothing! 
						// Maybe have a separate queue. For now, just have the duplicates!
						ConnectionManager.broadcastImgClusters(req);
					}
 
					
					
				} else if (!req.getPing().getIsPing() && req.getHeader().getClusterId() != ClusterManager.getInstance().getServerConf().getClusterId()) {
					//Case 3: Received image intended for another cluster. If I am the leader send it to that cluster.
					//Else send it to the leader, if i am not the leader.
					
					//if we are the leader
					//Send it to all other clusters
					if(ElectionManager.getInstance().isLeaderAlive() && ElectionManager.getInstance().getLeaderNode() == ClusterManager.getInstance().getServerConf().getNodeId()) {
						//I am the leader - send it to the destination cluster
						ConnectionManager.broadcastToRemoteCluster(req, req.getHeader().getClusterId());
					} else if(ElectionManager.getInstance().isLeaderAlive()) {
						//Send it to the leader
						//ConnectionManager.broadcastImgClusters(req,ElectionManager.getInstance().getLeaderNode());
						//Not necessary since the originating server already sent it to the leader
					} else {
						//Leader is not alive - discard the message
					}
				}

				/*if(req.hasPayload())
				{
					String dirName="F:\\";
				    logger.info("Received payload ahoy!");  
					PayLoad payload = req.getPayload();
					
				      ByteString imageByteString = payload.getData();
				      byte[] bytearray = imageByteString.toByteArray();
				      
				      
				      BufferedImage imag=ImageIO.read(new ByteArrayInputStream(bytearray));
						ImageIO.write(imag, "jpg", new File(dirName,"roshan.jpg"));
				      
				}*/

			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected processing failure, halting worker.", e);
				break;
			}
		}

		if (!forever) {
			logger.info("connection queue closing");
		}
	}

	private void sendPingtoClusterLeader(int clusterId) {
		ClusterManager.getInstance().sendPingToKnownLeaders(clusterId);
		
	}
}
