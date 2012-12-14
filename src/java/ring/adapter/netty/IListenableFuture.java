package ring.adapter.netty;

public interface IListenableFuture {
    void addListener(Runnable listener);

    public Object get();
}
