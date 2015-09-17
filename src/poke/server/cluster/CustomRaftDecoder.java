package poke.server.cluster;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poke.cluster.Image;
import poke.cluster.Image.Request;
import poke.comm.App;
import poke.core.Mgmt.Management;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.protobuf.ProtobufDecoder;

public class CustomRaftDecoder extends ProtobufDecoder  {
	protected static Logger logger = LoggerFactory.getLogger("Decoder");
	private static MessageLite prototype;
    private static final boolean HAS_PARSER = true;
    private Object[] messageTypes;
    
	public CustomRaftDecoder(Object[] messageTypes) {
		super((MessageLite) messageTypes[0]);
		this.prototype = (MessageLite) messageTypes[0];
		this.messageTypes = messageTypes;
	}
	
	@Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        final byte[] array;
        final int offset;
        final int length = msg.readableBytes();
        if (msg.hasArray()) {
            array = msg.array();
            offset = msg.arrayOffset() + msg.readerIndex();
        } else {
            array = new byte[length];
            msg.getBytes(msg.readerIndex(), array, 0, length);
            offset = 0;
        }

        if(HAS_PARSER) {
        	for(int i=0;i<messageTypes.length;i++) {
        		try {
            		prototype = (MessageLite) messageTypes[i];
            		out.add(prototype.getParserForType().parseFrom(array, offset, length));
            		break;
            	} catch(Exception e) {
            		logger.info("Finding suitable decoder for message");
            	}
        	} //End of while  	
        }
	} //End of decode()
}