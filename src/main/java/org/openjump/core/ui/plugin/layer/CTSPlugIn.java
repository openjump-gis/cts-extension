package org.openjump.core.ui.plugin.layer;


import com.vividsolutions.jump.I18N;
import com.vividsolutions.jump.coordsys.CoordinateSystem;
import com.vividsolutions.jump.feature.Feature;
import com.vividsolutions.jump.task.TaskMonitor;
import com.vividsolutions.jump.workbench.Logger;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.Layerable;
import com.vividsolutions.jump.workbench.model.UndoableCommand;
import com.vividsolutions.jump.workbench.plugin.*;
import com.vividsolutions.jump.workbench.ui.GUIUtil;
import com.vividsolutions.jump.workbench.ui.HTMLFrame;
import com.vividsolutions.jump.workbench.ui.MenuNames;
import com.vividsolutions.jump.workbench.ui.MultiInputDialog;
import com.vividsolutions.jump.workbench.ui.SuggestTreeComboBox;
import org.cts.CRSFactory;
import org.cts.Identifier;
import org.cts.crs.CRSException;
import org.cts.crs.CoordinateReferenceSystem;
import org.cts.crs.GeodeticCRS;
import org.cts.op.*;
import org.cts.registry.*;
import org.cts.units.Unit;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.Geometry;
import org.openjump.core.ccordsys.srid.SRIDStyle;

import javax.swing.*;
import java.awt.geom.NoninvertibleTransformException;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * PlugIn to transform coordinates using Coordinate Transformation Suite (CTS)
 */
public class CTSPlugIn extends ThreadedBasePlugIn implements Iconified, EnableChecked {

    private final I18N i18n = I18N.getInstance("cts_plugin");

    private final String REGISTRY           = i18n.get("CTSPlugIn.registry");
    private final String SOURCE             = "source";
    private final String SOURCE_LABEL       = i18n.get("CTSPlugIn.srcCRS");
    private final String TARGET             = "target";
    private final String TARGET_LABEL       = i18n.get("CTSPlugIn.tgtCRS");
    private final String OP_NOT_FOUND       = i18n.get("CTSPlugIn.op-not-found");
    private final String HETEROGEN_SRC      = i18n.get("CTSPlugIn.heterogeneous-sources");
    private final String TRANSFORM          = i18n.get("CTSPlugIn.transform");
    private final String REPLACE            = i18n.get("CTSPlugIn.replace");
    private final String SOURCE_DATUM       = i18n.get("CTSPlugIn.srcDatum");
    private final String TARGET_DATUM       = i18n.get("CTSPlugIn.tgtDatum");
    private final String SOURCE_SPHEROID    = i18n.get("CTSPlugIn.srcSpheroid");
    private final String TARGET_SPHEROID    = i18n.get("CTSPlugIn.tgtSpheroid");
    private final String SOURCE_TOWGS84     = i18n.get("CTSPlugIn.srcToWgs84");
    private final String TARGET_TOWGS84     = i18n.get("CTSPlugIn.tgtToWgs84");
    private final String TRANSFORMED_LAYERS = i18n.get("CTSPlugIn.transformed-layers");
    private final String INVALID_SRC_CRS    = i18n.get("CTSPlugIn.invalid-src-crs");
    private final String INVALID_TGT_CRS    = i18n.get("CTSPlugIn.invalid-tgt-crs");
    private final String SOURCE_PROJECTION  = i18n.get("CTSPlugIn.srcProjection");
    private final String TARGET_PROJECTION  = i18n.get("CTSPlugIn.tgtProjection");

    private static final String EPSG = "EPSG";
    private static final String IGNF = "IGNF";

    String registry = EPSG;
    String srcCode = "4326";
    String tgtCode = "4326";
    final Map<String,String> codes = new LinkedHashMap<>(64);

    public void initialize(PlugInContext context) {

        context.getFeatureInstaller().addMainMenuPlugin(
            this, new String[]{MenuNames.PLUGINS}, getName(),
            false, getIcon(), getEnableCheck(context)
        );

    }

