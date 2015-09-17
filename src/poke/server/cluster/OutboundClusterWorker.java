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

import io.netty.channel.ChannelFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poke.cluster.Image.Request;
import poke.server.cluster.ClusterQueue.ClusterQueueEntry;


public class OutboundClusterWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("cluster");

	int workerId;
	boolean forever = true;

	public OutboundClusterWorker(ThreadGroup tgrp, int workerId) {
		super(tgrp, "outbound-cluster-" + workerId);
		logger.info("OutboundClusterWorker initialized");
		this.workerId = workerId;

		if (ClusterQueue.outbound == null)
			throw new RuntimeException("cluster worker detected null queue");
	}

	@Override
	public void run() {
		while (true) {
			if (!forever && ClusterQueue.outbound.size() == 0)
				break;

			try {
				// block until a message is enqueued
				ClusterQueueEntry msg = ClusterQueue.outbound.take();

				if (logger.isDebugEnabled())
					logger.debug("Outbound cluster message routing to cluster " + msg.req.getHeader().getClusterId() + " and node"+msg.req.getHeader().getClientId());

				if (msg.channel.isWritable()) {
					boolean rtn = false;
					if (msg.channel != null && msg.channel.isOpen() && msg.channel.isWritable()) {
						ChannelFuture cf = msg.channel.write(msg);

						// blocks on write - use listener to be async
						cf.awaitUninterruptibly();
						rtn = cf.isSuccess();
						if (!rtn)
							ClusterQueue.outbound.putFirst(msg);
					}

				} else {
					logger.info("channel to cluster " + msg.req.getHeader().getClusterId() + " & node "+msg.req.getHeader().getClientId()+" is not writable");
					ClusterQueue.outbound.putFirst(msg);
				}
			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected cluster communcation failure", e);
				break;
			}
		}

		if (!forever) {
			logger.info("cluster outbound queue closing");
		}
	}

}
