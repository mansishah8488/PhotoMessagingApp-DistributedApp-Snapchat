/*
 * copyright 2015, gash
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
package poke.server.queue;

import io.netty.channel.Channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.cluster.Image;
import poke.cluster.Image.Header;
import poke.cluster.Image.PayLoad;
import poke.comm.App.Payload;
import poke.comm.App.Ping;
import poke.comm.App.PokeStatus;
import poke.comm.App.Request;
import poke.server.managers.ClusterManager;
import poke.server.managers.ConnectionManager;
import poke.server.resources.Resource;
import poke.server.resources.ResourceFactory;
import poke.server.resources.ResourceUtil;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;

public class InboundAppWorker extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("server");

	int workerId;
	PerChannelQueue sq;
	boolean forever = true;

	public InboundAppWorker(ThreadGroup tgrp, int workerId, PerChannelQueue sq) {
		super(tgrp, "inbound-" + workerId);
		this.workerId = workerId;
		this.sq = sq;

		if (sq.inbound == null)
			throw new RuntimeException("connection worker detected null inbound queue");
	}

	@Override
	public void run() {
		Channel conn = sq.getChannel();
		if (conn == null || !conn.isOpen()) {
			logger.error("connection missing, no inbound communication");
			return;
		}

		while (true) {
			if (!forever && sq.inbound.size() == 0)
				break;

			try {
				// block until a message is enqueued
				GeneratedMessage msg = sq.inbound.take();
				System.out.println("INBOUND APP WORKER!");
				// process request and enqueue response
				if (msg instanceof Request) {
					Request req = ((Request) msg);

					// HEY! if you find yourself here and are tempted to add
					// code to process state or requests then you are in the
					// WRONG place! This is a general routing class, all
					// request specific actions should take place in the
					// resource!

					// handle it locally - we create a new resource per
					// request. This helps in thread isolation however, it
					// creates creation burdens on the server. If
					// we use a pool instead, we can gain some relief.

					if(req.hasBody())
					{
						//Payload p = req.getBody();
						if(req.getBody().hasPing())
						{
							Ping p = req.getBody().getPing();
							
							if(p.getNumber() ==0) {
								//client is identifying itself
								//ClusterManager.getInstance().addLocalClient(p.getClientId(),p.getCaption(),p.getClusterId())
								//Ignore these messages as they are already processed in PerChannelQueue;
							} else {
								//Check if destination client is connected to this node itself!
								if(ClusterManager.getInstance().isClientPresent(p.getClientId()) && ClusterManager.getInstance().getServerConf().getClusterId()==p.getClusterId()) {
									Header.Builder header = Header.newBuilder();
									  header.setClientId(p.getClientId());
									  header.setClusterId(p.getClusterId());
									  header.setIsClient(p.getIsClient());
									  header.setCaption(p.getCaption());
									  PayLoad.Builder payloadBuilder = PayLoad.newBuilder();
									  payloadBuilder.setData(p.getTag());
									  Image.Ping.Builder pingBuilder = Image.Ping.newBuilder();
									  pingBuilder.setIsPing(false);
										
										//Management.Builder pb = Management.newBuilder();
										Image.Request.Builder reqBuilder = Image.Request.newBuilder();
									    reqBuilder.setHeader(header.build());
									    reqBuilder.setPayload(payloadBuilder.build());
									    reqBuilder.setPing(pingBuilder.build());
									    try{
									    	//ConnectionManager.broadcastImgClusters(reqBuilder.build());
									ConnectionManager.broadcastToClient(reqBuilder.build(), p.getClientId());
									    } catch(Exception e) {
									    	logger.info(e.getMessage());
									    }
								} else {
									//else convert the message to Image.proto format and broadcast to all local nodes.
									System.out.println(p.getNumber());
									System.out.println(p.getMd5());
									System.out.println(p.getClientId());
									System.out.println(p.getClusterId());
									System.out.println(p.getIsClient());
									System.out.println(p.getCaption());
									
						
									Header.Builder header = Header.newBuilder();
									  header.setClientId(p.getClientId());
									  header.setClusterId(p.getClusterId());
									  header.setIsClient(p.getIsClient());
									  header.setCaption(p.getCaption());
									  
									  PayLoad.Builder payloadBuilder = PayLoad.newBuilder();
									  payloadBuilder.setData(p.getTag());
									  
									  Image.Ping.Builder pingBuilder = Image.Ping.newBuilder();
									  pingBuilder.setIsPing(false);
									  
										
										//Management.Builder pb = Management.newBuilder();
										Image.Request.Builder reqBuilder = Image.Request.newBuilder();
									    reqBuilder.setHeader(header.build());
									    reqBuilder.setPayload(payloadBuilder.build());
									    reqBuilder.setPing(pingBuilder.build());
										
										//Management result= pb.build();
										System.out.println("BROADCAST START");
										ConnectionManager.broadcastImgClusters(reqBuilder.build());
										System.out.println("BROADCAST END");

								}
															}
						}
					}
					
					
				/*	Resource rsc = ResourceFactory.getInstance().resourceInstance(req.getHeader());

					Request reply = null;
					if (rsc == null) {
						logger.error("failed to obtain resource for " + req);
						reply = ResourceUtil
								.buildError(req.getHeader(), PokeStatus.NORESOURCE, "Request not processed");
					} else {
						// message communication can be two-way or one-way.
						// One-way communication will not produce a response
						// (reply).
						reply = rsc.process(req);
					}

					if (reply != null)
						sq.enqueueResponse(reply, null);*/
				}

			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected processing failure", e);
				break;
			}
		}

		if (!forever) {
			logger.info("connection queue closing");
		}
	}
}