    public String getName() {
        return i18n.get("CTSPlugIn");
    }

    public ImageIcon getIcon(){
        return new ImageIcon(this.getClass().getResource("world.png"));
    }

    public boolean execute(final PlugInContext context) throws Exception {

        MultiInputDialog dialog = new MultiInputDialog(context.getWorkbenchFrame(), "CoordinateTransformation", true);

        // Try to get the srid (epsg) of selected layers
        // 1) from the CoordinateSystem associated to the first selected layer
        // 2) from the SRIDStyle associated to the first selected layer
        CoordinateSystem coordSystem;
        if (context.getSelectedLayers().length > 0 &&
                null != (coordSystem = context.getSelectedLayer(0).getFeatureCollectionWrapper().getFeatureSchema().getCoordinateSystem())) {
            srcCode = coordSystem == CoordinateSystem.UNSPECIFIED ?
                    "0" : Integer.toString(coordSystem.getEPSGCode());
            if (srcCode.equals("0") && context.getSelectedLayer(0).getStyle(SRIDStyle.class) != null) {
                srcCode = Integer.toString(((SRIDStyle)context.getSelectedLayer(0).getStyle(SRIDStyle.class)).getSRID());
            }
        }

        final JComboBox<String> registry_cb = dialog.addComboBox(REGISTRY, registry, Arrays.asList("EPSG", "IGNF"),"");

        codes.clear();
        codes.putAll(getAvailableCRS(context, (String) registry_cb.getSelectedItem()));

        final SuggestTreeComboBox srcCodesCB = new SuggestTreeComboBox(codes.keySet().toArray(new String[0]), 8);
        srcCodesCB.setSelectedItem(srcCode);
        srcCodesCB.setPrototypeDisplayValue("abcdefghijklmnpqrstuvwxyz/0123456789");
        dialog.addRow(SOURCE, new JLabel(SOURCE_LABEL), srcCodesCB, new EnableCheck[0], "");

        final SuggestTreeComboBox tgtCodesCB = new SuggestTreeComboBox(codes.keySet().toArray(new String[0]), 8);
        tgtCodesCB.setSelectedItem(tgtCode);
        tgtCodesCB.setPrototypeDisplayValue("abcdefghijklmnpqrstuvwxyz/0123456789");
        dialog.addRow(TARGET, new JLabel(TARGET_LABEL), tgtCodesCB, new EnableCheck[0], "");

        registry_cb.addActionListener(e -> {
            try {
                codes.clear();
                codes.putAll(getAvailableCRS(context, (String) registry_cb.getSelectedItem()));
                srcCodesCB.changeModel(codes.keySet().toArray(new String[0]));
                tgtCodesCB.changeModel(codes.keySet().toArray(new String[0]));
                srcCodesCB.setSelectedItem(codes.keySet().iterator().next());
                tgtCodesCB.setSelectedItem(codes.keySet().iterator().next());
            } catch(RegistryException | CRSException | IOException t) {
                t.printStackTrace();
            }

        });

        GUIUtil.centreOnWindow(dialog);
        dialog.setVisible(true);
        if (dialog.wasOKPressed()) {
            registry = dialog.getText(REGISTRY);
            srcCode = codes.get(srcCodesCB.getSelectedItem());
            tgtCode = codes.get(tgtCodesCB.getSelectedItem());
            return true;
        }
        return false;
    }

    public void run(TaskMonitor monitor, PlugInContext context)
            throws RegistryException, CRSException, CoordinateOperationException {
        reportNothingToUndoYet(context);
        if (srcCode == null) {
            throw new RegistryException(INVALID_SRC_CRS);
        } else if (tgtCode == null) {
            throw new RegistryException(INVALID_TGT_CRS);
        }
        if (!tgtCode.equals(srcCode)) {
            CRSFactory crsFactory = new CRSFactory();
            RegistryManager registryManager = crsFactory.getRegistryManager();
            if (registry.equals("EPSG")) {
                registryManager.addRegistry(new EPSGRegistry());
            } else if (registry.equals("IGNF")) {
                registryManager.addRegistry(new IGNFRegistry());
            }
            CoordinateReferenceSystem srcCRS = registryManager.getRegistry(registry)
                    .getCoordinateReferenceSystem(new Identifier(registry, srcCode, null));
            CoordinateReferenceSystem tgtCRS = registryManager.getRegistry(registry)
                    .getCoordinateReferenceSystem(new Identifier(registry, tgtCode, null));

            commitChanges(monitor, context, srcCRS, tgtCRS);
            report(context, srcCRS, tgtCRS);
        }
    }



