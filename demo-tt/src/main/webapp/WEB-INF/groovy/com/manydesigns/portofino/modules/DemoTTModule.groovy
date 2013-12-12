package com.manydesigns.portofino.modules

import com.manydesigns.portofino.di.Inject
import com.manydesigns.portofino.i18n.ResourceBundleManager
import com.manydesigns.portofino.pageactions.registry.TemplateRegistry

public class DemoTTModule implements Module {

    public final static String TT_VERSION = "0.9"

    protected ModuleStatus status = ModuleStatus.CREATED;

    @Inject(BaseModule.RESOURCE_BUNDLE_MANAGER)
    public ResourceBundleManager resourceBundleManager;

    @Inject(BaseModule.MODULE_REGISTRY)
    public ModuleRegistry moduleRegistry;

    @Inject(PageactionsModule.TEMPLATES_REGISTRY)
    public TemplateRegistry templates;


    String getModuleVersion() {
        return TT_VERSION;
    }

    int getMigrationVersion() {
        return 1;
    }

    double getPriority() {
        return 100;
    }

    String getId() {
        return "tt";
    }

    String getName() {
        return "Portofino demo-tt";
    }

    int install() {
        return 1;
    }

    void init() {
// Speed up for GAE
//        for(Module module : moduleRegistry.getModules()) {
//            def classFileName = module.getClass().getSimpleName() + ".class";
//            def classUrl = module.getClass().getResource(classFileName);
//            if(classUrl != null) {
//                def path = classUrl.toString();
//                path = path.substring(0, path.length() - module.getClass().getName().length() - ".class".length());
//                resourceBundleManager.addSearchPath(path + PortofinoListener.PORTOFINO_MESSAGES_FILE_NAME);
//            }
//        }
        templates.register("homepage");
        status = ModuleStatus.ACTIVE;
    }

    void start() {
        status = ModuleStatus.STARTED;
    }

    void stop() {
        status = ModuleStatus.STOPPED;
    }

    void destroy() {
        status = ModuleStatus.DESTROYED;
    }

    ModuleStatus getStatus() {
        return status;
    }
}