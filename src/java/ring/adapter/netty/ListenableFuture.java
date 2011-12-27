package ring.adapter.netty;

public interface ListenableFuture {
    void addListener(Runnable listener);

    public Object get();
}
