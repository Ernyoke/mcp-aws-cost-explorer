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

public record DiscountReport(List<Discount> discounts, Map<String, Double> totalDiscountPerCurrency) {
    public String build() throws CsvRequiredFieldEmptyException, CsvDataTypeMismatchException {
        Writer stringWriter = new StringWriter();
        StatefulBeanToCsv<Discount> statefulBeanToCsv = new StatefulBeanToCsvBuilder<Discount>(stringWriter)
                .withQuotechar('\'')
                .withSeparator(CSVWriter.DEFAULT_SEPARATOR)
                .build();

        statefulBeanToCsv.write(discounts);

        return Qute.fmt(
                        """
                                Total discount per currency: {totalCost}
                                
                                Discount list in CSV format:
                                {csv}
                                """)
                .data("totalCost", this.totalDiscountPerCurrency)
                .data("csv", stringWriter.toString())
                .render();
    }
}