    // Commit reprojection as an undoable transaction
    private void commitChanges(final TaskMonitor monitor,
                               final PlugInContext context,
                               final CoordinateReferenceSystem srcCRS,
                               final CoordinateReferenceSystem tgtCRS) throws CoordinateOperationException {

        // Short-circuits for cases where transformation cannot be done
        CoordinateOperation coordinateOperation = getOperation(srcCRS, tgtCRS);
        if (coordinateOperation == null) {
            context.getWorkbenchFrame().warnUser(OP_NOT_FOUND);
            return;
        }
        Layer[] layers = context.getLayerNamePanel().getSelectedLayers();
        if (layers.length == 0) {
            // Should never reach here if the plugin has been called from UI
            return;
        }

        // Prepare parameters and data structures for transaction
        final CoordinateFilter filter = getCoordinateFilter(coordinateOperation);
        boolean epsg = tgtCRS.getAuthorityName().equalsIgnoreCase(EPSG);
        int epsgCode = epsg ? Integer.parseInt(tgtCRS.getAuthorityKey()) : 0;
        CoordinateSystemWrapper newCoordinateSystem = new CoordinateSystemWrapper(tgtCRS);
        final Map<String,ArrayList<Geometry>> srcGeometryMap = new HashMap<>();
        final Map<String,ArrayList<Geometry>> tgtGeometryMap = new HashMap<>();
        final Map<String,CoordinateSystem> oldCoordinateSystems = new HashMap<>();
        final Map<String,SRIDStyle> oldSridStyles = new HashMap<>();
        final Map<String,CoordinateSystem> newCoordinateSystems = new HashMap<>();

        // Start transaction
        context.getLayerManager().getUndoableEditReceiver().reportNothingToUndoYet();
        for (Layer layer : context.getSelectedLayers()) {
            oldCoordinateSystems.put(layer.getName(), layer.getFeatureCollectionWrapper().getFeatureSchema().getCoordinateSystem());
            ArrayList<Geometry> srcGeometries = new ArrayList<>();
            ArrayList<Geometry> tgtGeometries = new ArrayList<>();
            int count = 0;
            monitor.report(TRANSFORM + " " + layer.getName());
            for (Object object : layer.getFeatureCollectionWrapper().getFeatures()) {
                Geometry srcGeom = ((Feature)object).getGeometry();
                srcGeometries.add(srcGeom);
                Geometry tgtGeom = srcGeom.copy();
                tgtGeom.apply(filter);
                tgtGeom.setSRID(epsgCode);
                tgtGeom.geometryChanged();
                tgtGeometries.add(tgtGeom);
                if (++count % 100 == 0) monitor.report(count, layer.getFeatureCollectionWrapper().getFeatures().size(), "");
            }
            srcGeometryMap.put(layer.getName(), srcGeometries);
            tgtGeometryMap.put(layer.getName(), tgtGeometries);
            oldSridStyles.put(layer.getName(), (SRIDStyle)layer.getStyle(SRIDStyle.class));
            oldCoordinateSystems.put(layer.getName(), layer.getFeatureCollectionWrapper().getFeatureSchema().getCoordinateSystem());
            newCoordinateSystems.put(layer.getName(), newCoordinateSystem);
        }
        UndoableCommand cmd = new UndoableCommand(getName()) {
            public void execute() {
                boolean isFiringEvents = context.getLayerManager().isFiringEvents();
                context.getLayerManager().setFiringEvents(false);
                for (Layer layer : context.getSelectedLayers()) {
                    monitor.report(REPLACE + " " + layer.getName());
                    List<Feature> features = layer.getFeatureCollectionWrapper().getFeatures();
                    ArrayList<Geometry> geometries = tgtGeometryMap.get(layer.getName());
                    CoordinateSystem cs = newCoordinateSystems.get(layer.getName());
                    for (int i = 0 ; i < features.size() ; i++) {
                        Feature feature = features.get(i);
                        feature.setGeometry(geometries.get(i));
                    }
                    Layer.tryToInvalidateEnvelope(layer);
                    layer.removeStyle(layer.getStyle(SRIDStyle.class));
                    SRIDStyle sridStyle = new SRIDStyle();
                    sridStyle.setSRID(cs.getEPSGCode());
                    layer.addStyle(sridStyle);
                    layer.getFeatureCollectionWrapper().getFeatureSchema().setCoordinateSystem(cs);
                    layer.setFeatureCollectionModified(true);
                }
                context.getLayerManager().setFiringEvents(isFiringEvents);
                try {
                    context.getLayerViewPanel().getViewport().zoomToFullExtent();
                } catch(NoninvertibleTransformException e) {
                    e.printStackTrace();
                }
            }

            public void unexecute() {
                boolean isFiringEvents = context.getLayerManager().isFiringEvents();
                context.getLayerManager().setFiringEvents(false);
                for (Layer layer : context.getSelectedLayers()) {
                    List<Feature> features = layer.getFeatureCollectionWrapper().getFeatures();
                    ArrayList<Geometry> geometries = srcGeometryMap.get(layer.getName());
                    CoordinateSystem cs = oldCoordinateSystems.get(layer.getName());
                    for (int i = 0 ; i < features.size() ; i++) {
                        Feature feature = features.get(i);
                        feature.setGeometry(geometries.get(i));
                    }
                    Layer.tryToInvalidateEnvelope(layer);
                    layer.removeStyle(layer.getStyle(SRIDStyle.class));
                    if (oldSridStyles.get(layer.getName()) != null) layer.addStyle(oldSridStyles.get(layer.getName()));
                    layer.getFeatureCollectionWrapper().getFeatureSchema().setCoordinateSystem(cs);
                    layer.setFeatureCollectionModified(true);
                }
                context.getLayerManager().setFiringEvents(isFiringEvents);
                try {
                    context.getLayerViewPanel().getViewport().zoomToFullExtent();
                } catch(NoninvertibleTransformException e) {
                    e.printStackTrace();
                }
            }
        };
        boolean exceptionOccurred = true;
        try {
            cmd.execute();
            exceptionOccurred = false;
        }
        finally {
            if (exceptionOccurred) {
                context.getLayerManager().getUndoableEditReceiver()
                        .getUndoManager().discardAllEdits();
            }
        }
        context.getLayerManager().getUndoableEditReceiver().receive(cmd.toUndoableEdit());
    }

