/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.hirata.view;

import com.hirata.controller.ControladorConductor;
import com.hirata.controller.ControladorTelemetria;
import com.hirata.model.Camion;
import com.hirata.model.Sesion;
import com.hirata.model.TelemetriaRuta;
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
    
    // Variables para controlar la simulación en PanelConductor.java
    private double combustibleActual = 100.0; // Comienza lleno (100%)
    private double temperaturaActual = 3.5;    // Temperatura inicial ideal de carga (3.5°C)
    
    private java.awt.Image imgCamionAzul = null;
    private java.awt.Image imgCamionRojo = null;
    
    /**
     * Creates new form PanelConductor
     */
    public PanelConductor() {
        initComponents();
        
        // Cargar las imágenes PNG
        try {
            imgCamionAzul = new javax.swing.ImageIcon(getClass().getResource("camion_azul.png")).getImage();
            imgCamionRojo = new javax.swing.ImageIcon(getClass().getResource("camion_rojo.png")).getImage();
        } catch (Exception ex) {
            System.err.println("Advertencia: No se pudieron cargar los archivos PNG del camión. " + ex.getMessage());
        }
        
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
        
        mapaConductor.setPreferredSize(new java.awt.Dimension(800, 600)); 
        jfxContainerConductor.setLayout(new BorderLayout());
        jfxContainerConductor.add(mapaConductor, BorderLayout.CENTER);
    }

    private void cargarDestinosPrestablecidos() {
        cmbDestinosPredeterminados.removeAllItems();
        cmbDestinosPredeterminados.addItem("Seleccione Destino");
        cmbDestinosPredeterminados.addItem("ZOFRI Recinto Central");
        cmbDestinosPredeterminados.addItem("Puerto de Iquique Terminal");
        cmbDestinosPredeterminados.addItem("Rotonda El Pampino (Acceso Ruta A-16)");
        cmbDestinosPredeterminados.addItem("Hospital Regional de Iquique");
        cmbDestinosPredeterminados.addItem("Mall Plaza Iquique");
        cmbDestinosPredeterminados.addItem("Terminal Agropecuario (El Agro)");
        cmbDestinosPredeterminados.addItem("Playa Brava Sector Norte");
        cmbDestinosPredeterminados.addItem("Universidad Arturo Prat (UNAP)");
        cmbDestinosPredeterminados.addItem("Bajo Molle (Sector Industrial)");
        cmbDestinosPredeterminados.addItem("Alto Hospicio (Centro Logístico)");
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

                        // =========================================================================
                        // CONMUTACIÓN DE IMÁGENES (REEMPLAZA LOS RECTÁNGULOS)
                        // =========================================================================
                        if (imgCamionAzul != null && imgCamionRojo != null) {
                            
                            // Evaluación térmica en vivo: Cambia a rojo si excede los 5.0°C
                            java.awt.Image imagenAQuemar = (c.getTemperatura() > 5.0) ? imgCamionRojo : imgCamionAzul;
                            
                            // Tamaño 48x24 (Restamos la mitad: x - 24, y - 12)
                            g.drawImage(imagenAQuemar, x - 24, y - 12, 48, 24, null);
                            
                        } else {
                            // FALLBACK: Si falla el PNG, dibuja las cajas originales
                            g.setColor(c.getColorEstado()); 
                            g.fillRect(x - 12, y - 6, 22, 11); 
                            g.fillRect(x + 10, y - 3, 6, 8);   

                            g.setColor(Color.BLACK);
                            g.fillOval(x - 9, y + 5, 4, 4);    
                            g.fillOval(x + 2, y + 5, 4, 4);
                            g.fillOval(x + 10, y + 5, 4, 4);
                        }

                        // Imprime la patente del camión encima del icono para el conductor
                        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
                        g.setColor(Color.BLACK);
                        g.drawString(c.getPatente(), x - 20, y - 16);
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
        // 2. Si pasa la validación del GPS, buscar su historial o posición inicial
        GeoPosition ultimaPosicionBD = ctrl.obtenerUltimaPosicionCamion(patenteSeleccionada);
        this.origenCalculoRuta = (ultimaPosicionBD != null) ? ultimaPosicionBD : origenBase;

        // =========================================================================
        // NUEVO: Recuperar el último estado del combustible guardado en la BD
        // =========================================================================
        double ultimoCombustibleGuardado = ctrl.obtenerUltimoCombustible(idGpsVinculado);
        
        // Sincronizar variables globales de la pantalla con el último estado real
        this.combustibleActual = ultimoCombustibleGuardado;
        this.temperaturaActual = 3.5; // Reiniciar a temperatura ideal de carga

        // Mostrar de inmediato en los labels fijos antes de arrancar el motor
        lblCombustibleEnVivo.setText(String.format("%.1f%%", combustibleActual));
        lblTemperaturaEnVivo.setText(String.format("%.1f°C", temperaturaActual));
        
        // Inicializar las alertas en estado normal
        txtTemperaturaSim3.setText("SISTEMA OK");
        txtTemperaturaSim3.setForeground(new Color(0, 153, 51));
        txtTemperaturaSim2.setText("SISTEMA OK");
        txtTemperaturaSim2.setForeground(new Color(0, 153, 51));

        // Actualizar el constructor del Waypoint con el combustible real recuperado
        waypointCamionLocal = new CamionWaypoint(
            idGpsVinculado, 
            patenteSeleccionada, 
            this.origenCalculoRuta.getLatitude(), 
            this.origenCalculoRuta.getLongitude(), 
            this.combustibleActual, 
            this.temperaturaActual
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
        btnIniciarViaje = new javax.swing.JButton();
        cmbCamionesAsignados = new javax.swing.JComboBox<>();
        cmbDestinosPredeterminados = new javax.swing.JComboBox<>();
        btnCancelarViaje = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        jSeparator2 = new javax.swing.JSeparator();
        jPanel2 = new javax.swing.JPanel();
        txtCombustibleSim1 = new javax.swing.JLabel();
        lblCombustibleEnVivo = new javax.swing.JLabel();
        txtTemperaturaSim3 = new javax.swing.JLabel();
        lblTemperaturaEnVivo = new javax.swing.JLabel();
        txtTemperaturaSim1 = new javax.swing.JLabel();
        txtTemperaturaSim2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        sliderCombustible = new javax.swing.JSlider();
        lblValorSliderGasolina = new javax.swing.JLabel();
        sliderTemperatura = new javax.swing.JSlider();
        lblValorSliderTemp = new javax.swing.JLabel();
        btnAplicarSensores = new javax.swing.JButton();
        btnAplicarSensores1 = new javax.swing.JButton();
        txtCombustibleSim2 = new javax.swing.JLabel();
        txtTemperaturaSim4 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jfxContainerConductor.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jfxContainerConductor.setMinimumSize(new java.awt.Dimension(600, 500));
        jfxContainerConductor.setName(""); // NOI18N
        jfxContainerConductor.setPreferredSize(new java.awt.Dimension(800, 600));

        javax.swing.GroupLayout jfxContainerConductorLayout = new javax.swing.GroupLayout(jfxContainerConductor);
        jfxContainerConductor.setLayout(jfxContainerConductorLayout);
        jfxContainerConductorLayout.setHorizontalGroup(
            jfxContainerConductorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 796, Short.MAX_VALUE)
        );
        jfxContainerConductorLayout.setVerticalGroup(
            jfxContainerConductorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 596, Short.MAX_VALUE)
        );

        lblEstadoConductor.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        lblEstadoConductor.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblEstadoConductor.setText("ESTADO CONDUCTOR");
        lblEstadoConductor.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jLabel1.setFont(new java.awt.Font("Arial", 1, 24)); // NOI18N
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("SIMULADOR CONDUCTOR - GPS");

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        btnIniciarViaje.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnIniciarViaje.setText("Iniciar");
        btnIniciarViaje.addActionListener(this::btnIniciarViajeActionPerformed);

        cmbCamionesAsignados.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbCamionesAsignados.addActionListener(this::cmbCamionesAsignadosActionPerformed);

        cmbDestinosPredeterminados.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbDestinosPredeterminados.addActionListener(this::cmbDestinosPredeterminadosActionPerformed);

        btnCancelarViaje.setText("Cancelar");
        btnCancelarViaje.addActionListener(this::btnCancelarViajeActionPerformed);

        jLabel4.setFont(new java.awt.Font("Arial", 1, 24)); // NOI18N
        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("PANEL DE CONTROL");

        jPanel2.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        txtCombustibleSim1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        txtCombustibleSim1.setText("Combustible:");

        lblCombustibleEnVivo.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        lblCombustibleEnVivo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblCombustibleEnVivo.setText("------");
        lblCombustibleEnVivo.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        txtTemperaturaSim3.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        txtTemperaturaSim3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        txtTemperaturaSim3.setText("ALERTA COMBUSTIBLE");

        lblTemperaturaEnVivo.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        lblTemperaturaEnVivo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTemperaturaEnVivo.setText("------");
        lblTemperaturaEnVivo.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        txtTemperaturaSim1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        txtTemperaturaSim1.setText("Temp. Carga:");

        txtTemperaturaSim2.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        txtTemperaturaSim2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        txtTemperaturaSim2.setText("ALERTA TEMPERATURA");

        jLabel3.setFont(new java.awt.Font("Arial", 1, 18)); // NOI18N
        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel3.setText("ALERTA CONDUCTOR");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(txtCombustibleSim1, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(lblCombustibleEnVivo, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(16, 16, 16))
                    .addComponent(txtTemperaturaSim2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(txtTemperaturaSim3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(txtTemperaturaSim1, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(lblTemperaturaEnVivo, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 16, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtCombustibleSim1)
                    .addComponent(lblCombustibleEnVivo))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtTemperaturaSim3)
                .addGap(18, 18, 18)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtTemperaturaSim1)
                    .addComponent(lblTemperaturaEnVivo))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtTemperaturaSim2)
                .addContainerGap(14, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel2.setFont(new java.awt.Font("Arial", 1, 18)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("SIMULAR TELEMETRIA");

        sliderCombustible.addChangeListener(this::sliderCombustibleStateChanged);

        lblValorSliderGasolina.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblValorSliderGasolina.setText("------");

        sliderTemperatura.addChangeListener(this::sliderTemperaturaStateChanged);

        lblValorSliderTemp.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblValorSliderTemp.setText("------");

        btnAplicarSensores.setText("Aplicar Cambios");
        btnAplicarSensores.addActionListener(this::btnAplicarSensoresActionPerformed);

        btnAplicarSensores1.setText("Restablecer");
        btnAplicarSensores1.addActionListener(this::btnAplicarSensores1ActionPerformed);

        txtCombustibleSim2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        txtCombustibleSim2.setText("Combustible:");

        txtTemperaturaSim4.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        txtTemperaturaSim4.setText("Temp. Carga:");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnAplicarSensores1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnAplicarSensores, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtCombustibleSim2)
                            .addComponent(txtTemperaturaSim4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(sliderTemperatura, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lblValorSliderTemp, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lblValorSliderGasolina, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(sliderCombustible, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(lblValorSliderGasolina)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sliderCombustible, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtCombustibleSim2))
                .addGap(8, 8, 8)
                .addComponent(lblValorSliderTemp)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sliderTemperatura, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtTemperaturaSim4))
                .addGap(18, 18, 18)
                .addComponent(btnAplicarSensores)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnAplicarSensores1)
                .addGap(15, 15, 15))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmbCamionesAsignados, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnCancelarViaje, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnIniciarViaje, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmbDestinosPredeterminados, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSeparator1)
                    .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnCancelarViaje)
                .addGap(18, 18, 18)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblEstadoConductor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jfxContainerConductor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(16, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jfxContainerConductor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(lblEstadoConductor, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnCancelarViajeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelarViajeActionPerformed
        // TODO add your handling code here:
        // 1. Detener procesos en segundo plano
        if (emulador != null) {
            emulador.detenerSimulacion();
        }
        if (timerAnimacionConductor != null) {
            timerAnimacionConductor.stop(); // Apagar animación visual
        }

        // =========================================================================
        // SOLUCIÓN AL "TELETRANSPORTE": Limpiar el estado de la ruta anterior
        // =========================================================================
        
        // 2. Establecer el NUEVO punto de partida exactamente donde quedó estacionado el camión (Punto B)
        if (waypointCamionLocal != null) {
            this.origenCalculoRuta = waypointCamionLocal.getPosition();
        }

        // 3. Borrar la memoria de la ruta y el destino
        rutaCalculada = null;
        destinoSeleccionado = null;
        
        // 4. Regresar el ComboBox a "Seleccione Destino" 
        // (Esto dispara tu bloque switch internamente, lo que ayuda a limpiar la línea azul)
        cmbDestinosPredeterminados.setSelectedIndex(0);

        // =========================================================================

        // 5. Habilitar controles nuevamente
        btnIniciarViaje.setEnabled(true);
        btnCancelarViaje.setEnabled(false);
        cmbCamionesAsignados.setEnabled(true);
        cmbDestinosPredeterminados.setEnabled(true);
        
        lblEstadoConductor.setText("Viaje finalizado. Vehículo en posición esperando nueva ruta.");
        lblEstadoConductor.setForeground(java.awt.Color.BLACK);
        
        // 6. Redibujar el mapa (El PNG del camión se queda en el Punto B, pero la ruta desaparece)
        actualizarCapasMapaConductor();
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
        
        emulador.iniciarCombustibleSimulacion(this.combustibleActual);
        emulador.iniciarTemperaturaSimulacion(this.temperaturaActual);
        
        sliderCombustible.setValue((int) this.combustibleActual);
        sliderTemperatura.setValue((int) (this.temperaturaActual * 10));
        lblValorSliderGasolina.setText((int) this.combustibleActual + "%");
        lblValorSliderTemp.setText(String.format("%.1f°C", this.temperaturaActual));
        
        // Configurar los rangos y valores iniciales de los JSliders por código de forma segura
        sliderCombustible.setMinimum(0);
        sliderCombustible.setMaximum(100);
        sliderCombustible.setValue(100);
        sliderTemperatura.setMinimum(-100); // Soporta temperaturas bajo cero (-10.0°C)
        sliderTemperatura.setMaximum(250);  // Hasta 25.0°C
        sliderTemperatura.setValue(35);     // 35 significa 3.5°C

        hiloSimulacion = new Thread(emulador);
        hiloSimulacion.start();
        
        // 3. Temporizador de animación gráfica local para mover el camión cada 3s
        timerAnimacionConductor = new javax.swing.Timer(3000, e -> {
            // Verificamos si el emulador asíncrono está activo y viajando por la ruta
            if (emulador != null && emulador.isEnRuta()) {
                GeoPosition posActual = new GeoPosition(emulador.getLatActual(), emulador.getLonActual()); // Le pedimos al emulador su posición actual cambiante

                // 1. Sincronizamos los datos en vivo que viajan en el emulador de ruta hacia MySQL
                double gasEnVivo = emulador.getCombustibleActual();
                double tempEnVivo = emulador.getTemperaturaCarga();
                
                // Sincronizamos nuestras variables globales de la vista con el estado del emulador
                this.combustibleActual = gasEnVivo;
                this.temperaturaActual = tempEnVivo;

                // 2. Actualizar los labels en pantalla en tiempo real con las métricas del IoT
                lblCombustibleEnVivo.setText(String.format("%.1f%%", gasEnVivo));
                lblTemperaturaEnVivo.setText(String.format("%.1f°C", tempEnVivo));

                // 3. Los JSliders y sus etiquetas numéricas ahora SIEMPRE siguen el flujo dinámico del viaje
                sliderCombustible.setValue((int) gasEnVivo);
                sliderTemperatura.setValue((int) (tempEnVivo * 10));
                lblValorSliderGasolina.setText((int) gasEnVivo + "%");
                lblValorSliderTemp.setText(String.format("%.1f°C", tempEnVivo));
                
                // 4. Evaluación del estanque de Combustible (Alertas visuales inmediatas)
                if (gasEnVivo <= 15.0) {
                    txtTemperaturaSim3.setText("⚠️ ¡ALERTA! REFUELING REQUERIDO (BAJO 15%)");
                    txtTemperaturaSim3.setForeground(Color.RED);
                } else {
                    txtTemperaturaSim3.setText("COMBUSTIBLE OPTIMIZADO");
                    txtTemperaturaSim3.setForeground(new Color(0, 153, 51)); // Verde
                }

                // 2. Evaluación de la Cadena de Frío de la Carga (RF-13)
                if (tempEnVivo > 5.0) {
                    txtTemperaturaSim2.setText("🚨 CRÍTICO: CADENA DE FRÍO ROTA (SOBRE 5°C)");
                    txtTemperaturaSim2.setForeground(Color.RED);
                } else {
                    txtTemperaturaSim2.setText("REFRIGERACIÓN ESTABLE");
                    txtTemperaturaSim2.setForeground(new Color(0, 153, 51)); // Verde
                }
                
                // 6. Movemos el marcador del camión sobre el mapa del conductor
                waypointCamionLocal = new CamionWaypoint(
                    waypointCamionLocal.getIdGps(), 
                    patenteSeleccionada, 
                    posActual.getLatitude(), 
                    posActual.getLongitude(),
                    gasEnVivo, 
                    tempEnVivo
                );

                // Hace que la cámara del mapa siga al camión en movimiento
                mapaConductor.setAddressLocation(posActual);
                actualizarCapasMapaConductor();
                
            } else {
                if (emulador != null) {
                    this.origenCalculoRuta = new GeoPosition(emulador.getLatActual(), emulador.getLonActual());

                    // GUARDAR RESUMEN DEL VIAJE PARA EL INFORME (RF-12)
                    double gasGastado = emulador.getCombustibleGastado();
                    int totalAlertas = emulador.getContadorAlertasViaje();
                    String origen = cmbDestinosPredeterminados.getSelectedItem().toString(); // O de donde haya partido
                    String destino = "Destino Seleccionado"; // Aquí pasas el texto de tu ruta

                    // Llamas a un nuevo método de tu controlador/DAO para insertar
                    ctrl.guardarResumenViaje(idCamionReal, patenteSeleccionada, origen, destino, gasGastado, totalAlertas);

                    System.out.println("Viaje consolidado guardado. Gastó: " + gasGastado + "% | Alertas: " + totalAlertas);
                }
                btnCancelarViajeActionPerformed(null);
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
        // Evitar que se dispare por error cuando el combo se está limpiando/inicializando
        if (cmbDestinosPredeterminados.getSelectedIndex() == -1) return;

        // VALIDACIÓN 1: Verificar si hay un vehículo seleccionado
        if (cmbCamionesAsignados.getSelectedIndex() <= 0) {
            // Solo mostrar alerta si el usuario intentó elegir un destino válido (índice > 0)
            if (cmbDestinosPredeterminados.getSelectedIndex() > 0) {
                javax.swing.JOptionPane.showMessageDialog(this, "Debe seleccionar un vehículo primero antes de marcar un destino.", "Validación", javax.swing.JOptionPane.WARNING_MESSAGE);
                cmbDestinosPredeterminados.setSelectedIndex(0); // Lo regresamos a "Seleccione Destino"
            }
            return;
        }

        // VALIDACIÓN 2: Si el viaje ya inició y el camión está en movimiento, bloquear el cambio de ruta
        if (emulador != null && !btnIniciarViaje.isEnabled()) {
            return;
        }

        String seleccion = cmbDestinosPredeterminados.getSelectedItem().toString();

        // Asignar las coordenadas GPS reales según la opción seleccionada
        switch (seleccion) {
            case "ZOFRI Recinto Central":
                destinoSeleccionado = new GeoPosition(-20.211565, -70.134546);
                break;
            case "Puerto de Iquique Terminal":
                destinoSeleccionado = new GeoPosition(-20.205260, -70.156540);
                break;
            case "Rotonda El Pampino (Acceso Ruta A-16)":
                destinoSeleccionado = new GeoPosition(-20.229410, -70.130630);
                break;
            case "Hospital Regional de Iquique":
                destinoSeleccionado = new GeoPosition(-20.228420, -70.137810);
                break;
            case "Mall Plaza Iquique":
                destinoSeleccionado = new GeoPosition(-20.242950, -70.142060);
                break;
            case "Terminal Agropecuario (El Agro)":
                destinoSeleccionado = new GeoPosition(-20.245030, -70.125740);
                break;
            case "Playa Brava Sector Norte":
                destinoSeleccionado = new GeoPosition(-20.252030, -70.139610);
                break;
            case "Universidad Arturo Prat (UNAP)":
                destinoSeleccionado = new GeoPosition(-20.264220, -70.133280);
                break;
            case "Bajo Molle (Sector Industrial)":
                destinoSeleccionado = new GeoPosition(-20.276480, -70.130250);
                break;
            case "Alto Hospicio (Centro Logístico)":
                destinoSeleccionado = new GeoPosition(-20.273610, -70.106520);
                break;
            default: 
                // Caso "Seleccione Destino": Se limpia la ruta del mapa
                destinoSeleccionado = null;
                rutaCalculada = null; 
                actualizarCapasMapaConductor(); // Redibujar el mapa sin la línea azul
                lblEstadoConductor.setText("Destino cancelado. Esperando instrucciones.");
                lblEstadoConductor.setForeground(Color.BLACK);
                return;
        }

        // Si el switch asignó un destino válido, mandamos a calcular la ruta y dibujar la línea
        if (destinoSeleccionado != null) {
            actualizarDestinoEnRuta();
        }
    }//GEN-LAST:event_cmbDestinosPredeterminadosActionPerformed

    private void btnAplicarSensoresActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAplicarSensoresActionPerformed
        // TODO add your handling code here:
        if (emulador == null || !emulador.isEnRuta()) {
            JOptionPane.showMessageDialog(this, "Debe iniciar el viaje antes de alterar los sensores IoT.", "Atención", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. Extraer valores manuales de los Sliders
        int valorSliderGasolina = sliderCombustible.getValue();
        int valorSliderTemp = sliderTemperatura.getValue(); 

        double tempCalculada = valorSliderTemp / 10.0;

        // =========================================================================
        // INYECCIÓN EN TIEMPO REAL AL EMULADOR (Hacia la Base de Datos)
        // =========================================================================
        emulador.setCombustibleActual(valorSliderGasolina);
        emulador.setTemperaturaCarga(tempCalculada);

        // 3. Forzar refresco visual inmediato en las etiquetas del Conductor
        lblCombustibleEnVivo.setText(String.format("%.1f%%", (double) valorSliderGasolina));
        lblTemperaturaEnVivo.setText(String.format("%.1f°C", tempCalculada));
        
        // Forzar actualización inmediata de alertas al aplicar sliders manuales
        if (valorSliderGasolina <= 15) {
            txtTemperaturaSim3.setText("⚠️ ¡ALERTA! REFUELING REQUERIDO (BAJO 15%)");
            txtTemperaturaSim3.setForeground(Color.RED);
        } else {
            txtTemperaturaSim3.setText("COMBUS. OPTIMIZADO");
            txtTemperaturaSim3.setForeground(new Color(0, 153, 51));
        }

        if (tempCalculada > 5.0) {
            txtTemperaturaSim2.setText("🚨 CRÍTICO: CADENA DE FRÍO ROTA (SOBRE 5°C)");
            txtTemperaturaSim2.setForeground(Color.RED);
        } else {
            txtTemperaturaSim2.setText("REFRIGERACIÓN ESTABLE");
            txtTemperaturaSim2.setForeground(new Color(0, 153, 51));
        }

        System.out.println("[IoT-Forzado] Transmisión alterada hacia MySQL -> Gas: " + valorSliderGasolina + "% | Temp: " + tempCalculada + "°C");
    }//GEN-LAST:event_btnAplicarSensoresActionPerformed

    private void btnAplicarSensores1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAplicarSensores1ActionPerformed
        // TODO add your handling code here:
        System.out.println("Simulación devuelta al control automático del sensor IoT.");
    }//GEN-LAST:event_btnAplicarSensores1ActionPerformed

    private void sliderCombustibleStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sliderCombustibleStateChanged
        // TODO add your handling code here:
        // Muestra el valor entero directo con el signo %
        int gas = sliderCombustible.getValue();
        lblValorSliderGasolina.setText(gas + "%");
    }//GEN-LAST:event_sliderCombustibleStateChanged

    private void sliderTemperaturaStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sliderTemperaturaStateChanged
        // TODO add your handling code here:
        // Divide por 10.0 para mostrar el decimal real en el Label al arrastrar
        double temp = sliderTemperatura.getValue() / 10.0;
        lblValorSliderTemp.setText(String.format("%.1f°C", temp));
    }//GEN-LAST:event_sliderTemperaturaStateChanged
    
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
    private javax.swing.JButton btnAplicarSensores;
    private javax.swing.JButton btnAplicarSensores1;
    private javax.swing.JButton btnCancelarViaje;
    private javax.swing.JButton btnIniciarViaje;
    private javax.swing.JComboBox<String> cmbCamionesAsignados;
    private javax.swing.JComboBox<String> cmbDestinosPredeterminados;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JPanel jfxContainerConductor;
    private javax.swing.JLabel lblCombustibleEnVivo;
    private javax.swing.JLabel lblEstadoConductor;
    private javax.swing.JLabel lblTemperaturaEnVivo;
    private javax.swing.JLabel lblValorSliderGasolina;
    private javax.swing.JLabel lblValorSliderTemp;
    private javax.swing.JSlider sliderCombustible;
    private javax.swing.JSlider sliderTemperatura;
    private javax.swing.JLabel txtCombustibleSim1;
    private javax.swing.JLabel txtCombustibleSim2;
    private javax.swing.JLabel txtTemperaturaSim1;
    private javax.swing.JLabel txtTemperaturaSim2;
    private javax.swing.JLabel txtTemperaturaSim3;
    private javax.swing.JLabel txtTemperaturaSim4;
    // End of variables declaration//GEN-END:variables
}
