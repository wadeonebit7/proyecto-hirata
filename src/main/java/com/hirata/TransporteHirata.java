/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.hirata;

import com.hirata.controller.ControladorLogin;
import com.hirata.model.Sesion;
import com.hirata.view.LoginView;
import com.hirata.view.PanelConductor;
import java.util.List;

/**
 *
 * @author wadev
 */
public class TransporteHirata {

    public static void main(String[] args) {
        String user = "acortes";
        String pass = "456";

        ControladorLogin cl = new ControladorLogin();
        String respuesta = cl.acceder(user, pass);
        
        java.awt.EventQueue.invokeLater(() -> {
            new PanelConductor().setVisible(true);
        });
        
        String nombre = Sesion.nombre;
        System.out.println(nombre);
    }
}
