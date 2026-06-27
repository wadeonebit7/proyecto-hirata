/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.model;

/**
 *
 * @author wadev
 */
public class Componente {
    private int id;
    private String sn;
    private int idCategoriaComp;
    private String marca;
    private String modelo;
    private String capacidad;
    private String estado; // 'disponible', 'instalado', 'baja'

    public Componente() {}

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public int getIdCategoriaComp() { return idCategoriaComp; }
    public void setIdCategoriaComp(int idCategoriaComp) { this.idCategoriaComp = idCategoriaComp; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public String getCapacidad() { return capacidad; }
    public void setCapacidad(String capacidad) { this.capacidad = capacidad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
