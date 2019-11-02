package net.minecraftforge.gradle;

import com.google.gson.*;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.lang.reflect.Type;

public class OldPropertyMapSerializer implements JsonSerializer<PropertyMap> {
    @Override
    public JsonElement serialize(PropertyMap src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject out = new JsonObject();
        for (String key : src.keySet()) {
            JsonArray jsa = new JsonArray();
            for (Property p : src.get(key)) {
                jsa.add(new JsonPrimitive(p.getValue()));
            }
            out.add(key, jsa);
        }
        return out;
    }
}
