package org.example.bootstrap;

import org.example.radar.service.RadarService;
import org.example.radar.service.RadarServiceImpl;
import org.example.radar.store.AirObjectRegistry;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class RadarApplication {

    public static void main(String[] args) {
        try {
            AirObjectRegistry registry = new AirObjectRegistry();
            RadarService radarService = new RadarServiceImpl(registry);

            LocateRegistry.createRegistry(1099);
            Naming.rebind("rmi://localhost:1099/RadarService", radarService);

            System.out.println("Radar RMI service started successfully.");
            System.out.println("RMI registry listening on port 1099.");
            System.out.println("Service name: RadarService");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}