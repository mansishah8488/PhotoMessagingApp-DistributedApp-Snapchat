package poke.server.cluster;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.cluster.Image.Request;

public class ClusterHandler extends SimpleChannelInboundHandler<Request> {
	protected static Logger logger = LoggerFactory.getLogger("cluster");

	public ClusterHandler() {
		// logger.info("** HeartbeatHandler created **");
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Request req) throws Exception {
		// processing is deferred to the worker threads
		 logger.info("---> cluster got a message from cluster" + req.getHeader().getClusterId() + " & node "+req.getHeader().getClientId());
		ClusterQueue.enqueueRequest(req, ctx.channel());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		logger.error("cluster channel inactive");
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
