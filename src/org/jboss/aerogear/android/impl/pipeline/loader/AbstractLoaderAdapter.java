package org.jboss.aerogear.android.impl.pipeline.loader;

import android.annotation.TargetApi;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.google.common.base.Objects;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.ReadFilter;
import org.jboss.aerogear.android.impl.pipeline.GsonRequestBuilder;
import org.jboss.aerogear.android.pipeline.LoaderPipe;
import org.jboss.aerogear.android.pipeline.Pipe;
import org.jboss.aerogear.android.pipeline.PipeHandler;
import org.jboss.aerogear.android.pipeline.PipeType;
import org.jboss.aerogear.android.pipeline.RequestBuilder;
import org.jboss.aerogear.android.pipeline.ResponseParser;

import java.net.URL;
import java.util.List;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
@SuppressWarnings( { "rawtypes", "unchecked" })
public abstract class AbstractLoaderAdapter<T, K, L> implements LoaderPipe<T>, LoaderManager.LoaderCallbacks<T> {

    private final LoaderManagerOperations<K, L> loaderManagerOperations;

    protected final Handler handler;
    protected final Pipe<T> pipe;
    protected final String name;
    protected final RequestBuilder<T> requestBuilder;
    protected final ResponseParser<T> responseParser;
    protected final Context applicationContext;
    protected Multimap<String, Integer> idsForNamedPipes;

    public AbstractLoaderAdapter(String name, Pipe<T> pipe, LoaderManagerOperations<K, L> loaderManagerOperations, Context applicationContext) {
        this.loaderManagerOperations = loaderManagerOperations;
        this.handler = new Handler(Looper.getMainLooper());
        this.requestBuilder = pipe.getRequestBuilder();
        this.responseParser = pipe.getResponseParser();
        this.name = name;
        this.pipe = pipe;
        this.applicationContext = applicationContext;
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
        loaderManagerOperations.initLoader(id, bundle, (L) this);
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
        loaderManagerOperations.initLoader(id, bundle, (L) this);
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
        loaderManagerOperations.initLoader(id, bundle, (L) this);
    }

    @Override
    public void remove(String toRemoveId, Callback<Void> callback) {
        int id = Objects.hashCode(name, toRemoveId, callback);
        Bundle bundle = new Bundle();
        bundle.putSerializable(CALLBACK, callback);
        bundle.putSerializable(REMOVE_ID, toRemoveId);
        bundle.putSerializable(METHOD, Methods.REMOVE);
        loaderManagerOperations.initLoader(id, bundle, (L) this);
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
    public void reset() {
        for (Integer id : idsForNamedPipes.get(name)) {
            loaderManagerOperations.reset(id);
        }
        idsForNamedPipes.removeAll(name);
    }

    @Override
    public void setLoaderIds(Multimap<String, Integer> idsForNamedPipes) {
        this.idsForNamedPipes = idsForNamedPipes;
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

    protected abstract void verifyCallback(Callback<List<T>> callback);

    protected static enum Methods {

        READ, SAVE, REMOVE
    }

}
