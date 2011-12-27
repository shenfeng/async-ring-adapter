package ring.adapter.netty;

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;
import static org.jboss.netty.channel.ChannelFutureListener.CLOSE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.util.CharsetUtil.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.jboss.netty.handler.stream.ChunkedStream;

import clojure.lang.ISeq;
import clojure.lang.Seqable;

public class Util {

    public static void writeResp(ChannelHandlerContext ctx,
            DefaultHttpResponse resp, Object body, boolean keepAlive)
            throws IOException {
        final Channel ch = ctx.getChannel();
        if (body instanceof String) {
            final ChannelBuffer buffer = copiedBuffer((String) body, UTF_8)
                    .slice();
            resp.setContent(buffer);
            if (keepAlive) {
                setContentLength(resp, buffer.readableBytes());
                ch.write(resp);
            } else {
                ch.write(resp).addListener(CLOSE);
            }
        } else if (body instanceof Seqable) {
            final List<ChannelBuffer> comps = new ArrayList<ChannelBuffer>();
            ISeq seq = ((Seqable) body).seq();
            while (seq != null) {
                comps.add(copiedBuffer(seq.first().toString(), UTF_8).slice());
                seq = seq.next();
            }
            ChannelBuffer buffer = new CompositeChannelBuffer(
                    ByteOrder.BIG_ENDIAN, comps);
            resp.setContent(buffer);
            if (keepAlive) {
                setContentLength(resp, buffer.readableBytes());
                ch.write(resp);
            } else {
                ch.write(resp).addListener(CLOSE);
            }

        } else if (body instanceof File) {
            ch.write(resp);
            final ChunkedFile f = new ChunkedFile((File) body);
            if (keepAlive) {
                ch.write(f);
            } else {
                ch.write(f).addListener(CLOSE);
            }
        } else if (body instanceof InputStream) {
            ch.write(resp);
            final InputStream is = (InputStream) body;
            ch.write(new ChunkedStream(is)).addListener(
                    new ChannelFutureListener() {
                        public void operationComplete(ChannelFuture future)
                                throws Exception {
                            future.getChannel().close();
                            is.close();
                        }
                    });

        } else if (body == null) {
            setContentLength(resp, 0);
            if (keepAlive) {
                ch.write(resp);
            } else {
                ch.write(resp).addListener(CLOSE);
            }
        } else if (body instanceof HttpResponse) {
            if (keepAlive) {
                ch.write(body);
            } else {
                ch.write(body).addListener(CLOSE);
            }
        } else {
            throw new RuntimeException("Unrecognized body: " + body);
        }
    }
}
