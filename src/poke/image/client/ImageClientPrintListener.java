package poke.image.client;

import com.google.protobuf.GeneratedMessage;

import poke.client.comm.CommListener;
import poke.comm.App.Request;

public class ImageClientPrintListener implements CommListener {

	@Override
	public String getListenerID() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onMessage(GeneratedMessage msg) {
		// TODO Auto-generated method stub

	}

	/*@Override
	public void onMessage(poke.cluster.Image.Request msg) {
		// TODO Auto-generated method stub

	}*/

}
