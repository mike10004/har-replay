package io.github.mike10004.harreplay;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ImmutableListTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!ImmutableList.class.equals(type.getRawType())) {
            return null;
        }
        return (TypeAdapter<T>) createImmutableListTypeAdapter(gson, type);
    }

    static class UnsupportedNestedListException extends IllegalArgumentException {
        public UnsupportedNestedListException() {
            super("not supported: ImmutableList instances whose elements are ImmutableList instances");
        }
    }

    private <E> TypeAdapter<ImmutableList<E>> createImmutableListTypeAdapter(Gson gson, TypeToken<?> immutableListType) {
        ParameterizedType targetType = (ParameterizedType) immutableListType.getType();
        Type listElementType = targetType.getActualTypeArguments()[0];
        if (listElementType instanceof ParameterizedType) {
            Type rawElementType = ((ParameterizedType)listElementType).getRawType();
            if (rawElementType instanceof Class<?> && ImmutableList.class.isAssignableFrom((Class<?>) rawElementType)) {
                throw new UnsupportedNestedListException();
            }
        }
        TypeAdapter<E> elementTypeAdapter = getElementTypeAdapter(gson, listElementType);
        return new ImmutableListTypeAdapter<>(elementTypeAdapter);
    }

    /**
     * Gets the element type
     * @param gson gson instance
     * @param elementType list element type
     * @param <E> the element type represented by {@code elementType}
     * @return a type adapter
     */
    protected <E> TypeAdapter<E> getElementTypeAdapter(Gson gson, Type elementType) {
//        TypeAdapter<?> typeAdapter = gson.getDelegateAdapter(this, TypeToken.get(elementType));
        TypeAdapter<?> typeAdapter = gson.getAdapter(TypeToken.get(elementType));
        //noinspection unchecked // because elementType is not generified
        return (TypeAdapter<E>) typeAdapter;
    }

    private static class ImmutableListTypeAdapter<E> extends ListTypeAdapter<E, ImmutableList<E>> {

        private ImmutableListTypeAdapter(TypeAdapter<E> eTypeAdapter) {
            super(eTypeAdapter);
        }

        @Override
        protected ImmutableList<E> transform(List<E> list) {
            return ImmutableList.copyOf(list);
        }
    }

    private static abstract class ListTypeAdapter<E, L extends List<E>> extends TypeAdapter<L> {

        private final TypeAdapter<E> eTypeAdapter;

        private ListTypeAdapter(TypeAdapter<E> eTypeAdapter) {
            this.eTypeAdapter = eTypeAdapter;
        }

        protected abstract L transform(List<E> list);

        @Override
        public void write(JsonWriter out, L value) throws IOException {
            out.beginArray();
            for (E element : value) {
                eTypeAdapter.write(out, element);
            }
            out.endArray();
        }

        @Override
        public L read(JsonReader in) throws IOException {
            in.beginArray();
            List<E> list = new ArrayList<>();
            while (in.peek() != JsonToken.END_ARRAY) {
                E element = eTypeAdapter.read(in);
                list.add(element);
            }
            in.endArray();
            return transform(list);
        }
    }

}