package org.example.radar.service;

import org.example.common.dto.RadarScanResponse;
import org.example.common.dto.RadarUpdateRequest;
import org.example.common.enums.AircraftType;
import org.example.common.enums.FlyingObjectType;
import org.example.common.enums.Side;
import org.example.common.model.Position;
import org.example.radar.store.AirObjectRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.rmi.RemoteException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RadarServiceImplTest {

    private RadarService radarService;
    private AirObjectRegistry registry;

    @BeforeEach
    void setUp() throws RemoteException {
        registry = new AirObjectRegistry();
        radarService = new RadarServiceImpl(registry);
    }

    @Test
    void shouldStoreObjectAfterUpdate() throws RemoteException {
        RadarUpdateRequest p1 = new RadarUpdateRequest(
                AircraftType.MIG31,
                "P1",
                FlyingObjectType.AIRCRAFT,
                new Position(0.0, 0.0),
                2.0,
                Side.BLUE
        );

        radarService.updateAndScan(p1);

        assertNotNull(registry.getById("P1"));
        assertEquals("P1", registry.getById("P1").getId());
        assertEquals(FlyingObjectType.AIRCRAFT, registry.getById("P1").getObjectType());
        assertEquals(AircraftType.MIG31, registry.getById("P1").getAircraftType());
        assertEquals(Side.BLUE, registry.getById("P1").getSide());
        assertEquals(new Position(0.0, 0.0), registry.getById("P1").getPosition());
        assertTrue(registry.getById("P1").isActive());
    }

    @Test
    void shouldReturnEmptyListWhenNoOtherObjectsExist() throws RemoteException {
        RadarUpdateRequest p1 = new RadarUpdateRequest(
                AircraftType.MIG31,
                "P1",
                FlyingObjectType.AIRCRAFT,
                new Position(0.0, 0.0),
                2.0,
                Side.BLUE
        );

        RadarScanResponse response = radarService.updateAndScan(p1);

        assertNotNull(response);
        assertNotNull(response.getVisibleObjectIds());
        assertTrue(response.getVisibleObjectIds().isEmpty());
    }

    @Test
    void shouldNotSeeItselfOnRadar() throws RemoteException {
        RadarUpdateRequest p1 = new RadarUpdateRequest(
                AircraftType.MIG31,
                "P1",
                FlyingObjectType.AIRCRAFT,
                new Position(0.0, 0.0),
                10.0,
                Side.BLUE
        );

        RadarScanResponse response = radarService.updateAndScan(p1);

        assertFalse(response.getVisibleObjectIds().contains("P1"));
    }

    @Test
    void shouldReturnObjectsInsideRadarRange() throws RemoteException {
        RadarUpdateRequest c1 = new RadarUpdateRequest(
                AircraftType.MIG31,
                "C1",
                FlyingObjectType.AIRCRAFT,
                new Position(1.0, 1.0),
                2.0,
                Side.RED
        );

        RadarUpdateRequest c2 = new RadarUpdateRequest(
                AircraftType.SU30,
                "C2",
                FlyingObjectType.AIRCRAFT,
                new Position(5.0, 5.0),
                2.0,
                Side.RED
        );

        RadarUpdateRequest p1 = new RadarUpdateRequest(
                AircraftType.MIG31,
                "P1",
                FlyingObjectType.AIRCRAFT,
                new Position(0.0, 0.0),
                2.0,
                Side.BLUE
        );

        radarService.updateAndScan(c1);
        radarService.updateAndScan(c2);

        RadarScanResponse response = radarService.updateAndScan(p1);
        List<String> visibleIds = response.getVisibleObjectIds();

        assertTrue(visibleIds.contains("C1"));
        assertFalse(visibleIds.contains("C2"));
    }

    @Test
    void shouldUpdateExistingObjectPosition() throws RemoteException {
        RadarUpdateRequest firstUpdate = new RadarUpdateRequest(
                AircraftType.MIG31,
                "P1",
                FlyingObjectType.AIRCRAFT,
                new Position(0.0, 0.0),
                2.0,
                Side.BLUE
        );

        RadarUpdateRequest secondUpdate = new RadarUpdateRequest(
                AircraftType.MIG31,
                "P1",
                FlyingObjectType.AIRCRAFT,
                new Position(2.5, 3.5),
                2.0,
                Side.BLUE
        );

        radarService.updateAndScan(firstUpdate);
        radarService.updateAndScan(secondUpdate);

        assertNotNull(registry.getById("P1"));
        assertEquals(new Position(2.5, 3.5), registry.getById("P1").getPosition());
    }

    @Test
    void shouldRemoveObjectAfterUnregister() throws RemoteException {
        RadarUpdateRequest p1 = new RadarUpdateRequest(
                AircraftType.MIG31,
                "P1",
                FlyingObjectType.AIRCRAFT,
                new Position(0.0, 0.0),
                2.0,
                Side.BLUE
        );

        radarService.updateAndScan(p1);
        assertNotNull(registry.getById("P1"));

        radarService.unregister("P1");

        assertNull(registry.getById("P1"));
    }
}