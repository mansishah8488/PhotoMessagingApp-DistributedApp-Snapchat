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
package poke.resources;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import poke.comm.App.Payload;
import poke.comm.App.Ping;
import poke.comm.App.PokeStatus;
import poke.comm.App.Request;
import poke.server.resources.Resource;
import poke.server.resources.ResourceUtil;

public class PingResource implements Resource {
	protected static Logger logger = LoggerFactory.getLogger("server");
	int cluster_id;
	boolean is_client;
	String caption;
	String md_5;
	int clientId;
	
	public int getClusterId()
	{
		return this.cluster_id;
	}
	public int getClientId()
	{
		return this.clientId;
	}
	public boolean getIsClient()
	{
		return this.is_client;
	}
	public String getCaption()
	{
		return this.caption;
	}
	public String getMd5()
	{
		return this.md_5;
	}
	
	public PingResource() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see poke.server.resources.Resource#process(eye.Comm.Finger)
	 */
	public Request process(Request request) {
		// TODO add code to process the message/event received
		
		if((request.getBody().getPing().getTag())!=null)
		{
			String dirName="/home/nishanth/Desktop/";
		      
		      ByteString imageByteString = request.getBody().getPing().getTag();
		      cluster_id = request.getBody().getPing().getClusterId();
		      is_client = request.getBody().getPing().getIsClient();
		      caption= request.getBody().getPing().getCaption();
		      md_5= request.getBody().getPing().getMd5();
		      clientId= request.getBody().getPing().getClientId();
		      
		      byte[] bytearray = imageByteString.toByteArray();
		      caption=caption+".jpg";
		      
		      BufferedImage imag;
			try {
				imag = ImageIO.read(new ByteArrayInputStream(bytearray));
				ImageIO.write(imag, "jpg", new File(dirName,caption));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		      
		}
		else
			logger.error("Unknown management message");
		
		
		
		logger.info("Ping resource got an image from client: " + getClientId()+"from the cluster: "+getClusterId());

		Request.Builder rb = Request.newBuilder();

		// metadata
		rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), PokeStatus.SUCCESS, null));

		// payload
		Payload.Builder pb = Payload.newBuilder();
		Ping.Builder fb = Ping.newBuilder();
		fb.setTag(request.getBody().getPing().getTag());
		fb.setNumber(request.getBody().getPing().getNumber());
		
		fb.setClientId(clientId);
		fb.setClusterId(cluster_id);
		fb.setIsClient(false);
		fb.setCaption(caption);
		fb.setMd5(md_5);
		
		pb.setPing(fb.build());
		rb.setBody(pb.build());

		Request reply = rb.build();

		return reply;
	}
}
