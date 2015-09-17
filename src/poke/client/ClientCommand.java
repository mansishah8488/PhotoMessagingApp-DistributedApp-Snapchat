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
package poke.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.client.comm.CommConnection;
import poke.client.comm.CommListener;
import poke.comm.App.Header;
import poke.comm.App.Payload;
import poke.comm.App.Ping;
import poke.comm.App.Request;

import java.awt.image.BufferedImage;
import java.beans.Beans;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * The command class is the concrete implementation of the functionality of our
 * network. One can view this as a interface or facade that has a one-to-one
 * implementation of the application to the underlining communication.
 * 
 * IN OTHER WORDS (pay attention): One method per functional behavior!
 * 
 * @author gash
 * 
 */
public class ClientCommand {
	protected static Logger logger = LoggerFactory.getLogger("ClientCommand");

	private String host;
	private int port;
	private CommConnection comm;
	private String clientId;

	String[] EXTENSIONS = new String[] {
		"jpg", "png", "JPG", "PNG", "jpeg", "JPEG", "jfif", "JFIF", "exif", "EXIF", "tiff", "TIFF", "gif", "GIF", "bmp", "BMP"
	};

	FilenameFilter IMAGE_FILTER = new FilenameFilter() {

		@Override
		public boolean accept(final File dir, final String name) {
			for (final String ext: EXTENSIONS) {
				if (name.endsWith("." + ext)) {
					return (true);
				}
			}
			return (false);
		}
	};

	public ClientCommand(String host, int port, String clientId) {
		this.host = host;
		this.port = port;
		this.clientId = clientId;

		init();
		identifyThySelf(clientId);
	}

	private void identifyThySelf(String clientId) {
		Ping.Builder f = Ping.newBuilder();
		byte[] arr = new byte[10];
		f.setTag(ByteString.copyFrom(arr));
		f.setNumber(0); //0 is used to identify that it is identifying itself
		f.setClientId(Integer.parseInt(clientId));
		f.setClusterId(port);
		f.setIsClient(true);
		f.setCaption(host);


		// payload containing data
		Request.Builder r = Request.newBuilder();
		Payload.Builder p = Payload.newBuilder();
		p.setPing(f.build());
		r.setBody(p.build());

		// header with routing info
		Header.Builder h = Header.newBuilder();
		h.setOriginator(1000);
		h.setTime(System.currentTimeMillis());
		h.setRoutingId(Header.Routing.PING);
		r.setHeader(h.build());

		Request req = r.build();

		try {
			comm.sendMessage(req);
			logger.info("Identified thyself with server: "+host+":"+port);
		} catch (Exception e) {
			logger.warn("Unable to identify thyself! The world is ending! I'm useless!");
		}

	}

	private void init() {
		comm = new CommConnection(host, port);
		MonitorFiles mf = new MonitorFiles(comm, clientId);
		mf.start();
	}

	/**
	 * add an application-level listener to receive messages from the server (as
	 * in replies to requests).
	 * 
	 * @param listener
	 */
	public void addListener(CommListener listener) {
		comm.addListener(listener);
		comm.start();
	}

	public class MonitorFiles extends Thread {
		private CommConnection comm;
		private String clientId;
		protected Logger logger = LoggerFactory.getLogger("MonitorFiles");

		String[] EXTENSIONS = new String[] {
				"jpg", "png", "JPG", "PNG", "jpeg", "JPEG", "jfif", "JFIF", "exif", "EXIF", "tiff", "TIFF", "gif", "GIF", "bmp", "BMP"
		};

		FilenameFilter IMAGE_FILTER = new FilenameFilter() {

			@Override
			public boolean accept(final File dir, final String name) {
				for (final String ext: EXTENSIONS) {
					if (name.endsWith("." + ext)) {
						return (true);
					}
				}
				return (false);
			}
		};

		public MonitorFiles(CommConnection comm, String clientId) {
			this.comm = comm;
			this.clientId = clientId;
		}

		public void run() {
			logger.info("Monitoring directory for outbound messages");
			
			while (true) {
				String dirName = "outbox";
				ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);

				int cluster_id = 5;
				String clientId = "";
				boolean is_client = true;
				String caption = "";
				String md_5 = "";
				
				File dir = new File(dirName);

				for(final File file: dir.listFiles(IMAGE_FILTER)) {
					String[] destDetails = file.getName().split("_");
					if(destDetails.length < 3) {
						logger.info("Incorrect filename. Ignoring file: "+file.getName());
					} else {
						

						logger.info("Processing outbound file: " + file.getName());

						byte[] bytearray = null;
						try {
							cluster_id = Integer.parseInt(destDetails[0]);
							clientId = destDetails[1];
							caption = destDetails[2];
							BufferedImage img = null;
							img = ImageIO.read(file);

							ImageIO.write(img, "jpg", baos);
							baos.flush();

							bytearray = baos.toByteArray();
							baos.close();
							file.delete();
						} catch (IOException e) {
							logger.info(e.getMessage());
						} catch(Exception e) {
							logger.info(e.getMessage());
						}
						Ping.Builder f = Ping.newBuilder();
						f.setTag(ByteString.copyFrom(bytearray));
						f.setNumber(1);
						f.setClientId(Integer.parseInt(clientId));
						f.setClusterId(cluster_id);
						f.setIsClient(is_client);
						f.setCaption(caption);
						f.setMd5(md_5);

						// payload containing data
						Request.Builder r = Request.newBuilder();
						Payload.Builder p = Payload.newBuilder();
						p.setPing(f.build());
						r.setBody(p.build());

						// header with routing info
						Header.Builder h = Header.newBuilder();
						h.setOriginator(1000);
						h.setTime(System.currentTimeMillis());
						h.setRoutingId(Header.Routing.PING);
						r.setHeader(h.build());

						Request req = r.build();

						try {
							comm.sendMessage(req);
						} catch (Exception e) {
							logger.warn("Unable to deliver message, queuing");
						}
					} //End of else
					
				}

			}
		} //End of run method
	}
}