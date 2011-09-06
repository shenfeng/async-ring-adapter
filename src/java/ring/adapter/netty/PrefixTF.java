package ring.adapter.netty;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class PrefixTF implements ThreadFactory {

    private final String mPrefix;
    private final AtomicInteger mNum = new AtomicInteger(0);

    public PrefixTF(String prefix) {
        mPrefix = prefix;
    }

    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, mPrefix + "#" + mNum.incrementAndGet());
        return t;
    }
}
