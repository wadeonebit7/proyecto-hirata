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
public class OrdenTrabajo {
    private int id;
    private String estado; // 'abierta', 'cerrada', 'cancelada'
    private String tipoMantenimiento; // 'preventivo', 'correctivo'
    private Date fecha;
    private String firmaCliente; // RUT
    private String motivo;
    private int idEquipo;
    private int idTecnicoResponsable;
    private int idFirmaTecnico;

    public OrdenTrabajo() {}

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTipoMantenimiento() { return tipoMantenimiento; }
    public void setTipoMantenimiento(String tipoMantenimiento) { this.tipoMantenimiento = tipoMantenimiento; }
    public Date getFecha() { return fecha; }
    public void setFecha(Date fecha) { this.fecha = fecha; }
    public String getFirmaCliente() { return firmaCliente; }
    public void setFirmaCliente(String firmaCliente) { this.firmaCliente = firmaCliente; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public int getIdEquipo() { return idEquipo; }
    public void setIdEquipo(int idEquipo) { this.idEquipo = idEquipo; }
    public int getIdTecnicoResponsable() { return idTecnicoResponsable; }
    public void setIdTecnicoResponsable(int idTecnicoResponsable) { this.idTecnicoResponsable = idTecnicoResponsable; }
    public int getIdFirmaTecnico() { return idFirmaTecnico; }
    public void setIdFirmaTecnico(int idFirmaTecnico) { this.idFirmaTecnico = idFirmaTecnico; }
}
