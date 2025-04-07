package data;

import com.opencsv.CSVWriter;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.quarkus.qute.Qute;

import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

public record UsageReport<T extends Usage>(List<T> usage,
                                           Map<String, Double> totalCostPerCurrency) {

    public String generate() throws CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        Writer stringWriter = new StringWriter();
        StatefulBeanToCsv<T> statefulBeanToCsv = new StatefulBeanToCsvBuilder<T>(stringWriter)
                .withQuotechar('\'')
                .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                .build();

        statefulBeanToCsv.write(usage);

        return Qute.fmt(
                        """
                                Total cost per currency: {totalCost}
                                
                                Cost usage in CSV format:
                                {csv}
                                """)
                .data("totalCost", this.totalCostPerCurrency)
                .data("csv", stringWriter.toString())
                .render();
    }
}
