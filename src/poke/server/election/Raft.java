package poke.server.election;

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

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.LeaderElection;
import poke.core.Mgmt.LeaderElection.ElectAction;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.VectorClock;

/**
 * RAFT algo is useful for cases where a ring is not formed (e.g.,
 * tree) and the organization is not ordered as in algorithms such as HS or LCR.
 * 
 * 
 * Limitations: This is rather a simple (naive) implementation as it 1) assumes
 * a simple network, and 2) does not support the notion of diameter of the graph
 * (therefore, is not deterministic!). What this means is the choice of maxHops
 * cannot ensure we reach agreement.
 * 
 * Typically, a FM uses the diameter of the network to determine how many
 * iterations are needed to cover the graph. This approach can be shortened if a
 * cheat list is used to know the nodes of the graph. A lookup (cheat) list for
 * small networks is acceptable so long as the membership of nodes is relatively
 * static. For large communities, use of super-nodes can reduce propagation of
 * messages and reduce election time.
 * 
 * Alternate choices can include building a spanning tree of the network (each
 * node know must know this and for arbitrarily large networks, this is not
 * feasible) to know when a round has completed. Another choice is to wait for
 * child nodes to reply before broadcasting the next round. This waiting is in
 * effect a blocking (sync) communication. Therefore, does not really give us
 * true asynchronous behavior.
 * 
 * Is best-effort, non-deterministic behavior the best we can achieve?
 * 
 * @author gash
 * 
 */

public class Raft implements Election {

	protected static Logger logger = LoggerFactory.getLogger("Raft");

	private Integer nodeId;
	private ElectionState current;
	
	private ElectionListener listener;
	private int dummy_term;

	public Raft() {
		//As the name suggests its a dummy term. Actual term is to be obtained from the 
		//mgmt.proto file. TODO Update the mgmt.proto file to include term field
		dummy_term=4;
		logger.info(" Inside Raft ! ");
		

	}
	
	public void setListener(ElectionListener listener)
	{
		this.listener = listener;
	}
	
	public void clear()
	{
		
	}

	public boolean isElectionInprogress()
	{
		return false;
		
	}
	
	public Integer getElectionId()
	{
		return 1;
	}
	
	public Integer createElectionID()
	{
		return 1;
	}
	
	public Integer getWinner()
	{
		return 1;
	}
	
	private boolean updateCurrent(LeaderElection req) {
		boolean isNew = false;

		if (current == null) {
			current = new ElectionState();
			isNew = true;
		}

		current.electionID = req.getElectId();
		current.candidate = req.getCandidateId();
		current.desc = req.getDesc();
		current.maxDuration = req.getExpires();
		current.startedOn = System.currentTimeMillis();
		current.state = req.getAction();
		current.id = -1; // TODO me or sender?
		current.active = true;

		return isNew;
	}
	
	private void notify(boolean success, Integer leader) {
		if (listener != null)
			listener.concludeWith(success, leader);
	}

	
	//Central process pertaining to leader election
	@Override
	public Management process(Management mgmt) {
		if (!mgmt.hasElection())
			return null;

		LeaderElection req = mgmt.getElection();
		if (req.getExpires() <= System.currentTimeMillis()) {
			// election has expired without a conclusion?
		}

		Management rtn = null;

		if (req.getAction().getNumber() == ElectAction.DECLAREELECTION_VALUE) {
			// an election is declared!

			// required to eliminate duplicate messages - on a declaration,
			// should not happen if the network does not have cycles
			List<VectorClock> rtes = mgmt.getHeader().getPathList();
			for (VectorClock rp : rtes) {
				if (rp.getNodeId() == this.nodeId) {
					// message has already been sent to me, don't use and
					// forward
					return null;
				}
			}

			// I got here because the election is unknown to me

			// this 'if debug is on' should cover the below dozen or so
			// println()s. It is here to help with seeing when an election
			// occurs.
			
			System.out.println("\n\n*********************************************************");
			System.out.println(" RAFT ELECTION: Election declared");
			System.out.println("   Election ID:  " + req.getElectId());
			System.out.println("   Rcv from:     Node " + mgmt.getHeader().getOriginator());
			System.out.println("   Expires:      " + new Date(req.getExpires()));
			System.out.println("   Nominates:    Node " + req.getCandidateId());
			System.out.println("   Desc:         " + req.getDesc());
			System.out.print("   Routing tbl:  [");
			for (VectorClock rp : rtes)
				System.out.print("Node " + rp.getNodeId() + " (" + rp.getVersion() + "," + rp.getTime() + "), ");
			System.out.println("]");
			System.out.println("*********************************************************\n\n");

			// sync master IDs to current election
			ElectionIDGenerator.setMasterID(req.getElectId());

			/**
			 * a new election can be declared over an existing election.
			 * 
			 * TODO need to have an monotonically increasing ID that we can test
			 */
			
			boolean isNew = updateCurrent(req);
			rtn = castVote(mgmt, isNew);

		} else if (req.getAction().getNumber() == ElectAction.DECLAREVOID_VALUE) {
			// no one was elected, I am dropping into standby mode
			logger.info("TODO: no one was elected, I am dropping into standby mode");
			this.clear();
			notify(false, null);
		} else if (req.getAction().getNumber() == ElectAction.DECLAREWINNER_VALUE) {
			// some node declared itself the leader
			logger.info("Election " + req.getElectId() + ": Node " + req.getCandidateId() + " is declared the leader");
			updateCurrent(mgmt.getElection());
			current.active = false; // it's over
			notify(true, req.getCandidateId());
		} else if (req.getAction().getNumber() == ElectAction.ABSTAIN_VALUE) {
			// for some reason, a node declines to vote - therefore, do nothing
		} else if (req.getAction().getNumber() == ElectAction.NOMINATE_VALUE) {
			boolean isNew = updateCurrent(mgmt.getElection());
			rtn = castVote(mgmt, isNew);
		} else {
			// this is me!
		}

		return rtn;
	}

	private synchronized Management castVote(Management mgmt, boolean isNew)
	{
		return null;
	}
	@Override
	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}
}
