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

public record UsageReport(List<Usage> usages, Map<String, Double> totalCostPerCurrency) {

    public String build() throws CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        Writer stringWriter = new StringWriter();
        StatefulBeanToCsv<Usage> statefulBeanToCsv = new StatefulBeanToCsvBuilder<Usage>(stringWriter)
                .withQuotechar('\'')
                .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                .build();

        statefulBeanToCsv.write(usages);

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
