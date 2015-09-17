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
import poke.cluster.Image.Header;
import poke.cluster.Image.PayLoad;
import poke.cluster.Image.Ping;
import poke.cluster.Image.Request;
import poke.core.Mgmt.LeaderElection.ElectAction;

import java.awt.image.BufferedImage;
import java.beans.Beans;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.core.Mgmt.LeaderElection;
import poke.core.Mgmt.LeaderElection.ElectAction;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.VectorClock;
import poke.server.conf.ServerConf;
import poke.server.election.Election;
import poke.server.election.ElectionListener;
import poke.server.election.FloodMaxElection;
import poke.server.election.RAFTElection;
import poke.server.managers.ConnectionManager;

/**
 * The election manager is used to determine leadership within the network. The
 * leader is not always a central point in which decisions are passed. For
 * instance, a leader can be used to break ties or act as a scheduling dispatch.
 * However, the dependency on a leader in a decentralized design (or any design,
 * matter of fact) can lead to bottlenecks during heavy, peak loads.
 * 
 * TODO An election is a special case of voting. We should refactor to use only
 * voting.
 * 
 * QUESTIONS:
 * 
 * Can we look to the PAXOS alg. (see PAXOS Made Simple, Lamport, 2001) to model
 * our behavior where we have no single point of failure; each node within the
 * network can act as a consensus requester and coordinator for localized
 * decisions? One can envision this as all nodes accept jobs, and all nodes
 * request from the network whether to accept or reject the request/job.
 * 
 * Does a 'random walk' approach to consistent data replication work?
 * 
 * What use cases do we would want a central coordinator vs. a consensus
 * building model? How does this affect liveliness?
 * 
 * Notes:
 * <ul>
 * <li>Communication: the communication (channel) established by the heartbeat
 * manager is used by the election manager to communicate elections. This
 * creates a constraint that in order for internal (mgmt) tasks to be sent to
 * other nodes, the heartbeat must have already created the channel.
 * </ul>
 * 
 * @author gash
 * 
 */
public class ElectionManager implements ElectionListener {
	protected static Logger logger = LoggerFactory.getLogger("election");
	protected static AtomicReference<ElectionManager> instance = new AtomicReference<ElectionManager>();

	private static ServerConf conf;

	// number of times we try to get the leader when a node starts up
	private int firstTime = 1;

	/** The election that is in progress - only ONE! */
	private Election election;
	private int electionCycle = -1;
	private Integer syncPt = 1;

	/** The leader */
	private Integer leaderNode;
	
	//NISHANTH: Newly added for maintaining the election  
		private static String NodeState="follower";
		
		public static String getNodeState()
		{
			return NodeState;
		}

		public static void setNodeStateFollower()
		{
			NodeState="follower";
		}
		public void setNodeStateLeader()
		{
			NodeState="leader";
		}
		public void setNodeStateCandidate()
		{
			NodeState="candidate";
		}

		
	public boolean isLeaderAlive(Management mgmt) {
		if(leaderNode == null && (election == null || !election.isElectionInprogress())) 
			return false;
		else 
			return true;
	}
	
	public boolean isLeaderAlive() {
		if(leaderNode == null && (election == null || !election.isElectionInprogress())) 
			return false;
		else 
			return true;
	}
	
	public void setLeader(Integer lNode) {
		this.leaderNode = lNode;
	}
	
	public static ElectionManager initManager(ServerConf conf) {
		ElectionManager.conf = conf;
		instance.compareAndSet(null, new ElectionManager());
		setNodeStateFollower();
		logger.info(" Thus  I am in: " +getNodeState()+" state");
		return instance.get();
	}

