package org.jetlinks.supports.cache;

import lombok.SneakyThrows;
import org.h2.mvstore.Cursor;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.jetlinks.core.Payload;
import org.jetlinks.core.cache.FileQueue;
import org.jetlinks.core.codec.Codec;
import org.jetlinks.core.config.ConfigKey;
import org.springframework.util.Assert;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * http://www.h2database.com/html/mvstore.html
 *
 * @param <T> Type
 */
class MVStoreQueue<T> implements FileQueue<T> {

    private MVStore store;
    private MVMap<Long, byte[]> mvMap;

    private final AtomicLong index = new AtomicLong();

    private final Codec<T> codec;

    private final String name;

    private final Path storageFile;

    @SneakyThrows
    MVStoreQueue(Path filePath,
                 String name,
                 Codec<T> codec) {
        Files.createDirectories(filePath);
        this.name = name;
        this.storageFile = filePath.resolve(name);
        this.codec = codec;
        open();
    }

    protected void open() {
        try {
            if (store != null && !store.isClosed()) {
                store.close();
            }
        } catch (Throwable ignore) {

        }
        String path = storageFile.toUri().getScheme().equals("jimfs") ?
                storageFile.toUri().toString() : storageFile.toString();

        store = new MVStore.Builder()
                .fileName(path)
                // TODO: 2021/7/13 配置化
                .cacheSize(1)
                .autoCommitDisabled()
                .open();

        mvMap = store.openMap(name);
        if (!mvMap.isEmpty())
            index.set(mvMap.lastKey());

    }


    @Override
    public void flush() {
        if (store.isClosed()) {
            return;
        }
        store.commit();
        store.sync();
    }

    @Override
    public synchronized void close() {
        if (store.isClosed()) {
            return;
        }
        store.sync();
        store.close();
    }

    private void checkClose() {
        if (store.isClosed()) {
            throw new IllegalStateException("file queue " + name + " is closed");
        }
    }

    private byte[] encode(T data) {
        return codec.encode(data)
                    .getBytes(true);
    }

    private T decode(byte[] data) {
        if (data == null) {
            return null;
        }
        Payload payload = Payload.of(data);
        T val = null;
        try {
            val = codec.decode(payload);
        } finally {
            if (!(val instanceof Payload)) {
                payload.release();
            }
        }
        return val;
    }

    @Override
    public int size() {
        checkClose();
        return mvMap.size();
    }

    @Override
    public boolean isEmpty() {
        checkClose();
        return mvMap.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        checkClose();
        return mvMap.containsValue(o);
    }

    @Override
    @Nonnull
    public Iterator<T> iterator() {
        checkClose();
        Cursor<Long, byte[]> cursor = mvMap.cursor(mvMap.firstKey());

        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return cursor.hasNext();
            }

            @Override
            public T next() {
                return decode(cursor.getValue());
            }
        };
    }

    @Override
    @Nonnull
    public Object[] toArray() {
        return toArray(new Object[0]);
    }

    @Override
    @Nonnull
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        checkClose();
        return stream().toArray((i) -> a);
    }

    @Override
    public boolean add(T t) {
        checkClose();
        if (null == t) {
            return false;
        }
        byte[] val = encode(t);
        do {
            val = mvMap.put(index.incrementAndGet(), val);
        } while (val != null);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("remove unsupported");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        checkClose();
        return mvMap.values().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        checkClose();
        for (T t : c) {
            add(t);
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("removeAll unsupported");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("retainAll unsupported");
    }

    @Override
    public void clear() {
        if(mvMap.isClosed()){
            return;
        }
        mvMap.clear();
        index.set(0);
    }

    @Override
    public boolean offer(T t) {
        checkClose();
        return add(t);
    }

    @Override
    public T remove() {
        checkClose();
        T data = poll();
        if (data == null) {
            throw new NoSuchElementException("No such element in file " + storageFile);
        }
        return data;
    }

    @Override
    public T poll() {
        if(mvMap.isClosed()){
            return null;
        }
        byte[] removed;
        synchronized (this) {
            Long key = mvMap.firstKey();
            removed = key == null ? null : mvMap.remove(key);
            if (removed == null) {
                index.set(0);
                return null;
            }
        }
        return decode(removed);

    }

    @Override
    public T element() {
        if(mvMap.isClosed()){
            return null;
        }
        T data = peek();
        if (data == null) {
            throw new NoSuchElementException("No such element in file " + storageFile);
        }
        return data;
    }

    @Override
    public T peek() {
        checkClose();
        byte[] value = mvMap.get(mvMap.firstKey());
        return decode(value);
    }


    static class Builder<T> implements FileQueue.Builder<T> {
        private String name;
        private Codec<T> codec;
        private Path path;

        @Override
        public FileQueue.Builder<T> name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public FileQueue.Builder<T> codec(Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        @Override
        public FileQueue.Builder<T> path(Path path) {
            this.path = path;
            return this;
        }

        @Override
        public FileQueue.Builder<T> options(Map<String, Object> options) {
            return this;
        }

        @Override
        public FileQueue.Builder<T> option(String key, Object value) {
            return this;
        }

        @Override
        public <V> FileQueue.Builder<T> option(ConfigKey<V> key, V value) {
            return this;
        }

        @Override
        public FileQueue<T> build() {
            Assert.hasText(name, "name must not be empty");
            Assert.notNull(path, "path must not be null");
            Assert.notNull(path, "codec must not be null");
            return new MVStoreQueue<>(path, name, codec);
        }
    }
}
