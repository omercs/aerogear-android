package org.jboss.aerogear.android.impl.pipeline.loader;

import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.util.Log;
import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.pipeline.AbstractFragmentCallback;
import org.jboss.aerogear.android.pipeline.Pipe;
import org.jboss.aerogear.android.pipeline.support.AbstractFragmentActivityCallback;
import org.jboss.aerogear.android.pipeline.support.AbstractSupportFragmentCallback;

import java.util.List;

public class SupportFragmentLoaderAdapter<T> extends AbstractLoaderAdapter<T, LoaderManager, LoaderManager.LoaderCallbacks> {

    private static class SupportFragmentLoaderAdapterOperations extends LoaderManagerOperations<LoaderManager, LoaderManager.LoaderCallbacks> {

        protected SupportFragmentLoaderAdapterOperations(LoaderManager loaderManager) {
            super(loaderManager);
        }

        @Override
        void initLoader(Integer id, Bundle bundle, LoaderManager.LoaderCallbacks callbacks) {
            this.getLoaderManager().initLoader(id, bundle, callbacks);
        }

        @Override
        void reset(Integer id) {
            android.support.v4.content.Loader<Object> loader = this.getLoaderManager().getLoader(id);
            if(loader != null) {
                this.getLoaderManager().destroyLoader(id);
            }
        }
    }

    private final Fragment fragment;

    public SupportFragmentLoaderAdapter(Fragment fragment, Context applicationContext, Pipe pipe, String name) {
        super(name, pipe, new SupportFragmentLoaderAdapterOperations(fragment.getLoaderManager()), applicationContext);
        this.fragment = fragment;
    }

    @Override
    protected void verifyCallback(Callback<List<T>> callback) {
        if (callback instanceof AbstractFragmentCallback) {
            if (fragment == null) {
                throw new IllegalStateException("An AbstractFragmentCallback was supplied, but there is no Fragment.");
            }
        } else if (callback instanceof AbstractFragmentActivityCallback) {
            throw new IllegalStateException("An AbstractFragmentActivityCallback was supplied, but this is the modern Loader.");
        } else if (callback instanceof AbstractSupportFragmentCallback) {
            throw new IllegalStateException("An AbstractSupportFragmentCallback was supplied, but this is the modern Loader.");
        }

    }

    @Override
    public void onLoadFinished(Loader loader, Object o) {
        if (!(loader instanceof AbstractPipeLoader)) {
            Log.e(TAG,
                    "Adapter is listening to loaders which it doesn't support");
            throw new IllegalStateException(
                    "Adapter is listening to loaders which it doesn't support");
        } else {
            final AbstractPipeLoader<T> modernLoader = (AbstractPipeLoader<T>) loader;
            handler.post(new CallbackHandler<T>(modernLoader, fragment, data));
        }

    }

    @Override
    public void onLoaderReset(Loader loader) {

    }
}