    private void report(PlugInContext context, CoordinateReferenceSystem srcCRS, CoordinateReferenceSystem tgtCRS)
            throws CoordinateOperationException{
        HTMLFrame html = context.getOutputFrame();
        html.createNewDocument();
        html.setTitle(getName());
        html.append("<h2>" + TRANSFORMED_LAYERS + "</h2>");
        html.append(Arrays.toString(context.getSelectedLayers()));
        html.append("<h2>" + SOURCE_LABEL + "</h2>");
        html.addField(SOURCE_LABEL, srcCRS.toString());
        html.addField(SOURCE_DATUM, srcCRS.getDatum().toString());
        html.addField(SOURCE_TOWGS84, srcCRS.getDatum().getToWGS84().toString());
        html.addField(SOURCE_SPHEROID, (srcCRS.getDatum()).getEllipsoid().toString());
        html.addField(SOURCE_PROJECTION, srcCRS.getProjection() == null ? "null" : srcCRS.getProjection().toWKT(Unit.METER));
        html.append("<h2>" + TARGET_LABEL + "</h2>");
        html.addField(TARGET_LABEL, tgtCRS.toString());
        html.addField(TARGET_DATUM, tgtCRS.getDatum().toString());
        html.addField(TARGET_TOWGS84, tgtCRS.getDatum().getToWGS84().toString());
        html.addField(TARGET_SPHEROID, (srcCRS.getDatum()).getEllipsoid().toString());
        html.addField(TARGET_PROJECTION, tgtCRS.getProjection() == null ? "null" : tgtCRS.getProjection().toWKT(Unit.METER));
        html.append("<h2>" + getName() + "</h2>");
        html.addField("", getOperation(srcCRS, tgtCRS).toString().replaceAll("\n","<br>"));
    }

