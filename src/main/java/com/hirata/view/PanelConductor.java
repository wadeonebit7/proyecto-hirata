/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.hirata.view;

import com.hirata.controller.ControladorConductor;
import com.hirata.controller.ControladorTelemetria;
import com.hirata.model.Camion;
import com.hirata.model.Sesion;
import com.hirata.service.ServicioEnrutamiento;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import javax.swing.JOptionPane;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;


/**
 *
 * @author wadev
 */
public class PanelConductor extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(PanelConductor.class.getName());
    private javax.swing.Timer timerAnimacionConductor;
    
    ControladorConductor ctrl = new ControladorConductor();
    private JXMapViewer mapaConductor;
    private List<GeoPosition> rutaCalculada;
    private Thread hiloSimulacion;
    private ControladorTelemetria emulador;
    
    // Coordenada base fija de salida: Cavancha, Iquique [cite: 26]
    private final GeoPosition origenBase = new GeoPosition(-20.23950915893488, -70.14480829238892);
    private GeoPosition destinoSeleccionado = null;
    
    
    private CamionWaypoint waypointCamionLocal = null; // Guarda el waypoint visual del camión seleccionado por el chofer
     
    private org.jxmapviewer.viewer.GeoPosition origenCalculoRuta; // Almacena el punto de partida real del próximo viaje (puede ser la base o el último destino)
    
    /**
     * Creates new form PanelConductor
     */
    public PanelConductor() {
        initComponents();
        this.setLocationRelativeTo(null);
        inicializarMapaConductor();
        cargarDestinosPrestablecidos();
        cargarDatos();
    }
    
    private void inicializarMapaConductor() {
        mapaConductor = new JXMapViewer();
        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        System.setProperty("http.agent", "JXMapViewer2-TransporteHirata-v1.0");

        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        mapaConductor.setTileFactory(tileFactory);
        
        mapaConductor.setZoom(4); 
        mapaConductor.setAddressLocation(origenBase); // Posición oficial inicial Hirata 
        
        // Movimiento de mapa nativo
        org.jxmapviewer.input.PanMouseInputListener pamil = new org.jxmapviewer.input.PanMouseInputListener(mapaConductor);
        mapaConductor.addMouseListener(pamil);
        mapaConductor.addMouseMotionListener(pamil);
        
        // Zoom nativo
        mapaConductor.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                int nuevoZoom = mapaConductor.getZoom() + e.getWheelRotation();
                if (nuevoZoom >= 1 && nuevoZoom <= 15) {
                    mapaConductor.setZoom(nuevoZoom);
                    mapaConductor.repaint();
                }
            }
        });
        
        // EVENTO CLIC EN EL MAPA REESTRUCTURADO CON VALIDACIONES
        mapaConductor.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getButton() == java.awt.event.MouseEvent.BUTTON1) {
                    
                    // VALIDACIÓN 1: No se puede seleccionar destino sin un vehículo primero
                    if (cmbCamionesAsignados.getSelectedIndex() <= 0) {
                        javax.swing.JOptionPane.showMessageDialog(null, "Debe seleccionar un vehículo primero antes de marcar un destino.", "Validación", javax.swing.JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    
                    // VALIDACIÓN 2: Una vez comenzado el viaje, se bloquea el cálculo de rutas en el mapa
                    if (emulador != null && !btnIniciarViaje.isEnabled()) {
                        return; 
                    }

                    java.awt.Point clickPoint = e.getPoint();
                    destinoSeleccionado = mapaConductor.convertPointToGeoPosition(clickPoint);
                    actualizarDestinoEnRuta();
                }
            }
        });
        
        mapaConductor.setPreferredSize(new java.awt.Dimension(600, 500)); 
        jfxContainerConductor.setLayout(new BorderLayout());
        jfxContainerConductor.add(mapaConductor, BorderLayout.CENTER);
    }

    private void cargarDestinosPrestablecidos() {
        cmbDestinosPredeterminados.removeAllItems();
        cmbDestinosPredeterminados.addItem("Seleccione Destino");
        cmbDestinosPredeterminados.addItem("ZOFRI Recinto Central");
        cmbDestinosPredeterminados.addItem("Puerto de Iquique Terminal");
        cmbDestinosPredeterminados.addItem("Playa Brava Sector Norte");
    }

    // Método que se activa cuando el chofer usa el click o el ComboBox
    private void actualizarDestinoEnRuta() {
        if (destinoSeleccionado == null || origenCalculoRuta == null) return;
        
        System.out.println("[Conductor] Calculando camino vial desde posición actual hacia: " + destinoSeleccionado);
        
        rutaCalculada = ServicioEnrutamiento.calcularRutaCalles(this.origenCalculoRuta, destinoSeleccionado);

        if (rutaCalculada != null && !rutaCalculada.isEmpty()) {
            lblEstadoConductor.setText("Ruta establecida. Nodos viales: " + rutaCalculada.size());
            lblEstadoConductor.setForeground(new java.awt.Color(0, 102, 204));

            // Refrescamos las capas para incorporar la nueva línea calculada al vuelo
            actualizarCapasMapaConductor();
            mapaConductor.setAddressLocation(destinoSeleccionado);
        } else {
            lblEstadoConductor.setText("Error: No se pudo trazar la ruta en este sector.");
            lblEstadoConductor.setForeground(java.awt.Color.RED);
        }
    }

    /**
     * Une el camión seleccionado y la ruta vial en el lienzo gráfico sin interferencias.
     */
    private void actualizarCapasMapaConductor() {
        java.util.List<org.jxmapviewer.painter.Painter<JXMapViewer>> listaPainters = new java.util.ArrayList<>();

        // 1. CAPA DE LÍNEA: Si ya calculó ruta, agregamos el pintor del camino azul
        if (rutaCalculada != null && !rutaCalculada.isEmpty()) {
            listaPainters.add(new org.jxmapviewer.painter.Painter<JXMapViewer>() {
                @Override
                public void paint(java.awt.Graphics2D g, JXMapViewer map, int w, int h) {
                    g.setColor(new java.awt.Color(30, 144, 255, 180)); // Azul conductor
                    g.setStroke(new java.awt.BasicStroke(3.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

                    int[] puntosX = new int[rutaCalculada.size()];
                    int[] puntosY = new int[rutaCalculada.size()];

                    for (int i = 0; i < rutaCalculada.size(); i++) {
                        java.awt.geom.Point2D pixel = map.getTileFactory().geoToPixel(rutaCalculada.get(i), map.getZoom());
                        java.awt.Rectangle rect = map.getViewportBounds();
                        puntosX[i] = (int) pixel.getX() - rect.x;
                        puntosY[i] = (int) pixel.getY() - rect.y;
                    }
                    g.drawPolyline(puntosX, puntosY, rutaCalculada.size());
                }
            });
        }

        // 2. CAPA DE ICONO: Si seleccionó camión, lo pintamos usando el renderizador de figuras nativas
        if (waypointCamionLocal != null) {
            org.jxmapviewer.viewer.WaypointPainter<org.jxmapviewer.viewer.Waypoint> waypointPainter = new org.jxmapviewer.viewer.WaypointPainter<>();
            waypointPainter.setRenderer(new org.jxmapviewer.viewer.WaypointRenderer<org.jxmapviewer.viewer.Waypoint>() {
                @Override
                public void paintWaypoint(java.awt.Graphics2D g, JXMapViewer map, org.jxmapviewer.viewer.Waypoint wp) {
                    if (wp instanceof CamionWaypoint) {
                        CamionWaypoint c = (CamionWaypoint) wp;
                        java.awt.geom.Point2D p = map.getTileFactory().geoToPixel(c.getPosition(), map.getZoom());
                        int x = (int) p.getX();
                        int y = (int) p.getY();

                        g.setColor(c.getColorEstado()); // Azul o Rojo si falla la carga (RF-13)
                        g.fillRect(x - 12, y - 6, 22, 11); // Caja
                        g.fillRect(x + 10, y - 3, 6, 8);   // Cabina

                        g.setColor(Color.BLACK);
                        g.fillOval(x - 9, y + 5, 4, 4);    // Ruedas
                        g.fillOval(x + 2, y + 5, 4, 4);
                        g.fillOval(x + 10, y + 5, 4, 4);

                        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 10));
                        g.drawString(c.getPatente(), x - 15, y - 10); // Muestra la patente directo en el mapa
                    }
                }
            });

            java.util.Set<org.jxmapviewer.viewer.Waypoint> conjunto = new java.util.HashSet<>();
            conjunto.add(waypointCamionLocal);
            waypointPainter.setWaypoints(conjunto);
            listaPainters.add(waypointPainter);
        }

        // 3. Aplicar combinación final al visor
        org.jxmapviewer.painter.CompoundPainter<JXMapViewer> compuesto = new org.jxmapviewer.painter.CompoundPainter<>(listaPainters);
        mapaConductor.setOverlayPainter(compuesto);
        mapaConductor.repaint();
    }
    
    private void mostrarCamionEnOrigen() {
        // REGLA: Si presiona "Seleccionar Vehiculo", restablece destino y limpia el mapa por completo
        if (cmbCamionesAsignados.getSelectedIndex() <= 0) {
            cmbDestinosPredeterminados.setSelectedIndex(0); // Establece "Seleccione Destino"
            waypointCamionLocal = null;
            rutaCalculada = null;
            destinoSeleccionado = null;
            mapaConductor.setOverlayPainter(null);
            mapaConductor.repaint();
            return;
        }

        String patenteSeleccionada = cmbCamionesAsignados.getSelectedItem().toString();
        
        // 1. Recuperar el objeto Camion completo de forma segura
        Camion camionActual = ctrl.obtenerDatosCamionAdmin(patenteSeleccionada);
        
        // 2. VALIDACIÓN: Verificar que el objeto no haya llegado nulo desde MySQL
        if (camionActual == null) {
            javax.swing.JOptionPane.showMessageDialog(this, 
                "Error al recuperar los datos técnicos del vehículo.", 
                "Error de Consistencia", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 3. Extraer el ID de forma totalmente segura (¡Cero riesgos de fallas!)
        int idCamionReal = camionActual.getId();
        
        // 4. Continuar con la validación del GPS
        int idGpsVinculado = ctrl.obtenerIdGpsPorCamion(idCamionReal);

        if (idGpsVinculado == -1) {
            // Alerta al conductor
            javax.swing.JOptionPane.showMessageDialog(this, 
                "ERROR CRÍTICO: El vehículo " + patenteSeleccionada + " no cuenta con un dispositivo GPS asignado.\n" +
                "El mapa se bloqueará por seguridad operativa.", 
                "Falta de Hardware IoT", javax.swing.JOptionPane.ERROR_MESSAGE);

            // Bloquear y limpiar todo el entorno visual
            cmbDestinosPredeterminados.setSelectedIndex(0);
            waypointCamionLocal = null;
            rutaCalculada = null;
            destinoSeleccionado = null;
            mapaConductor.setOverlayPainter(null);
            mapaConductor.repaint();
            cmbCamionesAsignados.setSelectedIndex(0); // Regresar el combo de camiones al índice cero para forzar a elegir uno válido
            return;
        }
        
        // 2. Si pasa la validación del GPS, buscar su historial o posición inicial [-20.23950915, -70.14480829] 
        GeoPosition ultimaPosicionBD = ctrl.obtenerUltimaPosicionCamion(patenteSeleccionada);
        this.origenCalculoRuta = (ultimaPosicionBD != null) ? ultimaPosicionBD : origenBase; // Asignar el origen dinámico según el estado real de la BD

        waypointCamionLocal = new CamionWaypoint(
            idGpsVinculado, 
            patenteSeleccionada, 
            this.origenCalculoRuta.getLatitude(), 
            this.origenCalculoRuta.getLongitude(), 
            100.0, 
            2.5
        );

        mapaConductor.setAddressLocation(this.origenCalculoRuta);
        actualizarCapasMapaConductor();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jfxContainerConductor = new javax.swing.JPanel();
        cmbCamionesAsignados = new javax.swing.JComboBox<>();
        cmbDestinosPredeterminados = new javax.swing.JComboBox<>();
        btnIniciarViaje = new javax.swing.JButton();
        btnCancelarViaje = new javax.swing.JButton();
        txtCombustibleSim = new javax.swing.JLabel();
        txtTemperaturaSim = new javax.swing.JLabel();
        lblEstadoConductor = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jfxContainerConductor.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jfxContainerConductor.setMinimumSize(new java.awt.Dimension(600, 500));
        jfxContainerConductor.setName(""); // NOI18N
        jfxContainerConductor.setPreferredSize(new java.awt.Dimension(600, 500));

        javax.swing.GroupLayout jfxContainerConductorLayout = new javax.swing.GroupLayout(jfxContainerConductor);
        jfxContainerConductor.setLayout(jfxContainerConductorLayout);
        jfxContainerConductorLayout.setHorizontalGroup(
            jfxContainerConductorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jfxContainerConductorLayout.setVerticalGroup(
            jfxContainerConductorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 496, Short.MAX_VALUE)
        );

        cmbCamionesAsignados.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbCamionesAsignados.addActionListener(this::cmbCamionesAsignadosActionPerformed);

        cmbDestinosPredeterminados.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbDestinosPredeterminados.addActionListener(this::cmbDestinosPredeterminadosActionPerformed);

        btnIniciarViaje.setText("Iniciar Viaje");
        btnIniciarViaje.addActionListener(this::btnIniciarViajeActionPerformed);

        btnCancelarViaje.setText("Cancelar Viaje");
        btnCancelarViaje.addActionListener(this::btnCancelarViajeActionPerformed);

        txtCombustibleSim.setText("Combustible");

        txtTemperaturaSim.setText("Temperatura");

        lblEstadoConductor.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblEstadoConductor.setText("Estado Conductor");

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("NO SE QUE PONER DE TITULO");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(cmbDestinosPredeterminados, 0, 187, Short.MAX_VALUE)
                            .addComponent(cmbCamionesAsignados, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(txtCombustibleSim, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnCancelarViaje, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnIniciarViaje, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(12, 12, 12))
            .addGroup(layout.createSequentialGroup()
                .addGap(158, 158, 158)
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 282, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 172, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(txtTemperaturaSim, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblEstadoConductor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jfxContainerConductor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(6, 6, 6))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel1)
                        .addGap(22, 22, 22)
                        .addComponent(cmbCamionesAsignados, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cmbDestinosPredeterminados, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(44, 44, 44)
                        .addComponent(btnIniciarViaje, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnCancelarViaje)
                    .addComponent(txtCombustibleSim))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblEstadoConductor)
                    .addComponent(txtTemperaturaSim))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jfxContainerConductor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    // ACCIÓN DEL COMBOBOX DE DESTINOS
    private void cmbDestinosPredeterminadosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbDestinosPredeterminadosActionPerformed
        // TODO add your handling code here:
        if (cmbCamionesAsignados.getSelectedIndex() <= 0) {
            if (cmbDestinosPredeterminados.getSelectedIndex() > 0) {
                cmbDestinosPredeterminados.setSelectedIndex(0);
                javax.swing.JOptionPane.showMessageDialog(this, "Debe seleccionar un vehículo primero.", "Validación", javax.swing.JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        
        // Bloquear si ya está viajando
        if (emulador != null && !btnIniciarViaje.isEnabled()) return;

        int index = cmbDestinosPredeterminados.getSelectedIndex();
        if (index <= 0) return; 
        
        if (index == 1) destinoSeleccionado = new GeoPosition(-20.20521000, -70.13425000); // ZOFRI
        if (index == 2) destinoSeleccionado = new GeoPosition(-20.20110000, -70.13910000); // Puerto
        if (index == 3) destinoSeleccionado = new GeoPosition(-20.22415000, -70.15340000); // Playa Brava
        
        actualizarDestinoEnRuta();
    }//GEN-LAST:event_cmbDestinosPredeterminadosActionPerformed

    private void cmbCamionesAsignadosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbCamionesAsignadosActionPerformed
        // TODO add your handling code here:
        mostrarCamionEnOrigen();
    }//GEN-LAST:event_cmbCamionesAsignadosActionPerformed
    // BOTÓN: INICIAR RECORRIDO
                                            

    // BOTÓN: CANCELAR / DETENER RECORRIDO
    private void btnIniciarViajeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIniciarViajeActionPerformed
        // TODO add your handling code here:
        // 1. Validaciones básicas de selección
        if (cmbCamionesAsignados.getSelectedIndex() <= 0) {
            javax.swing.JOptionPane.showMessageDialog(this, "Debe seleccionar un camión asignado antes de arrancar.", "Atención", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (destinoSeleccionado == null || rutaCalculada == null || rutaCalculada.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this, "Debe marcar un punto de destino válido en el mapa o combo.", "Atención", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        String patenteSeleccionada = cmbCamionesAsignados.getSelectedItem().toString();
        
        Camion camionActual = ctrl.obtenerDatosCamionAdmin(patenteSeleccionada);

        if (camionActual == null) {
            javax.swing.JOptionPane.showMessageDialog(this, 
                "Error crítico al recuperar los datos del vehículo para la simulación.", 
                "Error de Consistencia", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Extraemos el ID dinámico de manera 100% segura
        int idCamionReal = camionActual.getId();

        // 2. Inicializar y arrancar el Emulador IoT con el ID real
        emulador = new ControladorTelemetria(idCamionReal, rutaCalculada);
        emulador.setPatente(patenteSeleccionada);
        
        hiloSimulacion = new Thread(emulador);
        hiloSimulacion.start();

        // 3. Temporizador de animación gráfica local para mover el camión cada 3s
        timerAnimacionConductor = new javax.swing.Timer(3000, e -> {
            if (emulador != null && emulador.isEnRuta()) {
                GeoPosition posActual = new GeoPosition(emulador.getLatActual(), emulador.getLonActual()); // Le pedimos al emulador su posición actual cambiante
                
                // Movemos el marcador del camión sobre el mapa
                waypointCamionLocal = new CamionWaypoint(
                    camionActual.getId(), patenteSeleccionada, posActual.getLatitude(), posActual.getLongitude(),
                    emulador.getCombustibleActual(), emulador.getTemperaturaCarga() // Muestra datos en vivo
                );
                
                // RECOMENDADO: Hace que la cámara del mapa siga al camión en movimiento
                mapaConductor.setAddressLocation(posActual);
                
                actualizarCapasMapaConductor();
            } else {
                if (emulador != null) {
                    this.origenCalculoRuta = new GeoPosition(emulador.getLatActual(), emulador.getLonActual());
                }
                btnCancelarViajeActionPerformed(null); // Si el emulador llegó a su destino, apagar el viaje y habilitar el mapa de nuevo
            }
        });
        timerAnimacionConductor.start();

        // 4. Conmutación de estados visuales seguros
        btnIniciarViaje.setEnabled(false);
        btnCancelarViaje.setEnabled(true);
        cmbCamionesAsignados.setEnabled(false); 
        cmbDestinosPredeterminados.setEnabled(false);
        lblEstadoConductor.setText("Viaje en progreso. Controles de mapa bloqueados.");
    }//GEN-LAST:event_btnIniciarViajeActionPerformed

    private void btnCancelarViajeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelarViajeActionPerformed
        // TODO add your handling code here:
        if (emulador != null) {
            emulador.detenerSimulacion();
        }
        if (timerAnimacionConductor != null) {
            timerAnimacionConductor.stop(); // Apagar animación visual
        }
        
        // Habilitar controles nuevamente
        btnIniciarViaje.setEnabled(true);
        btnCancelarViaje.setEnabled(false);
        cmbCamionesAsignados.setEnabled(true);
        cmbDestinosPredeterminados.setEnabled(true);
        lblEstadoConductor.setText("Viaje Terminado/Cancelado. Controles desbloqueados.");
    }//GEN-LAST:event_btnCancelarViajeActionPerformed

    private void cargarDatos() {
        
        // 1. Llenar el ComboBox de camiones asignados
        cmbCamionesAsignados.removeAllItems();
        cmbCamionesAsignados.addItem("Seleccionar Vehiculo");
        for (String patente : ctrl.obtenerCamionesAsignados(Sesion.idPersonal)) {
            cmbCamionesAsignados.addItem(patente);
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new PanelConductor().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancelarViaje;
    private javax.swing.JButton btnIniciarViaje;
    private javax.swing.JComboBox<String> cmbCamionesAsignados;
    private javax.swing.JComboBox<String> cmbDestinosPredeterminados;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jfxContainerConductor;
    private javax.swing.JLabel lblEstadoConductor;
    private javax.swing.JLabel txtCombustibleSim;
    private javax.swing.JLabel txtTemperaturaSim;
    // End of variables declaration//GEN-END:variables
}
