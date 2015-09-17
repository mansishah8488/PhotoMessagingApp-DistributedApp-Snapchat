package poke.server.cluster;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.Network;
import poke.core.Mgmt.Network.NetworkAction;
import poke.server.management.ManagementInitializer;
import poke.server.managers.ConnectionManager;

/**
 * The monitor is a client-side component that can exist as as its own client or
 * as an internal component to a server. Its purpose is to process responses
 * from server management messages/responses - heartbeats (HB).
 * 
 * It is conceivable to create a separate application/process that listens to
 * the network. However, one must consider the HB (management port) is more for
 * localized communication through the overlay network and not as a tool for
 * overall health of the network. For an external monitoring device, a UDP-based
 * communication is more appropriate.
 * 
 * @author gash
 * 
 */
public class ClusterMonitor {
	protected static Logger logger = LoggerFactory.getLogger("mgmt");

	protected ChannelFuture channel; // do not use directly, call connect()!
	private EventLoopGroup group;

	private static int N = 0; // unique identifier
	private String whoami;
	private int toNodeId;
	private String host;
	private int port;

	// this list is only used if the connection cannot be established - it holds
	// the listeners to be added.
	private List<ClusterListener> listeners = new ArrayList<ClusterListener>();

	/**
	 * Create a heartbeat message processor.
	 * 
	 * @param host
	 *            the hostname
	 * @param port
	 *            This is the management port
	 */
	public ClusterMonitor(String host, int port, int toNodeId) {
		this.toNodeId = toNodeId;
		this.host = host;
		this.port = port;
		this.group = new NioEventLoopGroup();

		logger.info("Creating cluster monitor for " + host + "(" + port + ")");
	}


	/**
	 * abstraction of notification in the communication
	 * 
	 * @param listener
	 */
	public void addListener(ClusterListener listener) {
	/*	if (handler == null && !listeners.contains(listener)) {
			listeners.add(listener);
			return;
		}

		try {
			handler.addListener(listener);
		} catch (Exception e) {
			logger.error("failed to add listener", e);
		}*/
	}

	public void release() {
		logger.warn("ClusterMonitor: releasing resources");
/*
		for (Integer id : handler.listeners.keySet()) {
			ClusterListener ml = handler.listeners.get(id);
			ml.connectionClosed();

			// hold back listeners to re-apply if the connection is
			// re-established.
			listeners.add(ml);
		}

		// TODO should wait a fixed time and use a listener to reset values;s
		channel = null;
		handler = null;*/
	}

	/**
	 * create connection to remote server
	 * 
	 * @return
	 */
	public Channel connect() {
		// Start the connection attempt.
		if (channel == null) {
			try {
				ClusterInitializer mi = new ClusterInitializer(false);

				Bootstrap b = new Bootstrap();
				// @TODO newFixedThreadPool(2);
				b.group(group).channel(NioSocketChannel.class).handler(mi);
				b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
				b.option(ChannelOption.TCP_NODELAY, true);
				b.option(ChannelOption.SO_KEEPALIVE, true);
				//b.handler(new ManagementInitializer(false));
				// Make the connection attempt.
				channel = b.connect(host, port).syncUninterruptibly();
				channel.awaitUninterruptibly(5000l);
				channel.channel().closeFuture().addListener(new ClusterClosedListener(this));

				if (N == Integer.MAX_VALUE)
					N = 1;
				else
					N++;
/*
				// add listeners waiting to be added
				if (listeners.size() > 0) {
					for (ClusterListener ml : listeners)
						handler.addListener(ml);
					listeners.clear();
				}*/
			} catch (Exception ex) {
				if (logger.isDebugEnabled())
					logger.debug("ClusterMonitor: failed to initialize the cluster node connection", ex);
				// logger.error("failed to initialize the heartbeat connection",
				// ex);
			}
		}

		if (channel != null && channel.isDone() && channel.isSuccess())
			return channel.channel();
		else
			throw new RuntimeException("Not able to establish connection to server");
	}

	public boolean isConnected() {
		if (channel == null)
			return false;
		else
			return channel.channel().isOpen();
	}

	public String getNodeInfo() {
		if (host != null)
			return host + ":" + port;
		else
			return "Unknown";
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	/**
	 * Called when the channel tied to the monitor closes. Usage:
	 * 
	 */
	public static class ClusterClosedListener implements ChannelFutureListener {
		private ClusterMonitor monitor;

		public ClusterClosedListener(ClusterMonitor monitor) {
			this.monitor = monitor;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			monitor.release();
			logger.info("Monitor is released - channel is closed?");
			//ConnectionManager.removeConnection(monitor.toNodeId, true);
		}
	}
}
