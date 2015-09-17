package poke.server.cluster;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message;

import poke.cluster.Image;
import poke.comm.App;
import poke.core.Mgmt.Management;
import poke.server.management.ManagementQueue;

public class GenericHandler extends SimpleChannelInboundHandler<Message> {
	protected static Logger logger = LoggerFactory.getLogger("MessageGenericHandler");

	public GenericHandler() {
		// logger.info("** HeartbeatHandler created **");
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Message req)/* throws Exception*/ {
		// processing is deferred to the worker threads
		logger.info("Recieved a Message");
		try {
			if(req instanceof Image.Request) {
				ClusterQueue.enqueueRequest((Image.Request)req, ctx.channel());
				logger.info("Queued into Cluster Queue");
			} else if(req instanceof Management) {
				ManagementQueue.enqueueRequest((Management)req, ctx.channel());
				logger.info("Queued into Management Queue");
			} else if(req instanceof App.Request) {
				//No queue in plae yet
				logger.info("Queued into App Queue");
			}
			}catch(Exception e) {
				logger.info("Exception:"+e.getMessage());
			}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		logger.error("Channel inactive: "+ctx.name());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

	/**
	 * usage:
	 * 
	 * <pre>
	 * channel.getCloseFuture().addListener(new ManagementClosedListener(queue));
	 * </pre>
	 * 
	 * @author gash
	 * 
	 */
	public static class ClusterClosedListener implements ChannelFutureListener {
		// private ManagementQueue sq;

		public ClusterClosedListener(ClusterQueue sq) {
			// this.sq = sq;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			// if (sq != null)
			// sq.shutdown(true);
			// sq = null;
		}

	}
}
