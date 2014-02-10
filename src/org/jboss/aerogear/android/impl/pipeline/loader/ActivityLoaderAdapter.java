package org.jboss.aerogear.android.impl.pipeline.loader;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.common.base.Objects;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.ReadFilter;
import org.jboss.aerogear.android.impl.pipeline.GsonRequestBuilder;
import org.jboss.aerogear.android.pipeline.AbstractActivityCallback;
import org.jboss.aerogear.android.pipeline.LoaderPipe;
import org.jboss.aerogear.android.pipeline.Pipe;
import org.jboss.aerogear.android.pipeline.PipeHandler;
import org.jboss.aerogear.android.pipeline.PipeType;
import org.jboss.aerogear.android.pipeline.RequestBuilder;
import org.jboss.aerogear.android.pipeline.ResponseParser;
import org.jboss.aerogear.android.pipeline.support.AbstractFragmentActivityCallback;
import org.jboss.aerogear.android.pipeline.support.AbstractSupportFragmentCallback;

import java.net.URL;
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
public class ActivityLoaderAdapter<T> implements LoaderPipe<T>,
        LoaderManager.LoaderCallbacks<T> {

    private static final String TAG = ActivityLoaderAdapter.class.getSimpleName();
    private final Handler handler;
    private Multimap<String, Integer> idsForNamedPipes;

    private static enum Methods {

        READ, SAVE, REMOVE
    }

    private final Context applicationContext;
    private Activity activity;
    private final Pipe<T> pipe;
    private final LoaderManager manager;
    private final String name;
    private final RequestBuilder<T> requestBuilder;
    private final ResponseParser<T> responseParser;

    public ActivityLoaderAdapter(Activity activity, Pipe<T> pipe,
                         String name) {
        this.pipe = pipe;
        this.requestBuilder = pipe.getRequestBuilder();
        this.responseParser = pipe.getResponseParser();
        this.manager = activity.getLoaderManager();
        this.applicationContext = activity.getApplicationContext();
        this.name = name;
        this.handler = new Handler(Looper.getMainLooper());
        this.activity = activity;
    }

    @Override
    public PipeType getType() {
        return pipe.getType();
    }

    @Override
    public URL getUrl() {
        return pipe.getUrl();
    }

    @Override
    public void read(Callback<List<T>> callback) {
        int id = Objects.hashCode(name, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(FILTER, null);
        bundle.putSerializable(METHOD, Methods.READ);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public void readWithFilter(ReadFilter filter, Callback<List<T>> callback) {
        read(filter, callback);
    }

    @Override
    public void read(ReadFilter filter, Callback<List<T>> callback) {
        int id = Objects.hashCode(name, filter, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(FILTER, filter);
        bundle.putSerializable(METHOD, Methods.READ);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public void save(T item, Callback<T> callback) {
        int id = Objects.hashCode(name, item, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(ITEM, requestBuilder.getBody(item));// item may not be
        // serializable, but it
        // has to be gsonable
        bundle.putSerializable(METHOD, Methods.SAVE);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public void remove(String toRemoveId, Callback<Void> callback) {
        int id = Objects.hashCode(name, toRemoveId, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(REMOVE_ID, toRemoveId);
        bundle.putSerializable(METHOD, Methods.REMOVE);
        manager.initLoader(id, bundle, this);
    }

    @Override
    public PipeHandler<T> getHandler() {
        return pipe.getHandler();
    }

    @Override
    public Gson getGson() {
        return requestBuilder instanceof GsonRequestBuilder ? ((GsonRequestBuilder) requestBuilder)
                .getGson() : null;
    }

    @Override
    public RequestBuilder<T> getRequestBuilder() {
        return requestBuilder;
    }

    @Override
    public ResponseParser<T> getResponseParser() {
        return responseParser;
    }

    @Override
    public Class<T> getKlass() {
        return pipe.getKlass();
    }

    @Override
    public Loader<T> onCreateLoader(int id, Bundle bundle) {
        this.idsForNamedPipes.put(name, id);
        Methods method = (Methods) bundle.get(METHOD);
        Callback callback = (Callback) bundle.get(CALLBACK);
        verifyCallback(callback);
        Loader loader = null;
        switch (method) {
            case READ: {
                ReadFilter filter = (ReadFilter) bundle.get(FILTER);
                loader = new ReadLoader(applicationContext, callback,
                        pipe.getHandler(), filter, this);
            }
            break;
            case REMOVE: {
                String toRemove = bundle.getString(REMOVE_ID, "-1");
                loader = new RemoveLoader(applicationContext, callback,
                        pipe.getHandler(), toRemove);
            }
            break;
            case SAVE: {
                byte[] json = bundle.getByteArray(ITEM);
                T item = responseParser.handleResponse(new String(json), pipe.getKlass());
                loader = new SaveLoader(applicationContext, callback,
                        pipe.getHandler(), item);
            }
            break;
        }
        return loader;
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

    @Override
    public void reset() {
        for (Integer id : idsForNamedPipes.get(name)) {
            Loader loader = manager.getLoader(id);
            if (loader != null) {
                manager.destroyLoader(id);
            }
        }
        idsForNamedPipes.removeAll(name);
    }

    @Override
    public void setLoaderIds(Multimap<String, Integer> idsForNamedPipes) {
        this.idsForNamedPipes = idsForNamedPipes;
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


    private void verifyCallback(Callback<List<T>> callback) {
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
