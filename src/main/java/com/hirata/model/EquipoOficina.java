/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.model;

import java.sql.Date;

/**
 *
 * @author wadev
 */
public class EquipoOficina {
    private int id;
    private String sn;
    private int estado;
    private String marca;
    private String modelo;
    private Date fechaRegistro;
    private int idPersonal;
    private int idTipoEquipo;

    // Constructor vacío
    public EquipoOficina() {}

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSn() { return sn; }
    public void setSn(String sn) { this.sn = sn; }
    public int getEstado() { return estado; }
    public void setEstado(int estado) { this.estado = estado; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public Date getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(Date fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public int getIdPersonal() { return idPersonal; }
    public void setIdPersonal(int idPersonal) { this.idPersonal = idPersonal; }
    public int getIdTipoEquipo() { return idTipoEquipo; }
    public void setIdTipoEquipo(int idTipoEquipo) { this.idTipoEquipo = idTipoEquipo; }
}
