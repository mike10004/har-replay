package io.github.mike10004.harreplay;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ImmutableListTypeAdapterFactoryTest {

    @Test
    public void create() {
        ListHolder original = new ListHolder();
        original.items = ImmutableList.of(new ListItem("beatrice", 3), new ListItem("henry", 5));
        String json = gson().toJson(original);
        System.out.format("json:%n%s%n", json);
        ListHolder deserialized = gson().fromJson(json, ListHolder.class);
        System.out.format("deserialized:%n%s%n", deserialized);
        assertEquals("deserialized", original, deserialized);
    }

    private static Gson gson() {
        return gson(null);
    }

    private static Gson gson(@Nullable TypeAdapterFactory typeAdapterFactory) {
        GsonBuilder b =new GsonBuilder().setPrettyPrinting();
        if (typeAdapterFactory != null) {
            b.registerTypeAdapterFactory(typeAdapterFactory);
        }
        return b.create();
    }

    public static class ListHolder {

        @JsonAdapter(ImmutableListTypeAdapterFactory.class)
        public ImmutableList<ListItem> items;

        @Override
        public String toString() {
            return "ListHolder{" +
                    "items=" + items +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListHolder that = (ListHolder) o;
            return Objects.equals(items, that.items);
        }

        @Override
        public int hashCode() {

            return Objects.hash(items);
        }
    }

    public static class ListItem {
        public String name;
        public int rank;

        public ListItem(String name, int rank) {
            this.name = name;
            this.rank = rank;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListItem listItem = (ListItem) o;
            return rank == listItem.rank &&
                    Objects.equals(name, listItem.name);
        }

        @Override
        public int hashCode() {

            return Objects.hash(name, rank);
        }

        @Override
        public String toString() {
            return "ListItem{" +
                    "name='" + name + '\'' +
                    ", rank=" + rank +
                    '}';
        }
    }

    @Test
    public void deserializeImmutableList() throws Exception {
        Type type = new TypeToken<ImmutableList<String>>(){}.getType();
        ImmutableList<String> list = new GsonBuilder()
                .registerTypeAdapterFactory(new ImmutableListTypeAdapterFactory())
                .create()
                .fromJson("[\"a\", \"b\", \"c\"]", type);
        assertEquals(ImmutableList.of("a", "b", "c"), list);
    }

    @Test
    public void deserializeEmptyList() throws Exception {
        Type type = new TypeToken<ImmutableList<String>>(){}.getType();
        ImmutableList<String> list = new GsonBuilder()
                .registerTypeAdapterFactory(new ImmutableListTypeAdapterFactory())
                .create()
                .fromJson("[]", type);
        assertEquals(ImmutableList.of(), list);
    }

    @Test
    public void deserializeField_annotation() throws Exception {
        System.out.println("deserializeField_annotation");
        String json = gson().toJson(ImmutableMap.of("children", new String[]{"a", "b", "c"}));
        System.out.println(json);
        StringListHolder p = gson().fromJson(json, StringListHolder.class);
        assertEquals(ImmutableList.of("a", "b", "c"), p.children);
    }

    @org.junit.Ignore
    @Test
    public void getDelegateAdapter_String() throws Exception {
        Gson gson = new Gson();
        TypeAdapter<String> typeAdapterFromClass = gson.getAdapter(String.class); // this is
        testStringAdapter(typeAdapterFromClass);
        System.out.println("string adapter from java.lang.String.class works");
        TypeAdapter<String> typeAdapterFromToken = gson.getAdapter(TypeToken.get(String.class));
        testStringAdapter(typeAdapterFromToken);
        System.out.println("string adapter from TypeToken.get(String.class) works");
        TypeAdapter<String> delegateAdapter = gson.getDelegateAdapter(null, TypeToken.get(String.class));
        assertSame("fromClass == fromToken", typeAdapterFromClass, typeAdapterFromToken);
        assertSame("fromClass == delegate", typeAdapterFromClass, delegateAdapter);
        assertSame("delegate == fromToken", delegateAdapter, typeAdapterFromToken);
        testStringAdapter(delegateAdapter);
    }

    private void testStringAdapter(TypeAdapter<String> adapter) throws IOException {
        String expected = "hello";
        String actual = adapter.fromJson("\"hello\"");
        assertEquals("delegate", expected, actual);

    }

    @org.junit.Ignore
    @Test
    public void getDelegateAdapter() throws Exception {
        TypeAdapter<String> adapter = new Gson().getDelegateAdapter(null, TypeToken.get(String.class));
        testStringAdapter(adapter);
    }

    @Test
    public void deserializeField_unannotated() throws Exception {
        System.out.println("deserializeField_unannotated");
        TypeAdapterFactory typeAdapterFactory = new ImmutableListTypeAdapterFactory();
        String json = gson().toJson(ImmutableMap.of("children", new String[]{"a", "b", "c"}));
        System.out.println(json);
        UnannotatedStringListHolder p = gson(typeAdapterFactory).fromJson(json, UnannotatedStringListHolder.class);
        assertEquals(ImmutableList.of("a", "b", "c"), p.children);
    }

    public static class StringListHolder {

        @JsonAdapter(ImmutableListTypeAdapterFactory.class)
        public ImmutableList<String> children;
    }

    public static class UnannotatedStringListHolder {
        public ImmutableList<String> children;
    }

    @JsonAdapter(CustomDeserializedValueTypeAdapter.class)
    public static final class CustomDeserializedValue {
        @SuppressWarnings("unused")
        public int strength;
        private transient boolean augmented;
    }

    public static class CustomDeserializedValueTypeAdapter extends TypeAdapter<CustomDeserializedValue> {

        private final Gson stockGson = new Gson();

        @Override
        public void write(JsonWriter out, CustomDeserializedValue value) {
            stockGson.toJson(value, CustomDeserializedValue.class, out);
        }

        @Override
        public CustomDeserializedValue read(JsonReader in) {
            CustomDeserializedValue deserialized = stockGson.fromJson(in, CustomDeserializedValue.class);
            deserialized.augmented = true;
            return deserialized;
        }
    }

    @Test
    public void writeList() throws Exception {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new ImmutableListTypeAdapterFactory())
                .create();
        ImmutableList<Integer> list = ImmutableList.of(1, 2, 3);
        String json = gson.toJson(list);
        assertEquals(new Gson().toJson(list), json); // check against default gson
    }

    @Test
    public void writeStringListHolder_empty() throws Exception {
        Gson gson = gson();
        ImmutableList<String> list = ImmutableList.of();
        StringListHolder holder = new StringListHolder();
        holder.children = list;
        String json = gson.toJson(holder);
        System.out.println(json);
        assertEquals("{\n" +
                "  \"children\": []\n" +
                "}", json); // check against default gson
    }

    @Test
    public void customDeserializedValuesInList() throws Exception {
        ImmutableList<CustomDeserializedValue> values = ImmutableList.<CustomDeserializedValue>builder()
                .build();
        values.forEach(value -> {
            checkState(!value.augmented);
        });
        Gson gson = gson(new ImmutableListTypeAdapterFactory());
        String json = gson.toJson(values);
        System.out.println(json);
        ImmutableList<CustomDeserializedValue> deserialized = gson.fromJson(json, new TypeToken<ImmutableList<CustomDeserializedValue>>(){}.getType());
        // check that CustomDeserializedValueTypeAdapter was used, setting augmented = true
        deserialized.forEach(item -> {
            assertTrue("augmented", item.augmented);
        });
    }

    @Test(expected = ImmutableListTypeAdapterFactory.UnsupportedNestedListException.class)
    public void nestedList_deserialize() throws Exception {
        String json = "[[1,2,3],[4,5,6]]";
        TypeToken<ImmutableList<ImmutableList<Integer>>> token = new TypeToken<ImmutableList<ImmutableList<Integer>>>() {};
        TypeAdapterFactory adapterFactory = new ImmutableListTypeAdapterFactory();
        ImmutableList<ImmutableList<Integer>> list = gson(adapterFactory).fromJson(json, token.getType());
        System.out.println(gson().toJson(list));
    }

    @Test
    public void nestedList_serialize() throws Exception {
        ImmutableList<ImmutableList<Integer>> listOfLists = ImmutableList.of(ImmutableList.of(1, 2, 3), ImmutableList.of(4, 5, 6));
        TypeAdapterFactory adapterFactory = new ImmutableListTypeAdapterFactory();
        String json = new GsonBuilder().registerTypeAdapterFactory(adapterFactory).create().toJson(listOfLists);
        String expected = "[[1,2,3],[4,5,6]]";
        assertEquals("json", expected, json);
    }
}