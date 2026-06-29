/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.model;

import java.sql.Timestamp;

/**
 *
 * @author wadev
 */
public class TelemetriaRuta {
    private int id;
    private int idGpsFk;
    private String patente;
    private Timestamp fechaHora;
    private double latitud;
    private double longitud;
    private double consumoCombustible;
    private double temperaturaMotor;
    private double temperaturaCelsius; // Atributo para el control de cadena de frío (RF-13)
    
    private String nombreChofer;
    
    private String marcaCamion;
    private String modeloCamion;
    
    private int segundosAtras;
    
    private String numeroSerieGps;
    

    // Constructor vacío
    public TelemetriaRuta() {}

    // Constructor completo para uso en DAOs y Emuladores
    public TelemetriaRuta(int id, int idGpsFk, String patente, Timestamp fechaHora, double latitud, double longitud, 
                          double consumoCombustible, double temperaturaMotor, double temperaturaCelsius) {
        this.id = id;
        this.idGpsFk = idGpsFk;
        this.patente = patente;
        this.fechaHora = fechaHora;
        this.latitud = latitud;
        this.longitud = longitud;
        this.consumoCombustible = consumoCombustible;
        this.temperaturaMotor = temperaturaMotor;
        this.temperaturaCelsius = temperaturaCelsius;
    }

    // --- GETTERS Y SETTERS ---
    
    public int getSegundosAtras() { return segundosAtras; }
    public void setSegundosAtras(int segundosAtras) { this.segundosAtras = segundosAtras; }
    
    
    public String getMarcaCamion() { return marcaCamion; }
    public void setMarcaCamion(String marcaCamion) { this.marcaCamion = marcaCamion; }

    public String getModeloCamion() { return modeloCamion; }
    public void setModeloCamion(String modeloCamion) { this.modeloCamion = modeloCamion; }
    
    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdGpsFk() { return idGpsFk; }
    public void setIdGpsFk(int idGpsFk) { this.idGpsFk = idGpsFk; }
    
    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }

    public Timestamp getFechaHora() { return fechaHora; }
    public void setFechaHora(Timestamp fechaHora) { this.fechaHora = fechaHora; }

    public double getLatitud() { return latitud; }
    public void setLatitud(double latitud) { this.latitud = latitud; }

    public double getLongitud() { return longitud; }
    public void setLongitud(double longitud) { this.longitud = longitud; }

    public double getConsumoCombustible() { return consumoCombustible; }
    public void setConsumoCombustible(double consumoCombustible) { this.consumoCombustible = consumoCombustible; }

    
    public double getTemperaturaMotor() { return temperaturaMotor; }
    public void setTemperaturaMotor(double temperaturaMotor) { this.temperaturaMotor = temperaturaMotor; }

    public double getTemperaturaCelsius() { return temperaturaCelsius; }
    public void setTemperaturaCelsius(double temperaturaCelsius) { this.temperaturaCelsius = temperaturaCelsius; }
    
    
    public String getNombreChofer() { return nombreChofer; }
    public void setNombreChofer(String nombreChofer) { this.nombreChofer = nombreChofer; }
    
    
    public String getNumeroSerieGps() { return numeroSerieGps; }
    public void setNumeroSerieGps(String numeroSerieGps) { this.numeroSerieGps = numeroSerieGps; }
}
