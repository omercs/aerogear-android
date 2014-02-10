package org.jboss.aerogear.android.impl.pipeline.loader;

abstract class LoaderManagerOperations<K, L> {
    private final K loaderManager;

    LoaderManagerOperations(K loaderManager) {
        this.loaderManager = loaderManager;
    }

    public K getLoaderManager() {
        return loaderManager;
    }

    abstract void initLoader(Integer id, android.os.Bundle bundle, L callbacks);

    abstract void reset(Integer id);
}
