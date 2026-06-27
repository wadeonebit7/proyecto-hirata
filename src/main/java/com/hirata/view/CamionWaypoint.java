/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.view;

import java.awt.Color;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;

/**
 *
 * @author wadev
 */
public class CamionWaypoint extends DefaultWaypoint {
    private final int idGps;
    private final String patente;
    private final double combustible;
    private final double temperatura;

    public CamionWaypoint(int idGps, String patente, double lat, double lon, double combustible, double temperatura) {
        super(new GeoPosition(lat, lon));
        this.idGps = idGps;
        this.patente = patente;
        this.combustible = combustible;
        this.temperatura = temperatura;
    }

    // Getters para recuperar la telemetría al hacer click
    public int getIdGps() { return idGps; }
    public String getPatente() { return patente; }
    public double getCombustible() { return combustible; }
    public double getTemperatura() { return temperatura; }

    /**
     * Determina el color del camión según la regla del RF-13 (Cadena de frío).
     */
    public Color getColorEstado() {
        return (this.temperatura > 5.0) ? Color.RED : new Color(0, 102, 204);
    }
}
