/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.model;

/**
 *
 * @author wadev
 */
public class Gps {
    private int id;
    private String numeroSerie;
    private String modeloHardware;
    private int idCamionFk;
    private int estado; // 1 = Activo, 0 = Inactivo

    public Gps() {}

    public Gps(int id, String numeroSerie, String modeloHardware, int idCamionFk, int estado) {
        this.id = id;
        this.numeroSerie = numeroSerie;
        this.modeloHardware = modeloHardware;
        this.idCamionFk = idCamionFk;
        this.estado = estado;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNumeroSerie() { return numeroSerie; }
    public void setNumeroSerie(String numeroSerie) { this.numeroSerie = numeroSerie; }

    public String getModeloHardware() { return modeloHardware; }
    public void setModeloHardware(String modeloHardware) { this.modeloHardware = modeloHardware; }

    public int getIdCamionFk() { return idCamionFk; }
    public void setIdCamionFk(int idCamionFk) { this.idCamionFk = idCamionFk; }

    public int getEstado() { return estado; }
    public void setEstado(int estado) { this.estado = estado; }
}
