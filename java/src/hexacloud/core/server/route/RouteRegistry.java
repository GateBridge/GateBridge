package hexacloud.core.server.route;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import hexacloud.core.utils.common.DebugUtils;

public class RouteRegistry {

    private final String name;
    private final Map<String, BiConsumer<String, PrintWriter>> routes = new HashMap<>();

    public RouteRegistry() {
        this("Global");
    }

    public RouteRegistry(String name) {
        this.name = name;
    }

    private final java.util.Set<String> publicRoutes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> fastPathRoutes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.List<RouteRule> routeRules = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addRouteRule(RouteRule rule) {
        if (rule != null) {
            this.routeRules.add(rule);
        }
    }

    public java.util.List<RouteRule> getRouteRulesList() {
        return routeRules;
    }

    public java.util.List<RouteRule> getRouteRules() {
        return routeRules;
    }

    public boolean isRoutePublic(String routeName) {
        if (routeName == null) return false;
        return publicRoutes.contains(routeName.toUpperCase());
    }

    public boolean isRouteFastPath(String routeName) {
        if (routeName == null) return false;
        return fastPathRoutes.contains(routeName.toUpperCase());
    }

    public boolean isRouteAdmin(String routeName) {
        if (routeName == null) return false;
        String upper = routeName.toUpperCase();
        if (!upper.startsWith("/")) {
            upper = "/" + upper;
        }
        return upper.equals("/V1/GET_NODES") || upper.equals("/V1/GET_NODES_JSON")
            || upper.equals("/V1/GET_CLUSTERS_JSON") || upper.equals("/V1/REGISTER")
            || upper.equals("/V1/TELEMETRY") || upper.equals("/V1/DEREGISTER")
            || upper.equals("/V1/LIST_CLUSTERS") || upper.equals("/V1/CREATE_CLUSTER")
            || upper.equals("/V1/GET_CLUSTER_CONFIG") || upper.equals("/V1/GET_GLOBAL_CONFIG")
            || upper.equals("/V1/SET_ALLOWED_IPS") || upper.equals("/V1/SET_TIMEOUT");
    }

    public void registerController(RouteController controller) {
        if(controller == null) return;
        
        Class<?> clazz = controller.getClass();
        for(Method method : clazz.getDeclaredMethods()) {
            if(method.isAnnotationPresent(RouteMapping.class)) {
                RouteMapping mapping = method.getAnnotation(RouteMapping.class);
                String raw = mapping.value();
                String pathKey = raw.startsWith("/") ? raw : "/" + raw;
                String upperKey = pathKey.toUpperCase();
                String lowerKey = pathKey.toLowerCase();

                if (mapping.isPublic()) {
                    publicRoutes.add(raw);
                    publicRoutes.add(pathKey);
                    publicRoutes.add(upperKey);
                    publicRoutes.add(lowerKey);
                }
                if (mapping.fastPath()) {
                    fastPathRoutes.add(raw);
                    fastPathRoutes.add(pathKey);
                    fastPathRoutes.add(upperKey);
                    fastPathRoutes.add(lowerKey);
                }
                
                Class<?>[] paramTypes = method.getParameterTypes();
                if(paramTypes.length == 2 && paramTypes[0] == String.class && paramTypes[1] == PrintWriter.class) {
                    method.setAccessible(true);
                    BiConsumer<String, PrintWriter> handler;
                    try {
                        java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
                        java.lang.invoke.MethodHandle mh = lookup.unreflect(method);
                        final java.lang.invoke.MethodHandle boundMh = mh.bindTo(controller);
                        handler = (args, out) -> {
                            try {
                                boundMh.invoke(args, out);
                            } catch(Throwable e) {
                                DebugUtils.error("Failed to invoke route method: " + method.getName(), e);
                                out.println("ERROR: Internal server error");
                            }
                        };
                    } catch (Exception ex) {
                        handler = (args, out) -> {
                            try {
                                method.invoke(controller, args, out);
                            } catch(Exception e) {
                                DebugUtils.error("Failed to invoke route method: " + method.getName(), e);
                                out.println("ERROR: Internal server error");
                            }
                        };
                    }
                    routes.put(raw, handler);
                    routes.put(pathKey, handler);
                    routes.put(upperKey, handler);
                    routes.put(lowerKey, handler);
                    DebugUtils.info("RouteScanner: [" + name + "] Registered route '" + raw + "' mapping to method " + clazz.getSimpleName() + "." + method.getName());
                } else {
                    DebugUtils.error("RouteScanner: Failed to register method " + clazz.getSimpleName() + "." + method.getName() + " -> Must accept parameters (String, PrintWriter)");
                }
            }
        }
    }

    public Map<String, BiConsumer<String, PrintWriter>> getRoutes() {
        return routes;
    }
}