	/**
	 * Access a consistent instance for the life of the process.
	 * 
	 * TODO do we need to have a singleton for this? What happens when a process
	 * acts on behalf of separate interests?
	 * 
	 * @return
	 */
	public static ElectionManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}
	public Integer getLeaderNode(){
		return this.leaderNode;
	}

	public Integer setLeaderNode(){
		this.leaderNode= null;
		return this.leaderNode;
	}

	public  int setFirstTime(){
		this.firstTime= 2;
		return this.firstTime;
	}


	/**
	 * returns the leader of the network
	 * 
	 * @return
	 */
	public Integer whoIsTheLeader() {
		return this.leaderNode;
	}

	/**
	 * initiate an election from within the server - most likely scenario is the
	 * heart beat manager detects a failure of the leader and calls this method.
	 * 
	 * Depending upon the algo. used (bully, flood, lcr, hs) the
	 * manager.startElection() will create the algo class instance and forward
	 * processing to it. If there was an election in progress and the election
	 * ID is not the same, the new election will supplant the current (older)
	 * election. This should only be triggered from nodes that share an edge
	 * with the leader.
	 */
	public void startElection() {
		electionCycle = electionInstance().createElectionID();
		
		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(electionCycle);
		elb.setAction(ElectAction.DECLAREELECTION);
		elb.setDesc("Node " + conf.getNodeId() + " detects no leader. Election!");
		elb.setCandidateId(conf.getNodeId()); // promote self
		setNodeStateCandidate();
		logger.info("Updated the state to:"+getNodeState());

		elb.setExpires(2 * 60 * 1000 + System.currentTimeMillis()); // 1 minute

		// bias the voting with my number of votes (for algos that use vote
		// counting)

		// TODO use voting int votes = conf.getNumberOfElectionVotes();

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		// now send it out to all my edges
		logger.info("Election started by node " + conf.getNodeId());
		electionInstance().process(mb.build());
		ConnectionManager.broadcast(mb.build());
	}

	/**
	 * @param args
	 */
	public void processRequest(Management mgmt) {
		if (!mgmt.hasElection())
			return;

		LeaderElection req = mgmt.getElection();

		// when a new node joins the network it will want to know who the leader
		// is - we kind of ram this request-response in the process request
		// though there has to be a better place for it
		if (req.getAction().getNumber() == LeaderElection.ElectAction.WHOISTHELEADER_VALUE) {
			respondToWhoIsTheLeader(mgmt);
			return;
		} else if (req.getAction().getNumber() == LeaderElection.ElectAction.THELEADERIS_VALUE) {
			logger.info("Node " + conf.getNodeId() + " got an answer on who the leader is. Its Node "
					+ req.getCandidateId());
			this.leaderNode = req.getCandidateId();
			setNodeStateFollower();
			logger.info(" Thus  I am in: " +getNodeState()+" state");
			return;
		}

		// else fall through to an election

		if (req.hasExpires()) {
			long ct = System.currentTimeMillis();
			if (ct > req.getExpires()) {
				// ran out of time so the election is over
				election.clear();
				return;
			}
		}

		Management rtn = electionInstance().process(mgmt);
		if (rtn != null) {
			if(rtn.getElection().getAction().getNumber() == ElectAction.DECLAREWINNER_VALUE) {
				setNodeStateLeader();
				logger.info(" Updated the state to:"+getNodeState());

				ConnectionManager.broadcast(rtn);
			} else {
				ConnectionManager.broadcast(rtn,rtn.getHeader().getOriginator());
			}
		}
			
	}

	/**
	 * check the health of the leader (usually called after a HB update)
	 * 
	 * @param mgmt
	 */
	public void assessCurrentState(Management mgmt) {
		// logger.info("ElectionManager.assessCurrentState() checking elected leader status");
		if (firstTime > 0 && ConnectionManager.getNumMgmtConnections() > 0) {
			// give it two tries to get the leader
			this.firstTime--;
			askWhoIsTheLeader();
/*		if (firstTime > 0 && ConnectionManager.getNumMgmtConnections() > 0) {
			// give it two tries to get the leader
			this.firstTime--;
			askWhoIsTheLeader();*/
		} else if (leaderNode == null && (election == null || !election.isElectionInprogress())) {
			// if this is not an election state, we need to assess the H&S of
			// the network's leader
			synchronized (syncPt) {
				startElection();
			}
		}
	}
	
	public void assessCurrentState() {
		// logger.info("ElectionManager.assessCurrentState() checking elected leader status");
		if (firstTime > 0 && ConnectionManager.getNumMgmtConnections() > 0) {
			// give it two tries to get the leader
			this.firstTime--;
			askWhoIsTheLeader();
/*		if (firstTime > 0 && ConnectionManager.getNumMgmtConnections() > 0) {
			// give it two tries to get the leader
			this.firstTime--;
			askWhoIsTheLeader();*/
		} else if (leaderNode == null && (election == null || !election.isElectionInprogress()) && ConnectionManager.getNumMgmtConnections() > 0) {
			// if this is not an election state, we need to assess the H&S of
			// the network's leader
			synchronized (syncPt) {
				startElection();
			}
		}
	}

	/** election listener implementation */
	@Override
	public void concludeWith(boolean success, Integer leaderID) {
		if (success) {
			logger.info("----> the leader is " + leaderID);
			this.leaderNode = leaderID;
			
			/*String dirName="F:\\";
			ByteArrayOutputStream baos=new ByteArrayOutputStream(1000);
			BufferedImage img;
			byte[] bytearray=null;
			try {
				img = ImageIO.read(new File(dirName,"darknight.jpg"));
				ImageIO.write(img, "jpg", baos);
				baos.flush();

				 bytearray=  baos.toByteArray();
				baos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			Header.Builder header = Header.newBuilder();
		  header.setClientId(2);
		  header.setClusterId(2);
		  header.setIsClient(true);
		  header.setCaption("darknight");
		  
		  PayLoad.Builder payloadBuilder = PayLoad.newBuilder();
		  payloadBuilder.setData(ByteString.copyFrom(bytearray));
		  
		  Ping.Builder pingBuilder = Ping.newBuilder();
		  pingBuilder.setIsPing(false);
		  
			
			//Management.Builder pb = Management.newBuilder();
			Request.Builder reqBuilder = Request.newBuilder();
		    reqBuilder.setHeader(header.build());
		    reqBuilder.setPayload(payloadBuilder.build());
		    reqBuilder.setPing(pingBuilder.build());
			
			//Management result= pb.build();
			System.out.println("BROADCAST START");
			ConnectionManager.broadcastImgClusters(reqBuilder.build(),1);
			System.out.println("BROADCAST END");*/
		}

		election.clear();
	}

	private void respondToWhoIsTheLeader(Management mgmt) {
		if (this.leaderNode == null) {
			logger.info("----> I cannot respond to who the leader is! I don't know!");
			return;
		}

		logger.info("Node " + conf.getNodeId() + " is replying to " + mgmt.getHeader().getOriginator()
				+ "'s request who the leader is. Its Node " + this.leaderNode);

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(electionCycle);
		elb.setAction(ElectAction.THELEADERIS);
		elb.setDesc("Node " + this.leaderNode + " is the leader");
		elb.setCandidateId(this.leaderNode);
		elb.setExpires(-1);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());
