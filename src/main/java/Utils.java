import io.quarkiverse.mcp.server.ToolCallException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public interface Utils {
    static boolean isValidDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            LocalDate.parse(date, formatter);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    static void validateStartEndEndDate(String startDate, String endDate) throws ToolCallException {
        if (!Utils.isValidDate(startDate)) {
            throw new ToolCallException("Invalid start date: " + startDate);
        }

        if (!Utils.isValidDate(endDate)) {
            throw new ToolCallException("Invalid start date: " + startDate);
        }
    }
}
