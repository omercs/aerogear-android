/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.android.impl.pipeline.loader;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.pipeline.AbstractFragmentCallback;
import org.jboss.aerogear.android.pipeline.LoaderPipe;
import org.jboss.aerogear.android.pipeline.Pipe;
import org.jboss.aerogear.android.pipeline.support.AbstractFragmentActivityCallback;
import org.jboss.aerogear.android.pipeline.support.AbstractSupportFragmentCallback;

import java.util.List;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@SuppressWarnings( { "rawtypes", "unchecked" })
public class FragmentLoaderAdapter<T> extends AbstractLoaderAdapter<T, LoaderManager, LoaderManager.LoaderCallbacks> implements LoaderPipe<T>, LoaderManager.LoaderCallbacks<T> {

    private static class FragmentLoaderAdapterOperations extends LoaderManagerOperations<LoaderManager, LoaderManager.LoaderCallbacks> {

        FragmentLoaderAdapterOperations(LoaderManager loaderManager) {
            super(loaderManager);
        }

        @Override
        void initLoader(Integer id, Bundle bundle, LoaderManager.LoaderCallbacks callbacks) {
            this.getLoaderManager().initLoader(id, bundle, callbacks);
        }

        @Override
        void reset(Integer id) {
            Loader<Object> loader = this.getLoaderManager().getLoader(id);
            if (loader != null) {
                this.getLoaderManager().destroyLoader(id);
            }
        }
    }
    private static final String TAG = FragmentLoaderAdapter.class.getSimpleName();

    private Fragment fragment;

    public FragmentLoaderAdapter(Fragment fragment, Context applicationContext,
                         Pipe<T> pipe, String name) {
        super(name, pipe, new FragmentLoaderAdapterOperations(fragment.getLoaderManager()), applicationContext);
        this.fragment = fragment;
    }

    @Override
    public void onLoadFinished(Loader<T> loader, final T data) {
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
    public void onLoaderReset(Loader<T> loader) {
        Log.e(TAG, loader.toString());

    }

    static class CallbackHandler<T> implements Runnable {

        private final AbstractPipeLoader<T> modernLoader;
        private final Fragment fragment;
        private final T data;

        public CallbackHandler(AbstractPipeLoader<T> loader, Fragment fragment, T data) {
            this.modernLoader = loader;
            this.fragment = fragment;
            this.data = data;
        }

        @Override
        public void run() {
            if (modernLoader.hasException()) {
                final Exception exception = modernLoader.getException();
                Log.e(TAG, exception.getMessage(), exception);
                if (modernLoader.getCallback() instanceof AbstractFragmentCallback) {
                    AbstractFragmentCallback callback = (AbstractFragmentCallback) modernLoader.getCallback();
                    callback.setFragment(fragment);
                    callback.onFailure(exception);
                    callback.setFragment(null);
                } else {
                    modernLoader.getCallback().onFailure(exception);
                }
            } else {

                if (modernLoader.getCallback() instanceof AbstractFragmentCallback) {
                    AbstractFragmentCallback callback = (AbstractFragmentCallback) modernLoader.getCallback();
                    callback.setFragment(fragment);
                    callback.onSuccess(data);
                    callback.setFragment(null);
                } else {
                    modernLoader.getCallback().onSuccess(data);
                }
            }

        }
    }


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
}
