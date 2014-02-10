package org.jboss.aerogear.android.impl.pipeline.loader;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.Loader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.pipeline.AbstractActivityCallback;
import org.jboss.aerogear.android.pipeline.Pipe;
import org.jboss.aerogear.android.pipeline.support.AbstractFragmentActivityCallback;
import org.jboss.aerogear.android.pipeline.support.AbstractSupportFragmentCallback;

import java.util.List;

/**
 * This class wraps a Pipe in an asynchronous Loader.
 *
 * This classes uses Loaders from android.conent. It will not work on pre
 * Honeycomb devices. If you do need to support Android devices &lt; version
 * 3.0, consider using {@link org.jboss.aerogear.android.impl.pipeline.SupportLoaderAdapter}
 *
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@SuppressWarnings( { "rawtypes", "unchecked" })
public class ActivityLoaderAdapter<T> extends AbstractLoaderAdapter<T, LoaderManager, LoaderManager.LoaderCallbacks> {

    private static class ActivityLoaderAdapterOperations extends LoaderManagerOperations<LoaderManager, LoaderManager.LoaderCallbacks> {


        ActivityLoaderAdapterOperations(LoaderManager loaderManager) {
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

    private static final String TAG = ActivityLoaderAdapter.class.getSimpleName();

    private Activity activity;

    public ActivityLoaderAdapter(Activity activity, Pipe<T> pipe,
                         String name) {
        super(name, pipe, new ActivityLoaderAdapterOperations(activity.getLoaderManager()), activity.getApplicationContext());
        this.activity = activity;
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
            handler.post(new CallbackHandler<T>(modernLoader, activity, data));
        }
    }

    @Override
    public void onLoaderReset(Loader<T> loader) {
        Log.e(TAG, loader.toString());

    }

    static class CallbackHandler<T> implements Runnable {

        private final AbstractPipeLoader<T> modernLoader;
        private final Activity activity;
        private final T data;

        public CallbackHandler(AbstractPipeLoader<T> loader, Activity activity, T data) {
            this.modernLoader = loader;
            this.activity = activity;
            this.data = data;
        }

        @Override
        public void run() {
            if (modernLoader.hasException()) {
                final Exception exception = modernLoader.getException();
                Log.e(TAG, exception.getMessage(), exception);
                if (modernLoader.getCallback() instanceof AbstractActivityCallback) {
                    AbstractActivityCallback callback = (AbstractActivityCallback) modernLoader.getCallback();
                    callback.setActivity(activity);
                    callback.onFailure(exception);
                    callback.setActivity(null);
                } else {
                    modernLoader.getCallback().onFailure(exception);
                }

            } else {
                if (modernLoader.getCallback() instanceof AbstractActivityCallback) {
                    AbstractActivityCallback callback = (AbstractActivityCallback) modernLoader.getCallback();
                    callback.setActivity(activity);
                    callback.onSuccess(data);
                    callback.setActivity(null);
                } else {
                    modernLoader.getCallback().onSuccess(data);
                }
            }

        }
    }


    @Override
    protected void verifyCallback(Callback<List<T>> callback) {
        if (callback instanceof AbstractActivityCallback) {
            if (activity == null) {
                throw new IllegalStateException("An AbstractActivityCallback was supplied, but there is no Activity.");
            }
        } else if (callback instanceof AbstractFragmentActivityCallback) {
            throw new IllegalStateException("An AbstractFragmentActivityCallback was supplied, but this is the modern Loader.");
        } else if (callback instanceof AbstractSupportFragmentCallback) {
            throw new IllegalStateException("An AbstractSupportFragmentCallback was supplied, but this is the modern Loader.");
        }
    }

}
