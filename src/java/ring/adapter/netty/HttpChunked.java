package ring.adapter.netty;

import static java.lang.Integer.toHexString;
import static org.jboss.netty.buffer.ChannelBuffers.BIG_ENDIAN;
import static org.jboss.netty.buffer.ChannelBuffers.dynamicBuffer;
import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.channel.ChannelFutureListener.CLOSE;
import static org.jboss.netty.util.CharsetUtil.UTF_8;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

public class HttpChunked {
    static final byte[] CRLF = new byte[] { 13, 10 };
    static final ChannelBuffer OVER = wrappedBuffer("0\r\n".getBytes(UTF_8));

    private volatile Channel ch = null;
    private volatile boolean keepAlive = false;
    private volatile boolean closed = false;
    private List<String> pendings = new ArrayList<String>();

    public HttpChunked() {
    }

    public HttpChunked(String msg) {
        synchronized (pendings) {
            pendings.add(msg);
        }
    }

    public ChannelFuture close() throws Exception {
        this.closed = true;
        if (ch != null) {
            ChannelFuture future = ch.write(OVER);
            if (!keepAlive) {
                future.addListener(CLOSE);
            }
            ch = null;
            return future;
        } else {
            return null;
        }
    }

    public ChannelFuture send(String msg) {
        if (ch != null) {
            sendPendingIfAny();
            byte[] bytes = msg.getBytes(UTF_8);
            ChannelBuffer buffer = dynamicBuffer(BIG_ENDIAN,
                    16 + bytes.length);
            // chunk length
            buffer.writeBytes(toHexString(bytes.length).getBytes(UTF_8));
            buffer.writeBytes(CRLF);
            buffer.writeBytes(bytes);
            buffer.writeBytes(CRLF);
            return ch.write(buffer);
        } else {
            pendings.add(msg);
        }
        return null;
    }

    public ChannelFuture sendAndClose(String msg) throws Exception {
        send(msg);
        return close();
    }

    private void sendPendingIfAny() {
        synchronized (pendings) {
            if (pendings.size() != 0) {
                int length = 0;
                byte[][] bytes = new byte[pendings.size()][];
                for (int i = 0; i < pendings.size(); i++) {
                    byte[] bs = pendings.get(i).getBytes(UTF_8);
                    length += bs.length;
                    bytes[i] = bs;
                }
                pendings.clear();
                ChannelBuffer buffer = dynamicBuffer(BIG_ENDIAN, 16 + length);

                buffer.writeBytes(toHexString(length).getBytes(UTF_8));
                buffer.writeBytes(CRLF);

                for (byte[] bs : bytes) {
                    buffer.writeBytes(bs);
                }
                buffer.writeBytes(CRLF);
                ch.write(buffer);
            }
        }
        if (closed) {
            ch.write(OVER);
        }
    }

    public void setChannel(Channel ch, boolean keepAlive) {
        this.ch = ch;
        this.keepAlive = keepAlive;
        sendPendingIfAny();
    }
}
