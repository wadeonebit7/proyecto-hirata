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
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.viewer.WaypointRenderer;


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
    
    // Coordenada base fija de salida: Cavancha, Iquique
    private final GeoPosition origenBase = new GeoPosition(-20.23950915893488, -70.14480829238892);
    private GeoPosition destinoSeleccionado = null;
    
    
    private CamionWaypoint waypointCamionLocal = null; // Guarda el waypoint visual del camión seleccionado por el chofer
     
    private GeoPosition origenCalculoRuta; // Almacena el punto de partida real del próximo viaje (puede ser la base o el último destino)
    
    
    
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
        java.util.List<Painter<JXMapViewer>> listaPainters = new ArrayList<>();

        // 1. CAPA DE LÍNEA: Si ya calculó ruta, agregamos el pintor del camino azul
        if (rutaCalculada != null && !rutaCalculada.isEmpty()) {
            listaPainters.add(new Painter<JXMapViewer>() {
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
            WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
            waypointPainter.setRenderer(new WaypointRenderer<Waypoint>() {
                @Override
                public void paintWaypoint(Graphics2D g, JXMapViewer map, Waypoint wp) {
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
        lblEstadoConductor = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        txtTemperaturaSim1 = new javax.swing.JLabel();
        txtCombustibleSim1 = new javax.swing.JLabel();
        btnIniciarViaje = new javax.swing.JButton();
        cmbCamionesAsignados = new javax.swing.JComboBox<>();
        cmbDestinosPredeterminados = new javax.swing.JComboBox<>();
        btnCancelarViaje = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jButton6 = new javax.swing.JButton();
        txtCombustibleSim = new javax.swing.JLabel();
        txtTemperaturaSim = new javax.swing.JLabel();
        txtTemperaturaSim2 = new javax.swing.JLabel();
        jSlider1 = new javax.swing.JSlider();
        jSlider2 = new javax.swing.JSlider();
        jSeparator1 = new javax.swing.JSeparator();
        jSeparator2 = new javax.swing.JSeparator();
        txtTemperaturaSim3 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jfxContainerConductor.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jfxContainerConductor.setMinimumSize(new java.awt.Dimension(600, 500));
        jfxContainerConductor.setName(""); // NOI18N
        jfxContainerConductor.setPreferredSize(new java.awt.Dimension(600, 500));

        javax.swing.GroupLayout jfxContainerConductorLayout = new javax.swing.GroupLayout(jfxContainerConductor);
        jfxContainerConductor.setLayout(jfxContainerConductorLayout);
        jfxContainerConductorLayout.setHorizontalGroup(
            jfxContainerConductorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 596, Short.MAX_VALUE)
        );
        jfxContainerConductorLayout.setVerticalGroup(
            jfxContainerConductorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        lblEstadoConductor.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblEstadoConductor.setText("Estado Conductor");

        jLabel1.setFont(new java.awt.Font("Arial", 1, 24)); // NOI18N
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("SIMULADOR CONDUCTOR - GPS");

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        txtTemperaturaSim1.setText("Temperatura:");

        txtCombustibleSim1.setText("Combustible:");

        btnIniciarViaje.setText("Iniciar");
        btnIniciarViaje.addActionListener(this::btnIniciarViajeActionPerformed);

        cmbCamionesAsignados.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbCamionesAsignados.addActionListener(this::cmbCamionesAsignadosActionPerformed);

        cmbDestinosPredeterminados.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbDestinosPredeterminados.addActionListener(this::cmbDestinosPredeterminadosActionPerformed);

        btnCancelarViaje.setText("Cancelar");
        btnCancelarViaje.addActionListener(this::btnCancelarViajeActionPerformed);

        jLabel2.setFont(new java.awt.Font("Arial", 1, 14)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("SIMULAR SITUACIONES");

        jLabel3.setFont(new java.awt.Font("Arial", 1, 14)); // NOI18N
        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel3.setText("ALERTA CONDUCTOR");

        jLabel4.setFont(new java.awt.Font("Arial", 1, 24)); // NOI18N
        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("PANEL DE CONTROL");

        jButton6.setText("Aplicar Cambios");

        txtCombustibleSim.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        txtCombustibleSim.setText("------");
        txtCombustibleSim.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        txtTemperaturaSim.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        txtTemperaturaSim.setText("------");
        txtTemperaturaSim.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        txtTemperaturaSim2.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        txtTemperaturaSim2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        txtTemperaturaSim2.setText("ALERTA TEMPERATURA");

        txtTemperaturaSim3.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        txtTemperaturaSim3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        txtTemperaturaSim3.setText("ALERTA COMBUSTIBLE");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(36, 36, 36)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(txtTemperaturaSim3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(txtTemperaturaSim2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(txtTemperaturaSim1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(57, 57, 57)
                        .addComponent(txtTemperaturaSim, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(txtCombustibleSim1, javax.swing.GroupLayout.PREFERRED_SIZE, 113, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(txtCombustibleSim, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(39, Short.MAX_VALUE))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmbCamionesAsignados, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnCancelarViaje, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnIniciarViaje, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmbDestinosPredeterminados, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSlider1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSeparator1)
                    .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jButton6, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSlider2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addComponent(jLabel4)
                .addGap(18, 18, 18)
                .addComponent(cmbCamionesAsignados, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(7, 7, 7)
                .addComponent(cmbDestinosPredeterminados, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnIniciarViaje)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                .addComponent(btnCancelarViaje)
                .addGap(8, 8, 8)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtCombustibleSim1)
                    .addComponent(txtCombustibleSim))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtTemperaturaSim3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtTemperaturaSim1)
                    .addComponent(txtTemperaturaSim))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtTemperaturaSim2)
                .addGap(12, 12, 12)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addGap(18, 18, 18)
                .addComponent(jSlider1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jSlider2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(38, 38, 38)
                .addComponent(jButton6)
                .addGap(14, 14, 14))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jfxContainerConductor, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addComponent(lblEstadoConductor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(22, 22, 22)
                .addComponent(jLabel1)
                .addGap(18, 29, Short.MAX_VALUE)
                .addComponent(lblEstadoConductor)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jfxContainerConductor, javax.swing.GroupLayout.DEFAULT_SIZE, 529, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

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

    private void cmbCamionesAsignadosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbCamionesAsignadosActionPerformed
        // TODO add your handling code here:
        mostrarCamionEnOrigen();
    }//GEN-LAST:event_cmbCamionesAsignadosActionPerformed

    // BOTÓN: CANCELAR / DETENER RECORRIDO
    private void btnIniciarViajeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIniciarViajeActionPerformed
        // TODO add your handling code here:
        // 1. Validaciones básicas de selección
        if (cmbCamionesAsignados.getSelectedIndex() <= 0) {
            JOptionPane.showMessageDialog(this, "Debe seleccionar un camión asignado antes de arrancar.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (destinoSeleccionado == null || rutaCalculada == null || rutaCalculada.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe marcar un punto de destino válido en el mapa o combo.", "Atención", JOptionPane.WARNING_MESSAGE);
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

    private void cmbDestinosPredeterminadosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbDestinosPredeterminadosActionPerformed

    }//GEN-LAST:event_cmbDestinosPredeterminadosActionPerformed
    
   // BOTÓN: INICIAR RECORRIDO
                                            

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
    private javax.swing.JButton jButton6;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSlider jSlider1;
    private javax.swing.JSlider jSlider2;
    private javax.swing.JPanel jfxContainerConductor;
    private javax.swing.JLabel lblEstadoConductor;
    private javax.swing.JLabel txtCombustibleSim;
    private javax.swing.JLabel txtCombustibleSim1;
    private javax.swing.JLabel txtTemperaturaSim;
    private javax.swing.JLabel txtTemperaturaSim1;
    private javax.swing.JLabel txtTemperaturaSim2;
    private javax.swing.JLabel txtTemperaturaSim3;
    // End of variables declaration//GEN-END:variables
}
