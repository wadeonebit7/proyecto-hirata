/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.model;

/**
 *
 * @author wadev
 */
public class Camion {
    private int id;
    private String estado;
    private int idPersonalFk;
    private String patente;
    private String marca;
    private String modelo;
    private int anio;
    private int kmActual;

    // Constructor vacío obligatorio
    public Camion() {}

    // Constructor completo actualizado para el DAO
    public Camion(int id, String estado, int idPersonalFk, String patente, String marca, String modelo, int anio, int kmActual) {
        this.id = id;
        this.estado = estado;
        this.idPersonalFk = idPersonalFk;
        this.patente = patente;
        this.marca = marca;
        this.modelo = modelo;
        this.anio = anio;
        this.kmActual = kmActual;
    }

    // --- GETTERS Y SETTERS ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public int getIdPersonalFk() { return idPersonalFk; }
    public void setIdPersonalFk(int idPersonalFk) { this.idPersonalFk = idPersonalFk; }

    public String getPatente() { return patente; }
    public void setPatente(String patente) { this.patente = patente; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public int getKilometrajeAcumulado() { return kmActual; }
    public void setKilometrajeAcumulado(int kmActual) { this.kmActual = kmActual; }
}