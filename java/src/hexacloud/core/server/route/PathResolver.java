package hexacloud.core.server.route;

import java.util.List;

public class PathResolver {

    public static RouteResolution resolve(String path, String host, RouteRegistry registry) {
        if (path == null || path.isEmpty()) {
            return new RouteResolution(null, null, false, null, null);
        }

        // 1. Direct O(1) Local Route Lookup (Hot path)
        if (registry != null) {
            String upper = path.toUpperCase();
            if (registry.getRoutes().containsKey(upper)) {
                return new RouteResolution(null, null, false, upper, null);
            }
            if (registry.getRoutes().containsKey(path)) {
                return new RouteResolution(null, null, false, upper, null);
            }
        }

        String matchingPath = path;
        if (path.contains("//")) {
            matchingPath = path.replaceAll("//+", "/");
            if (registry != null) {
                String upper = matchingPath.toUpperCase();
                if (registry.getRoutes().containsKey(upper)) {
                    return new RouteResolution(null, null, false, upper, null);
                }
                if (registry.getRoutes().containsKey(matchingPath)) {
                    return new RouteResolution(null, null, false, upper, null);
                }
            }
        }

        // 2. Resolve local route key using fallback tolerances
        String localRouteKey = findLocalRouteKey(matchingPath, registry);
        if (localRouteKey != null) {
            return new RouteResolution(null, null, false, localRouteKey, null);
        }

        // 3. Resolve proxy paths (/clusters/{name}/...)
        int clustersIdx = matchingPath.indexOf("/clusters/");
        if (clustersIdx != -1) {
            String prefix = matchingPath.substring(0, clustersIdx);
            String pathWithoutClusters = matchingPath.substring(clustersIdx + 10);
            int slashIdx = pathWithoutClusters.indexOf('/');
            String targetClusterName;
            String clusterSubpath;
            if (slashIdx != -1) {
                targetClusterName = pathWithoutClusters.substring(0, slashIdx);
                clusterSubpath = pathWithoutClusters.substring(slashIdx);
            } else {
                targetClusterName = pathWithoutClusters;
                clusterSubpath = "/";
            }
            return new RouteResolution(targetClusterName, clusterSubpath, false, null, prefix);
        }

        // 4. Match Ingress rules
        if (registry != null) {
            List<RouteRule> rules = registry.getRouteRulesList();
            if (rules != null && !rules.isEmpty()) {
                for (RouteRule rule : rules) {
                    if (rule.matches(host, matchingPath)) {
                        return new RouteResolution(rule.getClusterName(), rule.rewritePath(matchingPath), true, null, null);
                    }
                }
            }
        }

        return new RouteResolution(null, null, false, null, null);
    }

    private static String findLocalRouteKey(String matchingPath, RouteRegistry registry) {
        if (registry == null) return null;

        String upper = matchingPath.toUpperCase();
        if (registry.getRoutes().containsKey(upper)) {
            return upper;
        }

        if (upper.startsWith("/") && upper.length() > 1) {
            String stripped = upper.substring(1);
            if (registry.getRoutes().containsKey(stripped)) {
                return stripped;
            }
        }

        // Try unv1 / v1 variants
        if (upper.startsWith("/V1/") || upper.equals("/V1")) {
            String unv1 = upper.equals("/V1") ? "/" : upper.substring(3);
            if (registry.getRoutes().containsKey(unv1)) {
                return unv1;
            }
        } else {
            String withV1 = "/V1" + (upper.startsWith("/") ? upper : "/" + upper);
            if (registry.getRoutes().containsKey(withV1)) {
                return withV1;
            }
        }

        return null;
    }
}
