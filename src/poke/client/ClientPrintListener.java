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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;

import poke.client.comm.CommListener;
import poke.client.util.ClientUtil;
import poke.cluster.Image.PayLoad;
//import poke.comm.App.Header;
//import poke.comm.App.Request;
import poke.cluster.Image.Request;

/**
 * example listener that an application would use to receive events.
 * 
 * @author gash
 * 
 */
public class ClientPrintListener implements CommListener {
	protected static Logger logger = LoggerFactory.getLogger("connect");

	private String id;

	public ClientPrintListener(String id) {
		this.id = id;
	}

	@Override
	public String getListenerID() {
		return id;
	}

	@Override
	public void onMessage(GeneratedMessage msg5) {
		logger.info("Received message in ClientPrintlistener");
		Request msg = (Request) msg5;
		
		if(msg.hasPayload())
		{
			String dirName="F:\\";
		    logger.info("Received payload ahoy!");  
			PayLoad payload = msg.getPayload();
			
		      ByteString imageByteString = payload.getData();
		      byte[] bytearray = imageByteString.toByteArray();
		      
		      
		      BufferedImage imag;
			try {
				imag = ImageIO.read(new ByteArrayInputStream(bytearray));
				ImageIO.write(imag, "jpg", new File(dirName,"roshan.jpg"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
				
		      
		}
	}

}
