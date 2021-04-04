module com.github.robtimus.os.windows {
    requires static java.desktop;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    exports com.github.robtimus.os.windows;
    exports com.github.robtimus.os.windows.registry;
    exports com.github.robtimus.os.windows.service;
    exports com.github.robtimus.os.windows.window;
}
