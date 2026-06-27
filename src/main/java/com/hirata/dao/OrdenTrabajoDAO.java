/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.dao;

import com.hirata.model.MySQLConexion;
import com.hirata.model.OrdenTrabajo;
import java.sql.*;

/**
 *
 * @author wadev
 */
public class OrdenTrabajoDAO {
    // RF-06: Registrar apertura de una nueva orden de trabajo
    public int insertarOrden(OrdenTrabajo ot) {
        String sql = "INSERT INTO orden_trabajo (tipo_mantenimiento, fecha, firma_cliente, motivo, id_equipo, id_tecnico_responsable, id_firma_tecnico) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = MySQLConexion.getConexion();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, ot.getTipoMantenimiento());
            ps.setDate(2, ot.getFecha());
            ps.setString(3, ot.getFirmaCliente());
            ps.setString(4, ot.getMotivo());
            ps.setInt(5, ot.getIdEquipo());
            ps.setInt(6, ot.getIdTecnicoResponsable());
            ps.setInt(7, ot.getIdFirmaTecnico());
            
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1); // Retorna el ID de la OT generada
            }
        } catch (SQLException e) {
            System.err.println("Error OrdenTrabajoDAO.insertarOrden: " + e.getMessage());
        }
        return -1;
    }

    // RF-06 / RF-07: Cierre completo aplicando los checklists mediante el SP
    public boolean cerrarOrdenCompleta(int idOt, boolean actVersiones, boolean evalPc, boolean copiaSeg, 
                                       boolean actOfimatica, boolean actAntivirus, boolean actSoftImp, String obsHw,
                                       boolean carcasa, boolean pantalla, boolean teclado, boolean touchpad, boolean puertosUsb) {
        String sql = "{CALL sp_cerrar_orden(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
        try (Connection con = MySQLConexion.getConexion();
             CallableStatement cs = con.prepareCall(sql)) {
            
            cs.setInt(1, idOt);
            cs.setBoolean(2, actVersiones);
            cs.setBoolean(3, evalPc);
            cs.setBoolean(4, copiaSeg);
            cs.setBoolean(5, actOfimatica);
            cs.setBoolean(6, actAntivirus);
            cs.setBoolean(7, actSoftImp);
            cs.setString(8, obsHw);
            
            // Checklists físicos de salida
            cs.setBoolean(9, carcasa);
            cs.setBoolean(10, pantalla);
            cs.setBoolean(11, teclado);
            cs.setBoolean(12, touchpad);
            cs.setBoolean(13, puertosUsb);
            
            // Elementos de herramientas estándar por defecto
            cs.setBoolean(14, true); // destornilladores
            cs.setBoolean(15, true); // brocha
            cs.setBoolean(16, true); // alcohol_iso
            cs.setBoolean(17, true); // pasta_termica

            cs.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("Error OrdenTrabajoDAO.cerrarOrdenCompleta: " + e.getMessage());
            return false;
        }
    }
}
