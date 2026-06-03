package org.example.radar.service;

import org.example.common.dto.RadarContact;
import org.example.common.dto.RadarScanResponse;
import org.example.common.dto.RadarUpdateRequest;
import org.example.common.enums.FlyingObjectType;
import org.example.common.model.AirObjectState;
import org.example.common.model.Position;
import org.example.common.util.DistanceUtils;
import org.example.radar.store.AirObjectRegistry;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

public class RadarServiceImpl extends UnicastRemoteObject implements RadarService {

    private final AirObjectRegistry registry;

    public RadarServiceImpl(AirObjectRegistry registry) throws RemoteException {
        super();
        this.registry = registry;
    }


    @Override
    public RadarScanResponse updateAndScan(RadarUpdateRequest request) throws RemoteException {
        AirObjectState currentState = new AirObjectState(
                true,
                request.getAircraftType(),
                request.getId(),
                request.getObjectType(),
                request.getPosition(),
                request.getSide()
        );
        registry.upsert(currentState);

        List<RadarContact> visibleObjects = new ArrayList<>();
        Position myPosition = request.getPosition();
        double radarRange = request.getRadarRange();

        for (AirObjectState other : registry.getAll()) {
            if (other.getId().equals(request.getId())) {
                continue;
            }

            if (!other.isActive()) {
                continue;
            }

            if (request.getObjectType() == FlyingObjectType.MISSILE
                    && other.getObjectType() != FlyingObjectType.AIRCRAFT) {
                continue;
            }

            double distance = DistanceUtils.euclideanDistance(myPosition, other.getPosition());

            if (distance <= radarRange) {
                visibleObjects.add(new RadarContact(
                        other.getId(),
                        other.getObjectType(),
                        other.getSide(),
                        other.getPosition()
                ));
            }
        }

        return new RadarScanResponse(visibleObjects);
    }

    @Override
    public void unregister(String id) throws RemoteException {
        registry.remove(id);
    }
}
