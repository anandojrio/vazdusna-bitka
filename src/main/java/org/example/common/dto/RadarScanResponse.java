package org.example.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RadarScanResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<RadarContact> visibleObjects;

    public RadarScanResponse() {
        this.visibleObjects = new ArrayList<>();
    }

    public RadarScanResponse(List<RadarContact> visibleObjects) {
        this.visibleObjects = new ArrayList<>(visibleObjects);
    }

    public List<RadarContact> getVisibleObjects() {
        return visibleObjects;
    }

    public void setVisibleObjects(List<RadarContact> visibleObjects) {
        this.visibleObjects = visibleObjects;
    }

    public List<String> getVisibleObjectIds() {
        return visibleObjects.stream()
                .map(RadarContact::getId)
                .collect(Collectors.toList());
    }
}