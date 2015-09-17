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
 * This is the client invoker, which sends the connection establishment to the server.
 */
public class ClientInvoke {
	private String id;
	private int count;

	public ClientInvoke(String id) {
		this.id = id;
	}

	public void run(String host, int port, String clientId) {
		ClientCommand cc = new ClientCommand(host, port, clientId);
		CommListener listener = new ClientPrintListener("Team awesome 5");
		cc.addListener(listener);
	}

	public static void main(String[] args) {
		try {
			/*
			 * Arguments:
			 * 0 - host 
			 * 1 - port
			 * 2 - client id
			 */
			
			if(args.length < 3) {
				System.out.println("Failed to start!");
				System.out.println("Required Arguments: <ServerHost> <ServerPort> <ClientId>");
				System.exit(0);
			}
			
			ClientInvoke CI = new ClientInvoke(args[2]);
			CI.run(args[0],new Integer(args[1]), args[2]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	} //End of main()
} //End of class ClientInvoke
