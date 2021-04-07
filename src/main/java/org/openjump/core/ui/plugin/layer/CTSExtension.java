package org.openjump.core.ui.plugin.layer;

import com.vividsolutions.jump.workbench.Logger;
import com.vividsolutions.jump.workbench.plugin.Extension;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;

/**
 * Created by Michaël on 30/11/14.
 */
// 1.0.0 (2021-04-07) migration to OpenJUMP2 / JTS 1.18 / CTS 1.5.2
// 0.1.3 (2017-01-21) suggesttree moved to OpenJUMP, CTSPlugIn 0.1.3 needs OJ 1.10+
// 0.1.2
// 0.1.1
// 0.1.0 (2014-12-06) initial version
public class CTSExtension extends Extension {

    public String getName() {
        return "CTS Extension (Micha\u00EBl Michaud)";
    }

    public String getVersion() {
        return "1.0.0 (2021-04-07)";
    }

    public void configure(PlugInContext context) throws Exception {

        boolean missing_libraries = false;
        try {
            getClass().getClassLoader().loadClass("org.cts.crs.CoordinateReferenceSystem");
        } catch(ClassNotFoundException cnfe) {
            Logger.warn("CTS Extension cannot be initialized : missing library : cts-*.jar");
            missing_libraries = true;
        }
        if (missing_libraries) return;

        new CTSPlugIn().initialize(context);
    }

}
