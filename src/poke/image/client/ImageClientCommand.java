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
package poke.image.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.client.comm.CommConnection;
import poke.client.comm.CommListener;
import poke.cluster.Image.Header;
import poke.cluster.Image.PayLoad;
import poke.cluster.Image.Ping;
import poke.cluster.Image.Request;
import java.awt.image.BufferedImage;
import java.beans.Beans;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
public class ImageClientCommand {
	protected static Logger logger = LoggerFactory.getLogger("client");

	private String host;
	private int port;
	private CommConnection comm;

	public ImageClientCommand(String host, int port) {
		this.host = host;
		this.port = port;

		init();
	}

	private void init() {
		comm = new CommConnection(host, port);
	}

	/**
	 * add an application-level listener to receive messages from the server (as
	 * in replies to requests).
	 * 
	 * @param listener
	 */
	public void addListener(CommListener listener) {
		comm.addListener(listener);
	}

	/**
	 * Our network's equivalent to ping
	 * 
	 * @param tag
	 * @param num
	 */
	public void poke(String tag, int num) {
		// data to send
		String dirName="/home/mishanth/wallP";
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
	    
		Request req = reqBuilder.build();

		try {
			comm.sendMessage(req);
		} catch (Exception e) {
			logger.warn("Unable to deliver message, queuing");
		}
	}
}
