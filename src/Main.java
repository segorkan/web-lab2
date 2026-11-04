
import com.fastcgi.FCGIInterface;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    private static final Pattern INT = Pattern.compile("-?\\d{1,9}");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d{1,20}([.,]\\d{1,40})?");
    private static final Map<Integer, String> statusMessages = Map.of(200, "OK", 400, "Bad Request", 500, "Internal Server Error", 405, "Method Not Allowed");
    private static final String OK_MESSAGE = "Верное значение.";

    private static final String JSON_TEMPLATE = """
            {
                "x": %d,
                "y": %f,
                "result": [
                            %s
                          ],
                "time": "%s",
                "execTime": %.4f
            }
            """;
    private static final String JSON_ERROR_TEMPLATE = """
            {
                "time": "%s",
                "reason": "%s"
            }
            """;
    private static final String JSON_VALIDATION_ERROR = """
            {
                "time": "%s",
                "execTime": %.4f,
                "x_result": "%s",
                "y_result": "%s",
                "r_result": "%s"
            }
            """;
    private static final String ITEM_TEMPLATE = """
                {"r": %d, "result": %b}
            """;

    public static void main(String[] args) {
        FCGIInterface fcgi = new FCGIInterface();
        while (fcgi.FCGIaccept() >= 0){
            Instant start = Instant.now();
            try {
                Properties p = FCGIInterface.request.params;
                String method = p.getProperty("REQUEST_METHOD");
                if (method.equals("POST")){
                    int len = Integer.parseInt(p.getProperty("CONTENT_LENGTH", "0"));
                    if (len < 0){
                        throw new IOException("Недопустимое значение Content-Length.");
                    }
                    String message = new String(System.in.readNBytes(len), StandardCharsets.UTF_8);
                    Map<String, List<String>> params = parseBody(message);
                    Point goodParams = validateParams(params);
                    String items = goodParams.getRs().stream()
                            .map(r -> String.format(Locale.ROOT, ITEM_TEMPLATE, r, checkIfInsideFigure(goodParams.getX(), goodParams.getY(), r)))
                            .collect(Collectors.joining("," + "\n"));
                    Instant end = Instant.now();
                    double duration = Duration.between(start, end).toNanos() / 1000000.0;
                    String json = String.format(Locale.ROOT, JSON_TEMPLATE, goodParams.getX(), goodParams.getY(), items, Instant.now(), duration);
                    formResponse(json, 200, null);
                }
                else {
                    throw new NotSupportedOperationException(method);
                }
            } catch (IOException e){
                e.printStackTrace();
                String json = String.format(Locale.ROOT, JSON_ERROR_TEMPLATE, Instant.now(), e.getMessage());
                formResponse(json, 500, null);
            } catch (ValidationException e){
                Instant end = Instant.now();
                double duration = Duration.between(start, end).toNanos() / 1000000.0;
                List<String> errors = e.getErrors();
                String json = String.format(Locale.ROOT, JSON_VALIDATION_ERROR, Instant.now(), duration, errors.get(0), errors.get(1), errors.get(2));
                formResponse(json, 400, null);
            } catch (NumberFormatException e){
                String json = String.format(Locale.ROOT, JSON_ERROR_TEMPLATE, Instant.now(), "Неверный формат Content-Length.");
                formResponse(json, 400, null);
            } catch (NotSupportedOperationException e){
                String json = String.format(Locale.ROOT, JSON_ERROR_TEMPLATE, Instant.now(), "Не поддерживается метод " + e.getMessage());
                formResponse(json, 405, "Allow: POST");
            } catch (Exception e){
                e.printStackTrace();
                String json = String.format(Locale.ROOT, JSON_ERROR_TEMPLATE, Instant.now(), "Внутренняя ошибка сервера.");
                formResponse(json, 500, null);
            }
        }
    }

    private static void formResponse(String json, int code, String header){
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String head = "Status: " + code + " " + statusMessages.get(code) + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + (header != null ? header + "\r\n" : "")
                + "\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        System.out.write(headBytes, 0, headBytes.length);
        System.out.write(body, 0, body.length);
        System.out.flush();
    }

    private static Map<String, List<String>> parseBody(String message) {
        Map<String, List<String>> map = new HashMap<>();
        if (message == null || message.isEmpty()) return map;
        for (String pair : message.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    private static String first(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private static Point validateParams(Map<String, List<String>> params) throws ValidationException{
        String xMsg = OK_MESSAGE;
        String yMsg = OK_MESSAGE;
        String rMsg = OK_MESSAGE;
        List<Integer> validatedRs = new ArrayList<>();
        Integer x = null;
        Double y = null;

        try {
            x = validateX(first(params, "x"));
        } catch (ValidationException e){
            xMsg = e.getMessage();
        }
        try {
            y = validateY(first(params, "y"));
        } catch (ValidationException e){
            yMsg = e.getMessage();
        }

        List<String> rs = params.get("r");
        if (rs == null || rs.isEmpty()){
            rMsg = "Выберите хотя бы одно значение R.";
        }
        else if (rs.size() > 5){
            rMsg = "Слишком много значений R.";
        }
        else {
            Set<String> rErrors = new LinkedHashSet<>();
            for (String r : rs) {
                try {
                    validatedRs.add(validateR(r));
                } catch (ValidationException e){
                    rErrors.add(e.getMessage());
                }
            }
            if (!rErrors.isEmpty()){
                rMsg = String.join(" ", rErrors);
            }
        }
        if (!xMsg.equals(OK_MESSAGE) || !yMsg.equals(OK_MESSAGE) || !rMsg.equals(OK_MESSAGE)){
            throw new ValidationException(List.of(xMsg, yMsg, rMsg));
        }
        return new Point(x, y, validatedRs);
    }

    private static int validateX(String s) {
        if (s == null || s.trim().isEmpty()){
            throw new ValidationException("Не введён X.");
        }
        if (!INT.matcher(s.trim()).matches()) {
            throw new ValidationException("X должен быть целым числом");
        }
        int x = Integer.parseInt(s.trim());
        if (x < -3 || x > 5) {
            throw new ValidationException("X должен быть от -3 до 5");
        }
        return x;
    }

    private static double validateY(String s) {
        if (s == null || s.trim().isEmpty()){
            throw new ValidationException("Не введён Y.");
        }
        if (!DECIMAL.matcher(s.trim()).matches()) {
            throw new ValidationException("Y должен быть числом");
        }
        double y = Double.parseDouble(s.trim().replace(',', '.'));
        if (y <= -5 || y >= 5) {                       // границы не входят, см. примечание
            throw new ValidationException("Y должен быть в интервале (-5, 5)");
        }
        return y;
    }

    private static int validateR(String s) {
        if (s == null || !INT.matcher(s.trim()).matches()) {
            throw new ValidationException("R должен быть целым числом");
        }
        int r = Integer.parseInt(s.trim());
        if (r < 1 || r > 5) {
            throw new ValidationException("R должен быть от 1 до 5");
        }
        return r;
    }

    private static boolean checkIfInsideFigure(int x, double y, int r) {
        if (x >= 0 && y >= 0) return x * x + y * y <= (double) r * r;
        if (x <= 0 && y <= 0) return x >= -r && y >= -r / 2.0;
        if (x >= 0 && y <= 0) return y >= x / 2.0 - r / 2.0;
        return false;
    }
}