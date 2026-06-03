package org.example.radar.store;

import org.example.common.enums.AircraftType;
import org.example.common.model.AirObjectState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AirObjectRegistry {
    private final ConcurrentMap<String, AirObjectState> objects = new ConcurrentHashMap<String, AirObjectState>();

    public void upsert(AirObjectState state) {
        objects.put(state.getId(), state);
    }

    public AirObjectState getById(String id) {
        return objects.get(id);
    }

    public void remove (String id) {
        objects.remove(id);
    }

    public List<AirObjectState> getAll() {
        return new ArrayList<>(objects.values());
    }

    @Override
    public String toString() {
        return "AirObjectRegistry{" +
                "objects=" + objects +
                '}';
    }
}
