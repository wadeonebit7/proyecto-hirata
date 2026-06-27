/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.hirata.dao;

import com.hirata.model.MySQLConexion;
import com.hirata.model.Componente;
import java.sql.*;

/**
 *
 * @author wadev
 */
public class ComponenteDAO {
    // Llama al procedimiento almacenado para intercambiar piezas físicas
    public boolean registrarCambioComponente(int idOt, Integer idCompAnt, int idCompNuevo, String obs, String estadoAnt) {
        String sql = "{CALL sp_cambio_componente(?, ?, ?, ?, ?)}";
        try (Connection con = MySQLConexion.getConexion();
             CallableStatement cs = con.prepareCall(sql)) {
            
            cs.setInt(1, idOt);
            if (idCompAnt == null) {
                cs.setNull(2, Types.INTEGER);
            } else {
                cs.setInt(2, idCompAnt);
            }
            cs.setInt(3, idCompNuevo);
            cs.setString(4, obs);
            cs.setString(5, estadoAnt); // 'disponible' o 'baja'
            
            cs.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("Error ComponenteDAO.registrarCambioComponente: " + e.getMessage());
            return false;
        }
    }
}