/*
		// now send it to the requester
		logger.info("Election started by node " + conf.getNodeId());*/
		try {

			ConnectionManager.getConnection(mgmt.getHeader().getOriginator(), true).write(mb.build());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void askWhoIsTheLeader() {
		logger.info("Node " + conf.getNodeId() + " is searching for the leader");

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(-1);
		elb.setAction(ElectAction.WHOISTHELEADER);
		elb.setDesc("Node " + this.leaderNode + " is asking who the leader is");
		elb.setCandidateId(-1);
		elb.setExpires(-1);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		// now send it to the requester
		ConnectionManager.broadcast(mb.build());
	}

	private Election electionInstance() {
		if (election == null) {
			synchronized (syncPt) {
				if (election !=null)
					return election;
				
				// new election
				String clazz = ElectionManager.conf.getElectionImplementation();

				// if an election instance already existed, this would
				// override the current election
				try {
					election = (Election) Beans.instantiate(this.getClass().getClassLoader(), clazz);
					election.setNodeId(conf.getNodeId());
					election.setListener(this);
					
					// this sucks - bad coding here! should use configuration
					// properties
					/*if (election instanceof FloodMaxElection) {
						logger.warn("Node " + conf.getNodeId() + " setting max hops to arbitrary value (4)");
						((FloodMaxElection) election).setMaxHops(4);
					} */
					
					//Test invocation of RAFT
					// Added new invocation call -TODO yet to set the required variables  
					if (election instanceof RAFTElection) {
							logger.warn("Node " + conf.getNodeId() + " No need for HOPS in RAFT");
							
					}

				} catch (Exception e) {
					logger.error("Failed to create " + clazz, e);
				}
			}
		}

		return election;

	}
}
