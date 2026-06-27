/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.controller;

import com.hirata.dao.EquipoDAO;
import com.hirata.dao.ComponenteDAO;
import com.hirata.dao.OrdenTrabajoDAO;
import com.hirata.model.EquipoOficina;
import com.hirata.model.OrdenTrabajo;
import com.hirata.model.MySQLConexion;
import java.sql.*;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author wadev
 */
public class ControladorEquipos extends ControladorBase{
    // Instanciamos los DAOs necesarios para interactuar con la persistencia
    private final EquipoDAO equipoDAO = new EquipoDAO();
    private final ComponenteDAO componenteDAO = new ComponenteDAO();
    private final OrdenTrabajoDAO ordenTrabajoDAO = new OrdenTrabajoDAO();

    // RF-08: El Administrador consulta el historial consumiendo la VISTA 'v_ordenes_trabajo'
    @Override
    public DefaultTableModel verHistorial(int idVacio) {
        String[] columnas = {"ID OT", "Estado", "Tipo Maint.", "Fecha", "Equipo SN", "Marca", "Técnico"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0);
        
        String sql = "SELECT id_ot, estado, tipo_mantenimiento, fecha, equipo_sn, equipo_marca, tecnico_responsable FROM v_ordenes_trabajo";

        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                modelo.addRow(new Object[]{
                    rs.getInt("id_ot"), rs.getString("estado"), rs.getString("tipo_mantenimiento"),
                    rs.getDate("fecha"), rs.getString("equipo_sn"), rs.getString("equipo_marca"),
                    rs.getString("tecnico_responsable")
                });
            }
        } catch (SQLException e) {
            System.err.println("Error al cargar historial desde vista: " + e.getMessage());
        }
        return modelo;
    }

    // RF-09: El Administrador de inventario ve el stock disponible consumiendo la VISTA 'v_bodega'
    @Override
    public DefaultTableModel verAlertas(int idVacio) {
        String[] columnas = {"ID", "S/N Componente", "Categoría", "Marca", "Modelo", "Capacidad"};
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0);
        String sql = "SELECT id, sn, categoria, marca, modelo, capacidad FROM v_bodega";
        
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                modelo.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("sn"), rs.getString("categoria"),
                    rs.getString("marca"), rs.getString("modelo"), rs.getString("capacidad")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return modelo;
    }

    // Lógica de negocio para buscar equipo (Usa el DAO y maneja excepciones)
    public EquipoOficina verificarYBuscarEquipo(String sn) {
        if (sn == null || sn.trim().isEmpty()) {
            return null;
        }
        return equipoDAO.buscarPorSN(sn.trim().toUpperCase());
    }

    // RF-06: Lógica para abrir una nueva Orden de Trabajo
    public int abrirOrdenTrabajo(String tipoMant, String firmaCliente, String motivo, String snEquipo, int idTecnico, int idCreador) {
        // Regla de negocio: Validar que el equipo exista primero (Excepción RF-06)
        EquipoOficina eq = equipoDAO.buscarPorSN(snEquipo);
        if (eq == null) {
            return -1; // Indica que el equipo no está registrado
        }

        // Construimos el modelo con los datos validados
        OrdenTrabajo ot = new OrdenTrabajo();
        ot.setTipoMantenimiento(tipoMant);
        ot.setFecha(new java.sql.Date(System.currentTimeMillis()));
        ot.setFirmaCliente(firmaCliente);
        ot.setMotivo(motivo);
        ot.setIdEquipo(eq.getId());
        ot.setIdTecnicoResponsable(idTecnico);
        ot.setIdFirmaTecnico(idCreador);

        // Delegamos el guardado puro al DAO
        return ordenTrabajoDAO.insertarOrden(ot);
    }

    // RF-09: Control de cambio de piezas invocando al DAO
    public boolean gestionarCambioComponente(int idOt, Integer idCompAnt, int idCompNuevo, String obs, String estadoAnt) {
        // Regla de negocio: Validaciones previas de IDs si fuesen necesarias
        if (idOt <= 0 || idCompNuevo <= 0) return false;
        
        return componenteDAO.registrarCambioComponente(idOt, idCompAnt, idCompNuevo, obs, estadoAnt);
    }

    // RF-06 / RF-07: Lógica para el cierre regular invocando al DAO
    public boolean gestionarCierreOrden(int idOt, boolean actVersiones, boolean evalPc, boolean copiaSeg, 
                                        boolean actOfimatica, boolean actAntivirus, boolean actSoftImp, String obsHw,
                                        boolean carcasa, boolean pantalla, boolean teclado, boolean touchpad, boolean puertosUsb) {
        if (idOt <= 0) return false;
        
        return ordenTrabajoDAO.cerrarOrdenCompleta(idOt, actVersiones, evalPc, copiaSeg, actOfimatica, actAntivirus, actSoftImp, obsHw, carcasa, pantalla, teclado, touchpad, puertosUsb);
    }
}
