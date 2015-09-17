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
package poke.image.client;

import poke.client.ClientCommand;
import poke.client.ClientPrintListener;
import poke.client.comm.CommListener;

/**
 * Incomplete code. Dont use it yet !
 * 
 * 
 * 
 */
public class ImageClientInvoke {
	private String tag;
	private int count;

	public ImageClientInvoke(String tag) {
		this.tag = tag;
	}

	public void run() {
		ImageClientCommand cc = new ImageClientCommand("localhost", 5570);
	//	CommListener listener = new ImageClientPrintListener(" Image Client");
	//	cc.addListener(listener);
		cc.poke(tag, count);

	}

	public static void main(String[] args) {
		try {
			ImageClientInvoke CI = new ImageClientInvoke("jab");
			CI.run();

			// we are running asynchronously
			// NOTE that the images that need to be uploaded is provided 
			// with in the client command
			
			System.out.println(" The client will recieve the initiation");
			System.out.println("\nExiting in 5 seconds");
			Thread.sleep(5000);
			System.exit(0);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
