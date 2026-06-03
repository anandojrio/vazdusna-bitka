package org.example.radar.service;

import org.example.common.dto.RadarScanResponse;
import org.example.common.dto.RadarUpdateRequest;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RadarService extends Remote {

    RadarScanResponse updateAndScan(RadarUpdateRequest request) throws RemoteException;

    void unregister(String id) throws RemoteException;
}
