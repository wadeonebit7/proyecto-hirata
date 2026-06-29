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
public class ResumenViaje {
    private String patente;
    private String origen;
    private String destino;
    private double combustibleGastado;
    private int alertasTermicas;
    private Timestamp fechaViaje;
    private int frecuencia; // Variable extra para el top de rutas

    public ResumenViaje() {}

    // Getters y Setters
    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public double getCombustibleGastado() { return combustibleGastado; }
    public void setCombustibleGastado(double combustibleGastado) { this.combustibleGastado = combustibleGastado; }

    public int getAlertasTermicas() { return alertasTermicas; }
    public void setAlertasTermicas(int alertasTermicas) { this.alertasTermicas = alertasTermicas; }

    public Timestamp getFechaViaje() { return fechaViaje; }
    public void setFechaViaje(Timestamp fechaViaje) { this.fechaViaje = fechaViaje; }

    public int getFrecuencia() { return frecuencia; }
    public void setFrecuencia(int frecuencia) { this.frecuencia = frecuencia; }
}
