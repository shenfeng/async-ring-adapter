package ring.adapter.netty;

import java.util.concurrent.Future;
import org.jboss.netty.handler.codec.http.HttpResponse;

public interface HttpResponseFuture extends Future<HttpResponse> {
    void addListener(Runnable listener);
}
