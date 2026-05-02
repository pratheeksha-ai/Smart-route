import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.*;

public class SmartRouteServer {

    static final String DB_URL = "jdbc:postgresql://localhost:5432/smart_route_db";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "1234";

    static class Edge {
        int toId;
        String toName;
        int distance;

        Edge(int toId, String toName, int distance) {
            this.toId = toId;
            this.toName = toName;
            this.distance = distance;
        }

        public String toString() {
            return toName + "(" + distance + "m)";
        }
    }

    static class Node implements Comparable<Node> {
        int id;
        int distance;

        Node(int id, int distance) {
            this.id = id;
            this.distance = distance;
        }

        public int compareTo(Node other) {
            return this.distance - other.distance;
        }
    }

    public static void main(String[] args) {
        testDatabaseConnection();
        startServer();
    }

    public static void testDatabaseConnection() {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Connected to the database successfully!");
            conn.close();

        } catch (Exception e) {
            System.out.println("Error connecting to the database: " + e.getMessage());
        }
    }

    public static void startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

            server.createContext("/", SmartRouteServer::serveFile);
            server.createContext("/api/add-place", SmartRouteServer::addPlaceApi);
            server.createContext("/api/places", SmartRouteServer::getPlacesApi);
            server.createContext("/api/add-route", SmartRouteServer::addRouteApi);
            server.createContext("/api/routes", SmartRouteServer::getRoutesApi);
            server.createContext("/api/find-route", SmartRouteServer::findRouteApi);

            server.setExecutor(null);
            server.start();

            System.out.println("Server started successfully.");
            System.out.println("Open: http://localhost:8080");

        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    public static void serveFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            path = "/index.html";
        }

        File file = new File("public" + path);

        if (!file.exists()) {
            String response = "404 File Not Found";

            exchange.sendResponseHeaders(404, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();

            return;
        }

        byte[] data = Files.readAllBytes(file.toPath());

        String contentType = "text/html; charset=UTF-8";
        if (path.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            contentType = "application/javascript; charset=UTF-8";
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    public static void addPlace(String placeName) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String sql = "INSERT INTO places (name) VALUES (?) ON CONFLICT (name) DO NOTHING";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, placeName);
            ps.executeUpdate();

            System.out.println("Place checked/added successfully: " + placeName);

            conn.close();

        } catch (Exception e) {
            System.out.println("Error adding place: " + e.getMessage());
        }
    }

    public static void addPlaceApi(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        String placeName = "";

        if (query != null && query.startsWith("name=")) {
            placeName = query.substring(5);
            placeName = URLDecoder.decode(placeName, "UTF-8");
        }

        if (placeName.trim().isEmpty()) {
            String response = "Place name cannot be empty.";

            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();

            return;
        }

        addPlace(placeName.trim());

        String response = "Place added successfully: " + placeName;

        exchange.sendResponseHeaders(200, response.getBytes().length);
        exchange.getResponseBody().write(response.getBytes());
        exchange.close();
    }

    public static void getPlacesApi(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[");

        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String sql = "SELECT id, name FROM places ORDER BY id";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            int count = 0;

            while (rs.next()) {
                if (count > 0) {
                    json.append(",");
                }

                int id = rs.getInt("id");
                String name = rs.getString("name");

                json.append("{");
                json.append("\"id\":").append(id).append(",");
                json.append("\"name\":\"").append(escapeJson(name)).append("\"");
                json.append("}");

                count++;
            }

            json.append("]");
            conn.close();

        } catch (Exception e) {
            json = new StringBuilder();
            json.append("{\"error\":\"").append(escapeJson(e.getMessage())).append("\"}");
        }

        String response = json.toString();

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        exchange.getResponseBody().write(response.getBytes());
        exchange.close();
    }

    public static void addRoute(int fromId, int toId, int distance, int crowd, int safety) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String sql = "INSERT INTO routes (from_place_id, to_place_id, distance, crowd, safety) VALUES (?, ?, ?, ?, ?)";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, fromId);
            ps.setInt(2, toId);
            ps.setInt(3, distance);
            ps.setInt(4, crowd);
            ps.setInt(5, safety);

            ps.executeUpdate();

            System.out.println("Route added successfully: from " + fromId + " to " + toId);

            conn.close();

        } catch (Exception e) {
            System.out.println("Error adding route: " + e.getMessage());
        }
    }

    public static void addRouteApi(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        int fromId = 0;
        int toId = 0;
        int distance = 0;
        int crowd = 0;
        int safety = 0;

        try {
            String[] parts = query.split("&");

            for (String part : parts) {
                String[] keyValue = part.split("=");

                if (keyValue[0].equals("fromId")) {
                    fromId = Integer.parseInt(keyValue[1]);
                } else if (keyValue[0].equals("toId")) {
                    toId = Integer.parseInt(keyValue[1]);
                } else if (keyValue[0].equals("distance")) {
                    distance = Integer.parseInt(keyValue[1]);
                } else if (keyValue[0].equals("crowd")) {
                    crowd = Integer.parseInt(keyValue[1]);
                } else if (keyValue[0].equals("safety")) {
                    safety = Integer.parseInt(keyValue[1]);
                }
            }

            if (fromId == toId) {
                String response = "From place and To place cannot be same.";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
                return;
            }

            if (distance <= 0 || crowd < 1 || crowd > 10 || safety < 1 || safety > 10) {
                String response = "Enter valid distance, crowd, and safety values.";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
                return;
            }

            addRoute(fromId, toId, distance, crowd, safety);

            String response = "Route added successfully.";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();

        } catch (Exception e) {
            String response = "Error adding route: " + e.getMessage();
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        }
    }

    public static void getRoutesApi(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[");

        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String sql = "SELECT r.id, " +
                    "p1.name AS from_name, " +
                    "p2.name AS to_name, " +
                    "r.distance, " +
                    "r.crowd, " +
                    "r.safety " +
                    "FROM routes r " +
                    "JOIN places p1 ON r.from_place_id = p1.id " +
                    "JOIN places p2 ON r.to_place_id = p2.id " +
                    "ORDER BY r.id";

            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            int count = 0;

            while (rs.next()) {
                if (count > 0) {
                    json.append(",");
                }

                json.append("{");
                json.append("\"id\":").append(rs.getInt("id")).append(",");
                json.append("\"from\":\"").append(escapeJson(rs.getString("from_name"))).append("\",");
                json.append("\"to\":\"").append(escapeJson(rs.getString("to_name"))).append("\",");
                json.append("\"distance\":").append(rs.getInt("distance")).append(",");
                json.append("\"crowd\":").append(rs.getInt("crowd")).append(",");
                json.append("\"safety\":").append(rs.getInt("safety"));
                json.append("}");

                count++;
            }

            json.append("]");
            conn.close();

        } catch (Exception e) {
            json = new StringBuilder();
            json.append("{\"error\":\"").append(escapeJson(e.getMessage())).append("\"}");
        }

        String response = json.toString();

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        exchange.getResponseBody().write(response.getBytes());
        exchange.close();
    }

    public static void findRouteApi(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();

        int startId = 0;
        int endId = 0;

        try {
            String[] parts = query.split("&");

            for (String part : parts) {
                String[] keyValue = part.split("=");

                if (keyValue[0].equals("startId")) {
                    startId = Integer.parseInt(keyValue[1]);
                } else if (keyValue[0].equals("endId")) {
                    endId = Integer.parseInt(keyValue[1]);
                }
            }

            if (startId == endId) {
                String response = "{\"error\":\"Start and destination cannot be same.\"}";

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
                return;
            }

            String response = runDijkstra(startId, endId);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();

        } catch (Exception e) {
            String response = "{\"error\":\"Error finding route: " + escapeJson(e.getMessage()) + "\"}";

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        }
    }

    public static String runDijkstra(int startId, int endId) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            Map<Integer, String> placeNames = new HashMap<>();
            Map<Integer, List<Edge>> graph = new HashMap<>();

            String placeSql = "SELECT id, name FROM places";
            PreparedStatement placePs = conn.prepareStatement(placeSql);
            ResultSet placeRs = placePs.executeQuery();

            while (placeRs.next()) {
                int id = placeRs.getInt("id");
                String name = placeRs.getString("name");

                placeNames.put(id, name);
                graph.put(id, new ArrayList<Edge>());
            }

            String routeSql = "SELECT r.from_place_id, r.to_place_id, " +
                    "p1.name AS from_name, " +
                    "p2.name AS to_name, " +
                    "r.distance " +
                    "FROM routes r " +
                    "JOIN places p1 ON r.from_place_id = p1.id " +
                    "JOIN places p2 ON r.to_place_id = p2.id";

            PreparedStatement routePs = conn.prepareStatement(routeSql);
            ResultSet routeRs = routePs.executeQuery();

            while (routeRs.next()) {
                int from = routeRs.getInt("from_place_id");
                int to = routeRs.getInt("to_place_id");

                String fromName = routeRs.getString("from_name");
                String toName = routeRs.getString("to_name");

                int distance = routeRs.getInt("distance");

                graph.get(from).add(new Edge(to, toName, distance));
                graph.get(to).add(new Edge(from, fromName, distance));
            }

            Map<Integer, Integer> distances = new HashMap<>();
            Map<Integer, Integer> previous = new HashMap<>();

            for (int id : graph.keySet()) {
                distances.put(id, Integer.MAX_VALUE);
                previous.put(id, null);
            }

            PriorityQueue<Node> pq = new PriorityQueue<>();

            distances.put(startId, 0);
            pq.add(new Node(startId, 0));

            while (!pq.isEmpty()) {
                Node current = pq.poll();

                if (current.distance > distances.get(current.id)) {
                    continue;
                }

                for (Edge edge : graph.get(current.id)) {
                    int newDistance = distances.get(current.id) + edge.distance;

                    if (newDistance < distances.get(edge.toId)) {
                        distances.put(edge.toId, newDistance);
                        previous.put(edge.toId, current.id);
                        pq.add(new Node(edge.toId, newDistance));
                    }
                }
            }

            if (distances.get(endId) == Integer.MAX_VALUE) {
                conn.close();
                return "{\"error\":\"No route found.\"}";
            }

            ArrayList<String> path = new ArrayList<>();

            Integer current = endId;

            while (current != null) {
                path.add(placeNames.get(current));
                current = previous.get(current);
            }

            Collections.reverse(path);

            String pathText = String.join(" -> ", path);
            int totalDistance = distances.get(endId);

            conn.close();

            return "{\"path\":\"" + escapeJson(pathText) + "\",\"distance\":" + totalDistance + "}";

        } catch (Exception e) {
            return "{\"error\":\"Error running Dijkstra: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    public static String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}