    private CoordinateOperation getOperation(final CoordinateReferenceSystem srcCRS,
                                             final CoordinateReferenceSystem tgtCRS)
            throws CoordinateOperationException{
        Collection<CoordinateOperation> ops = CoordinateOperationFactory
                .createCoordinateOperations((GeodeticCRS) srcCRS, (GeodeticCRS) tgtCRS);
        return ops.size() == 0 ? null : CoordinateOperationFactory.getMostPrecise(ops);
    }

    private CoordinateFilter getCoordinateFilter(final CoordinateOperation op) {
        return new CoordinateFilter() {
            @Override
            public void filter(Coordinate coordinate) {
                try {
                    double[] xyz = op.transform(new double[]{coordinate.x, coordinate.y, coordinate.z});
                    coordinate.setOrdinate(0, xyz[0]);
                    coordinate.setOrdinate(1, xyz[1]);
                    if (xyz.length > 2) coordinate.setOrdinate(2, xyz[2]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private Map<String,String> getAvailableCRS(PlugInContext context, String registry)
            throws IOException, RegistryException, CRSException {
        return RegistryReader.read(registry);
    }

    EnableCheck getEnableCheck(final PlugInContext context) {
        EnableCheckFactory factory = context.getCheckFactory();
        return new MultiEnableCheck()
                .add(factory.createTaskWindowMustBeActiveCheck())
                .add(factory.createAtLeastNLayersMustBeSelectedCheck(1))
                .add(factory.createSelectedLayersMustBeEditableCheck())
                .add(factory.createSelectedLayerablesMustBeVectorLayers())
                .add(new EnableCheck() {
                    @Override
                    public String check(JComponent component) {
                        Layerable[] layerables = context.getLayerableNamePanel()
                                .selectedNodes(Layerable.class).toArray(new Layerable[0]);
                        if (layerables.length > 0) {
                            Layer layer = (Layer) layerables[0];
                            CoordinateSystem cs = layer.getFeatureCollectionWrapper().getFeatureSchema().getCoordinateSystem();
                            SRIDStyle srid = (SRIDStyle) layer.getStyle(SRIDStyle.class);
                            for (int i = 1; i < layerables.length; i++) {
                                layer = (Layer) layerables[i];
                                CoordinateSystem csi = layer.getFeatureCollectionWrapper().getFeatureSchema().getCoordinateSystem();
                                SRIDStyle sridi = (SRIDStyle) layer.getStyle(SRIDStyle.class);
                                if (cs == csi && srid == sridi) continue;
                                if (cs == null && csi != null) return HETEROGEN_SRC;
                                if (cs != null && csi == null) return HETEROGEN_SRC;
                                if (cs == CoordinateSystem.UNSPECIFIED && csi != CoordinateSystem.UNSPECIFIED)
                                    return HETEROGEN_SRC;
                                if (cs != CoordinateSystem.UNSPECIFIED && csi == CoordinateSystem.UNSPECIFIED)
                                    return HETEROGEN_SRC;
                                try {
                                    if (cs != null && csi != null && cs.getEPSGCode() != csi.getEPSGCode())
                                        return HETEROGEN_SRC;
                                } catch (UnsupportedOperationException e) {
                                    Logger.warn(e.getMessage());
                                }
                                if (srid != null && sridi != null && srid.getSRID() != sridi.getSRID())
                                    return HETEROGEN_SRC;
                            }
                            return null;
                        }
                        // should never reach here
                        return layerables.length > 0 ? null : "At least 1 layer must be selected";
                    }
                });
    }

}
